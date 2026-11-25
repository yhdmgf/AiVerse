package com.aiverse.feature.settings.data.remote

import android.content.Context
import com.aiverse.R
import com.aiverse.core.util.toUserMessage
import com.aiverse.feature.agent.domain.provider.joinUrl
import com.aiverse.feature.settings.domain.model.ModelMetadata
import com.aiverse.feature.settings.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** 网络类失败重试前的退避时长：跨过 libcore 解析失败缓存的有效期，否则重试必然复现同一失败。 */
private const val NETWORK_RETRY_BACKOFF_MS = 3_000L

/** 拉取模型列表的结果，包含解析出的模型列表、接口自带的模型元数据及完整的调试信息。 */
data class FetchModelsResult(
    val models: List<String>,
    val debugInfo: ModelTestResult,
    /** 接口响应里自带的每个模型元数据（上下文长度、工具/视觉能力等），键为模型 id；响应未提供的字段为 null。 */
    val modelMetadata: Map<String, ModelMetadata> = emptyMap()
)

/** 拉取模型异常，附带调试信息。 */
class FetchModelsException(
    override val message: String,
    val debugInfo: ModelTestResult?
) : RuntimeException(message)

/** 测试模型连通性的结果，包含完整的请求/响应调试信息。 */
data class ModelTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
    val requestUrl: String = "",
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String = "",
    val responseCode: Int = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String = "",
    val errorDetail: String = ""
)

/**
 * 直接通过 OkHttp 调用提供商的 REST 接口来拉取模型列表与测试连通性，
 * 复用全局 OkHttpClient，独立于聊天用的 Retrofit 适配器。
 */
@Singleton
class ModelApiService @Inject constructor(
    private val client: OkHttpClient,
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 拉取提供商可用模型列表（OpenAI 兼容 / Anthropic 均为 GET /v1/models，Gemini 为 GET /v1beta/models）。 */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        type: ProviderType,
        useFullUrl: Boolean = false,
        customHeaders: Map<String, String> = emptyMap()
    ): Result<FetchModelsResult> = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        runCatching {
            if (apiKey.isBlank()) error(context.getString(R.string.provider_api_key_required))

            val modelsPath = if (type == ProviderType.GEMINI) "v1beta/models" else "v1/models"
            val url = if (useFullUrl) baseUrl else joinUrl(baseUrl, modelsPath)
            capturedUrl = url
            val request = Request.Builder()
                .url(url)
                .applyAuth(apiKey, type)
                .applyCustomHeaders(customHeaders)
                .get()
                .build()

            val reqHeadersMap = request.headers.names().associateWith { name ->
                val raw = request.header(name).orEmpty()
                if (name.equals("Authorization", ignoreCase = true) || name.equals("x-api-key", ignoreCase = true)) {
                    if (raw.length > 12) raw.take(7) + "..." + raw.takeLast(4) else "***"
                } else {
                    raw
                }
            }
            capturedHeaders = reqHeadersMap

            client.newCall(request).execute().use { response ->
                val latency = (System.nanoTime() - start) / 1_000_000
                val body = response.body?.string().orEmpty()
                val respHeadersMap = response.headers.names().associateWith { response.header(it).orEmpty() }

                if (!response.isSuccessful) {
                    val debug = ModelTestResult(
                        success = false,
                        latencyMs = latency,
                        message = "HTTP ${response.code}: ${body.take(160)}",
                        requestUrl = url,
                        requestHeaders = reqHeadersMap,
                        requestBody = "",
                        responseCode = response.code,
                        responseHeaders = respHeadersMap,
                        responseBody = body,
                        errorDetail = body
                    )
                    throw FetchModelsException("HTTP ${response.code}: ${body.take(200)}", debug)
                }

                val jsonObj = json.parseToJsonElement(body).jsonObject
                val data = if (type == ProviderType.GEMINI) {
                    jsonObj["models"]?.jsonArray
                } else {
                    jsonObj["data"]?.jsonArray
                } ?: run {
                    val debug = ModelTestResult(
                        success = false,
                        latencyMs = latency,
                        message = context.getString(R.string.provider_fetch_missing_fields),
                        requestUrl = url,
                        requestHeaders = reqHeadersMap,
                        requestBody = "",
                        responseCode = response.code,
                        responseHeaders = respHeadersMap,
                        responseBody = body,
                        errorDetail = context.getString(R.string.provider_fetch_missing_fields_detail, body)
                    )
                    throw FetchModelsException(context.getString(R.string.provider_fetch_missing_fields), debug)
                }

                val modelList = data.mapNotNull { 
                        it.jsonObject[if (type == ProviderType.GEMINI) "name" else "id"]?.jsonPrimitive?.contentOrNull 
                    }
                    .map { if (type == ProviderType.GEMINI) it.removePrefix("models/") else it }
                    .filter { it.isNotBlank() }
                    .sorted()

                val debug = ModelTestResult(
                    success = true,
                    latencyMs = latency,
                    message = context.getString(R.string.provider_fetch_success, modelList.size, latency),
                    requestUrl = url,
                    requestHeaders = reqHeadersMap,
                    requestBody = "",
                    responseCode = response.code,
                    responseHeaders = respHeadersMap,
                    responseBody = body
                )
                FetchModelsResult(modelList, debug, parseFetchedMetadata(data, type))
            }
        }.recoverCatching { e ->
            if (e is FetchModelsException) {
                throw e
            }
            val latency = (System.nanoTime() - start) / 1_000_000
            val fallback = context.getString(R.string.settings_models_fetch_failed)
            val debug = ModelTestResult(
                success = false,
                latencyMs = latency,
                message = e.message ?: fallback,
                requestUrl = capturedUrl,
                requestHeaders = capturedHeaders,
                errorDetail = e.stackTraceToString()
            )
            throw FetchModelsException(e.message ?: fallback, debug)
        }
    }

    /** 发送一条极短请求验证 Key + 模型 + 端点是否可用，返回耗时。 */
    suspend fun testModel(
        baseUrl: String,
        apiKey: String,
        type: ProviderType,
        useFullUrl: Boolean,
        useResponseApi: Boolean,
        model: String,
        customHeaders: Map<String, String> = emptyMap()
    ): ModelTestResult {
        val first = testModelAttempt(baseUrl, apiKey, type, useFullUrl, useResponseApi, model, customHeaders)
        if (!first.failure.isRetryableNetworkFailure()) return first.result
        // libcore 会缓存解析失败（约 2 秒），立即重试只会命中原样失败，故先退避再试一次
        delay(NETWORK_RETRY_BACKOFF_MS)
        return testModelAttempt(baseUrl, apiKey, type, useFullUrl, useResponseApi, model, customHeaders).result
    }

    private suspend fun testModelAttempt(
        baseUrl: String,
        apiKey: String,
        type: ProviderType,
        useFullUrl: Boolean,
        useResponseApi: Boolean,
        model: String,
        customHeaders: Map<String, String>
    ): TestAttempt = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedBody = ""
        try {
            if (apiKey.isBlank()) error(context.getString(R.string.provider_api_key_required))

            val (url, payload) = when (type) {
                ProviderType.ANTHROPIC -> {
                    val u = if (useFullUrl) baseUrl else joinUrl(baseUrl, "v1/messages")
                    u to """{"model":${model.jsonStr()},"max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                }
                ProviderType.GEMINI -> {
                    // Interactions 的请求体是 `input`（step 序列或纯字符串），必须同时切到
                    // v1beta/interactions 端点；仍打到 generateContent 会被以缺 `contents` 拒绝。
                    // 图像模型（Nano Banana 系）只支持 Interactions，无论开关是否开启都强制切。
                    if (useResponseApi || model.endsWith("-image", ignoreCase = true)) {
                        val u = if (useFullUrl) baseUrl else joinUrl(baseUrl, "v1beta/interactions")
                        u to """{"model":${model.jsonStr()},"input":"hi","store":false}"""
                    } else {
                        val u = if (useFullUrl) {
                            baseUrl
                        } else {
                            val path = if (baseUrl.trimEnd('/').endsWith(model)) {
                                baseUrl.trimEnd('/') + ":generateContent"
                            } else {
                                joinUrl(baseUrl, "v1beta/models/$model:generateContent")
                            }
                            path
                        }
                        u to """{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}"""
                    }
                }
                else -> {
                    // Responses API 的请求体是 `input` 格式，必须同时切到 v1/responses 端点；
                    // 仍打到 chat/completions 会被服务端以「missing field `messages`」拒绝。
                    val path = if (useResponseApi) "v1/responses" else "v1/chat/completions"
                    val u = if (useFullUrl) baseUrl else joinUrl(baseUrl, path)
                    if (useResponseApi) {
                        u to """{"model":${model.jsonStr()},"input":[{"role":"user","content":"hi"}]}"""
                    } else {
                        u to """{"model":${model.jsonStr()},"max_completion_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .applyAuth(apiKey, type)
                .applyCustomHeaders(customHeaders)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            capturedUrl = url
            capturedBody = payload

            val reqHeadersMap = request.headers.names().associateWith { name ->
                val raw = request.header(name).orEmpty()
                if (name.equals("Authorization", ignoreCase = true) || name.equals("x-api-key", ignoreCase = true)) {
                    if (raw.length > 12) raw.take(7) + "..." + raw.takeLast(4) else "***"
                } else {
                    raw
                }
            }
            capturedHeaders = reqHeadersMap

            client.newCall(request).execute().use { response ->
                val latency = (System.nanoTime() - start) / 1_000_000
                val body = response.body?.string().orEmpty()
                val respHeadersMap = response.headers.names().associateWith { response.header(it).orEmpty() }

                if (response.isSuccessful) {
                    TestAttempt(ModelTestResult(
                        success = true,
                        latencyMs = latency,
                        message = context.getString(R.string.provider_test_success, latency),
                        requestUrl = url,
                        requestHeaders = reqHeadersMap,
                        requestBody = payload,
                        responseCode = response.code,
                        responseHeaders = respHeadersMap,
                        responseBody = body
                    ))
                } else {
                    TestAttempt(ModelTestResult(
                        success = false,
                        latencyMs = latency,
                        message = "HTTP ${response.code}: ${body.take(160)}",
                        requestUrl = url,
                        requestHeaders = reqHeadersMap,
                        requestBody = payload,
                        responseCode = response.code,
                        responseHeaders = respHeadersMap,
                        responseBody = body,
                        errorDetail = body
                    ))
                }
            }
        } catch (e: Exception) {
            val latency = (System.nanoTime() - start) / 1_000_000
            TestAttempt(
                result = ModelTestResult(
                    success = false,
                    latencyMs = latency,
                    message = e.toUserMessage(context),
                    requestUrl = capturedUrl,
                    requestHeaders = capturedHeaders,
                    requestBody = capturedBody,
                    errorDetail = e.stackTraceToString()
                ),
                failure = e
            )
        }
    }

    private fun Throwable?.isRetryableNetworkFailure(): Boolean =
        this is java.net.UnknownHostException ||
            this is java.net.ConnectException ||
            this is java.net.SocketTimeoutException

    private data class TestAttempt(val result: ModelTestResult, val failure: Throwable? = null)

    private fun Request.Builder.applyAuth(apiKey: String, type: ProviderType): Request.Builder =
        when (type) {
            ProviderType.ANTHROPIC -> this
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
            ProviderType.GEMINI -> this
                .header("x-goog-api-key", apiKey)
            else -> this.header("Authorization", "Bearer $apiKey")
        }

    private fun Request.Builder.applyCustomHeaders(customHeaders: Map<String, String>): Request.Builder =
        this.apply { customHeaders.forEach { (name, value) -> if (name.isNotBlank()) header(name, value) } }

    /** 转成安全的 JSON 字符串字面量（含引号、正确转义）。 */
    private fun String.jsonStr(): String = JsonPrimitive(this).toString()

    companion object {
        /**
         * 解析模型列表响应里每个模型自带的元数据（上下文长度、工具/视觉能力、输出上限等），
         * 供 [ModelMetadataService] 作为优先于内置目录、低于用户自定义的中间层来源。
         * 响应未提供的字段一律留空（上下文 0、能力 false），交由 [layerModelMetadata] 回退到内置目录/默认值，
         * 避免把「接口没说」当成「接口说不支持」。
         *
         * 兼容三种响应形态：OpenAI 兼容 / 聚合网关（`data[].id` + `context_length`/`capabilities`）、
         * Anthropic（`data[].id` + `max_tokens`）、Gemini（`models[].name` + `inputTokenLimit`/`supportedGenerationMethods`）。
         */
        internal fun parseFetchedMetadata(data: kotlinx.serialization.json.JsonArray, type: ProviderType): Map<String, ModelMetadata> {
        val idField = if (type == ProviderType.GEMINI) "name" else "id"
        val result = LinkedHashMap<String, ModelMetadata>()
        for (element in data) {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            val rawId = obj[idField]?.jsonPrimitive?.contentOrNull ?: continue
            val id = if (type == ProviderType.GEMINI) rawId.removePrefix("models/") else rawId
            if (id.isBlank()) continue

            // 上下文窗口候选：不含 max_tokens——Anthropic 的 max_tokens 是输出上限，不是上下文窗口。
            // 也不含 max_input_tokens/inputTokenLimit——它们是「单次输入窗口」，不是总上下文窗口；
            // 若接口只返回输入窗口而无总窗口，误当总窗口会把预算低估、用量百分比翻倍（issue 场景 8%→16%）。
            val contextTokens = longField(obj, "context_length", "context_window", "max_context_length", "max_model_len")
                ?.takeIf { it > 0 }?.toInt()
            // 单次最大输入窗口：独立于总窗口解析（不再强制等于 contextTokens）。
            val inputTokens = longField(obj, "max_input_tokens", "inputTokenLimit")
                ?.takeIf { it > 0 }?.toInt()
            // 输出上限候选：Anthropic 的 max_tokens 归到这里（如 8192），避免被误当上下文显示成 8K。
            val outputTokens = longField(obj, "max_output_tokens", "max_output", "max_tokens", "outputTokenLimit")
                ?.takeIf { it > 0 }?.toInt()
            val supportsTools = boolField(obj, "tool_call", "supports_tools", "function_calling", "tools")
                ?: capabilityFlag(obj, "tool_call", "tools", "function_calling")
                ?: (if (type == ProviderType.GEMINI) geminiSupportsTools(obj) else null)
            val supportsVision = boolField(obj, "supports_vision", "vision")
                ?: capabilityFlag(obj, "vision", "image_input", "image", "multimodal")

            result[id] = ModelMetadata(
                id = id,
                // display 字段按 provider 选：Gemini 用驼峰 displayName（其 name 是 models/ 前缀的 id），
                // 其余用 snake_case display_name；顶层 name 作为非 Gemini 的回退，仍不带 models/ 前缀。
                displayName = obj[if (type == ProviderType.GEMINI) "displayName" else "display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { type != ProviderType.GEMINI && it.isNotBlank() }
                    ?: id,
                contextTokens = contextTokens ?: 0,
                inputTokens = inputTokens ?: 0,
                outputTokens = outputTokens,
                supportsTools = supportsTools ?: false,
                supportsVision = supportsVision ?: false,
                source = ModelMetadata.Source.INFERRED
            )
        }
        return result
    }

        /** 取第一个存在的长整型字段（兼容 double 形式的整数，如 1000000.0）；均缺失返回 null。 */
        private fun longField(obj: kotlinx.serialization.json.JsonObject, vararg keys: String): Long? {
            for (key in keys) {
                val primitive = obj[key]?.jsonPrimitive ?: continue
                val value = primitive.longOrNull ?: primitive.doubleOrNull?.toLong() ?: continue
                if (value > 0) return value
            }
            return null
        }

        /** 取第一个存在的布尔字段；均缺失（含 null 字面量）返回 null。 */
        private fun boolField(obj: kotlinx.serialization.json.JsonObject, vararg keys: String): Boolean? {
            for (key in keys) {
                val primitive = obj[key]?.jsonPrimitive ?: continue
                val value = primitive.booleanOrNull ?: continue
                return value
            }
            return null
        }

        /** 从嵌套 `capabilities` 对象里取能力标志；无该对象或字段缺失返回 null。 */
        private fun capabilityFlag(obj: kotlinx.serialization.json.JsonObject, vararg keys: String): Boolean? {
            val capabilities = obj["capabilities"]?.jsonObject ?: return null
            return boolField(capabilities, *keys)
        }

        /** Gemini 用 `supportedGenerationMethods` 数组声明能力，含 `tools` 即支持工具调用。 */
        private fun geminiSupportsTools(obj: kotlinx.serialization.json.JsonObject): Boolean? {
            val methods = obj["supportedGenerationMethods"]?.jsonArray ?: return null
            return methods.any { it.jsonPrimitive.contentOrNull.equals("tools", ignoreCase = true) }
        }
    }
}
