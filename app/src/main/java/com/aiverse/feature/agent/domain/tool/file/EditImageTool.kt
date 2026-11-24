package com.aiverse.feature.agent.domain.tool.file

import android.util.Base64
import com.aiverse.core.util.AILogger
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.model.AgentContext
import com.aiverse.feature.agent.domain.provider.enrichWithHttpErrorBody
import com.aiverse.feature.agent.domain.provider.isKeySwitchFailure
import com.aiverse.feature.agent.domain.provider.joinUrl
import com.aiverse.feature.agent.domain.provider.resolveCustomHeaders
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.feature.settings.domain.model.AIProviderConfig
import com.aiverse.feature.settings.domain.model.ProviderType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject as KtxJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/**
 * 改图工具（图生图）：以上传的工作区参考图为基础，按提示词让生图模型编辑 / 重绘图片。
 *
 * - OpenAI 兼容供应商走 `POST /v1/images/edits`（multipart/form-data，单张 `image`、多张 `image[]`），
 *   通过注入的 [httpClient] 直接发 multipart（Retrofit 现有 JSON 端点不适用于 multipart）。
 * - Gemini 供应商走 generateContent（`v1beta/models/{model}:generateContent`），参考图以
 *   `contents[].parts[]` 里的 `inlineData` 传入，文本提示词作为另一个 part。
 *
 * 使用「设置 → 默认模型 → 生图模型」配置的 provider + 模型；模型不支持改图（如 dall-e-3）或未配置时
 * 返回带设置指引的错误。编辑结果始终以 [AgentImage] 返回，落盘逻辑与生图工具 [GenerateImageTool] 一致
 * （见 [AbstractImageTool]）。参考图会上传给生图服务。
 */
class EditImageTool @Inject constructor() : AbstractImageTool() {

    override val name = "editImage"
    override val description = "根据工作区参考图和文本指令编辑图片（图生图 / 改图），并在聊天区展示结果。第一张为底图、其余为参考；参考图会上传给生图服务。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_WRITE, ToolCapability.WRITE_WORKSPACE)
    override val logTag = "EditImageTool"
    override val parameters = mapOf(
        "paths" to ToolParameter(
            name = "paths",
            type = ParameterType.STRING,
            description = "工作区参考图路径，1 到 4 张（第一张为底图，其余为参考）。多张用逗号分隔，如 ~/workspace/a.png, ~/workspace/b.png。参考图会上传给生图服务。",
            required = true
        ),
        "prompt" to ToolParameter(
            name = "prompt",
            type = ParameterType.STRING,
            description = "改图指令，描述要如何编辑参考图（改什么、保留什么、风格等）。",
            required = true
        ),
        "n" to ToolParameter(
            name = "n",
            type = ParameterType.INTEGER,
            description = "生成张数，默认 1，最多 4。",
            required = false
        ),
        "size" to ToolParameter(
            name = "size",
            type = ParameterType.STRING,
            description = "输出图片尺寸，如 1024x1024（默认）、1024x1536、1536x1024。Gemini 通道忽略该参数。",
            required = false
        ),
        "quality" to ToolParameter(
            name = "quality",
            type = ParameterType.STRING,
            description = "画质：GPT Image 系列支持 low / medium / high / auto（默认 auto）；dall-e-3 支持 standard / hd（默认 standard）。Gemini 通道忽略该参数。",
            required = false,
            enum = listOf("low", "medium", "high", "auto", "standard", "hd")
        ),
        "output_path" to ToolParameter(
            name = "output_path",
            type = ParameterType.STRING,
            description = "保存路径（可选）。不传默认保存到 ~/.aiverse/generated-images/；传了则保存到指定路径，如 ~/workspace/assets/image.png。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val paths = parsePaths(args["paths"])
        val prompt = args["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val outputPath = args["output_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val count = args["n"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认编辑图片",
            summary = "AI 请求调用改图模型编辑图片（参考图会上传给生图服务）",
            details = buildString {
                append("参考图：\n")
                paths.forEach { p -> append("- ").append(p).append('\n') }
                append("指令：").append(prompt).append('\n')
                append("数量：").append(count).append('\n')
                append("保存到：").append(if (outputPath.isBlank()) "$DEFAULT_OUTPUT_DIR/" else outputPath)
            },
            argsPreview = argsPreview
        )
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult = withContext(Dispatchers.IO) {
        val paths = parsePaths(args["paths"])
        if (paths.isEmpty()) {
            return@withContext ToolResult.Error("缺少必需参数：paths（至少 1 张工作区参考图）", "MISSING_PATHS")
        }
        if (paths.size > MAX_IMAGES) {
            return@withContext ToolResult.Error("参考图最多 $MAX_IMAGES 张，当前为 ${paths.size} 张", "INVALID_PARAMS")
        }
        val prompt = args["prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return@withContext ToolResult.Error("缺少必需参数：prompt", "MISSING_PROMPT")
        }
        val size = args["size"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null } ?: DEFAULT_SIZE
        val n = args["n"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        if (n !in 1..MAX_IMAGES) {
            return@withContext ToolResult.Error("n 取值必须在 1 到 $MAX_IMAGES 之间，当前为 $n", "INVALID_PARAMS")
        }
        // 与 generateImage 一致：quality 仅 OpenAI 官方生图模型支持，Gemini 通道忽略。
        val quality = args["quality"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.ifBlank { null }
        val outputPath = args["output_path"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }

        val references = try {
            paths.map { path ->
                val bytes = readReferenceImageBytes(path)
                val mime = mimeForFormat(detectImageFormat(bytes))
                ReferenceImage(path, mime, bytes)
            }
        } catch (e: Exception) {
            return@withContext ToolResult.Error("读取参考图失败：${e.message}", "READ_REFERENCE_FAILED")
        }

        var seq = 0
        var lastProviderId = ""
        var activeApiKey = ""
        return@withContext try {
            val provider = resolveImageGenProvider()
            lastProviderId = provider.id
            activeApiKey = keyRotator.activeKey(provider, context.sessionId) ?: provider.firstUsableApiKey
            val model = provider.effectiveModel
            if (!supportsEdit(model)) {
                return@withContext ToolResult.Error(
                    "当前生图模型 $model 不支持改图（图生图）。请到「设置 → 默认模型 → 生图模型」选择支持图像编辑的模型（如 gpt-image-1，或 Gemini 图像模型）。",
                    "MODEL_NOT_SUPPORT_EDIT"
                )
            }
            if (provider.type == ProviderType.GEMINI) {
                return@withContext editViaGemini(provider, activeApiKey, model, prompt, references, n, outputPath, context.sessionId)
            }
            // OpenAI 兼容通道按模型校验 quality（复用 generateImage 的合法取值集合与报错文案）。
            val isGptImage = model.startsWith("gpt-image", ignoreCase = true)
            val isDalle2 = model.equals("dall-e-2", ignoreCase = true)
            val isDalle3 = model.equals("dall-e-3", ignoreCase = true)
            GenerateImageTool.validateImageParams(
                model, isGptImage, isDalle2, isDalle3, n, quality,
                background = null, moderation = null, style = null, outputFormat = null
            )?.let {
                return@withContext ToolResult.Error(it, "INVALID_PARAMS")
            }
            FileLogger.i(logTag, "editImage provider=${provider.id} model=$model refs=${references.size} prompt=$prompt n=$n size=$size quality=$quality")
            editViaOpenAI(provider, activeApiKey, model, prompt, references, n, size, quality, isGptImage, outputPath, context.sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val enriched = e.enrichWithHttpErrorBody()
            if (activeApiKey.isNotEmpty() && enriched.isKeySwitchFailure()) {
                keyRotator.reportFailure(lastProviderId, context.sessionId, activeApiKey)
            }
            FileLogger.e(logTag, "editImage 失败", enriched)
            AILogger.logError(context.sessionId, lastProviderId, enriched, seq)
            ToolResult.Error(enriched.message ?: "改图调用失败", "IMAGE_EDIT_FAILED")
        }
    }

    /** OpenAI 兼容改图：直接用 httpClient 发 multipart/form-data 到 /v1/images/edits。 */
    private suspend fun editViaOpenAI(
        provider: AIProviderConfig,
        apiKey: String,
        model: String,
        prompt: String,
        references: List<ReferenceImage>,
        n: Int,
        size: String,
        quality: String?,
        isGptImage: Boolean,
        outputPath: String?,
        sessionId: String?
    ): ToolResult {
        val url = if (provider.useFullUrl) provider.baseUrl else joinUrl(provider.baseUrl, "v1/images/edits")
        val formPartName = if (references.size > 1) "image[]" else "image"
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", n.toString())
            .addFormDataPart("size", size)
        if (quality != null) multipartBuilder.addFormDataPart("quality", quality)
        if (!isGptImage) multipartBuilder.addFormDataPart("response_format", "b64_json")
        references.forEach { ref ->
            multipartBuilder.addFormDataPart(
                formPartName,
                ref.path.substringAfterLast('/'),
                ref.bytes.toRequestBody(ref.mime.toMediaType())
            )
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .apply { resolveCustomHeaders(provider.customHeaders, sessionId, apiKey).forEach { (k, v) -> addHeader(k, v) } }
            .post(multipartBuilder.build())
            .build()

        val seq = AILogger.logRequest(
            sessionId, provider.id, model, "POST (multipart)", url,
            mapOf("prompt" to prompt, "n" to n, "size" to size, "quality" to quality, "images" to references.map { it.path })
        )
        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val error = IOException("改图请求失败 HTTP ${response.code}: $bodyString")
                .enrichWithHttpErrorBody()
            AILogger.logError(sessionId, provider.id, error, seq)
            throw error
        }
        val json = JsonParser.parseString(bodyString).asJsonObject
        AILogger.logResponse(sessionId, provider.id, json, seq)
        return buildEditResultFromOpenAIJson(json, n, outputPath, model)
    }

    /** 把 OpenAI images 响应（JSON）转成统一结果。 */
    private fun buildEditResultFromOpenAIJson(
        json: JsonObject,
        requestedN: Int,
        outputPath: String?,
        model: String
    ): ToolResult {
        val dataArr = json.getAsJsonArray("data")
        if (dataArr == null || dataArr.size() == 0) {
            return ToolResult.Error("改图服务未返回图片数据", "EMPTY_RESULT")
        }
        val effectiveBasePath = outputPath ?: createDefaultBasePath()
        val overwrite = outputPath != null
        val agentImages = mutableListOf<com.aiverse.feature.agent.domain.model.AgentImage>()
        val savedDisplayPaths = mutableListOf<String>()
        val filesList = mutableListOf<KtxJsonObject>()
        var totalBytes = 0L
        var failedCount = 0
        dataArr.forEachIndexed { index, itemEl ->
            try {
                val item = itemEl.asJsonObject
                val base64 = item.get("b64_json")?.takeIf { it.isJsonPrimitive }?.asString
                val url = item.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                val bytes: ByteArray
                val base64Out: String
                when {
                    !base64.isNullOrBlank() -> {
                        base64Out = base64
                        bytes = decodeImageBase64(base64)
                    }
                    !url.isNullOrBlank() -> {
                        bytes = downloadImageBytes(url)
                        base64Out = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                    else -> throw IOException("结果中缺少 base64 与 URL")
                }
                totalBytes = checkedTotalBytes(totalBytes, bytes.size)
                persistImageBytes(
                    bytes, base64Out, effectiveBasePath, overwrite,
                    agentImages, savedDisplayPaths, filesList
                )
            } catch (e: Exception) {
                failedCount++
                FileLogger.w(logTag, "处理第 ${index + 1} 张改图结果失败", e)
            }
        }
        if (agentImages.isEmpty()) {
            return ToolResult.Error("未能获取改图结果（图片无效、过大或下载失败）", "EMPTY_RESULT")
        }
        return buildImageResult(
            agentImages, savedDisplayPaths, filesList, model, requestedN,
            outputPath, failedCount, generatedVerb = "已编辑"
        )
    }

    /** Gemini 改图：走 generateContent，参考图以 inlineData part 传入。 */
    private suspend fun editViaGemini(
        provider: AIProviderConfig,
        apiKey: String,
        model: String,
        prompt: String,
        references: List<ReferenceImage>,
        n: Int,
        outputPath: String?,
        sessionId: String?
    ): ToolResult {
        val url = if (provider.useFullUrl) provider.baseUrl
        else joinUrl(provider.baseUrl, "v1beta/models/$model:generateContent")
        val effectiveBasePath = outputPath ?: createDefaultBasePath()
        val overwrite = outputPath != null
        val agentImages = mutableListOf<com.aiverse.feature.agent.domain.model.AgentImage>()
        val savedDisplayPaths = mutableListOf<String>()
        val filesList = mutableListOf<KtxJsonObject>()
        var totalBytes = 0L
        var failedCount = 0
        var lastSeq = 0
        try {
            for (index in 0 until n) {
                val parts = mutableListOf<Any>(
                    mapOf("text" to prompt)
                )
                references.forEach { ref ->
                    parts.add(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to ref.mime,
                                "data" to Base64.encodeToString(ref.bytes, Base64.NO_WRAP)
                            )
                        )
                    )
                }
                val request = mapOf("contents" to listOf(mapOf("parts" to parts)))
                lastSeq = AILogger.logRequest(sessionId, provider.id, model, "POST", url, request)
                val response = geminiApi.generateContent(
                    url = url,
                    apiKey = apiKey,
                    extraHeaders = resolveCustomHeaders(provider.customHeaders, sessionId, apiKey),
                    request = request
                )
                AILogger.logResponse(sessionId, provider.id, response, lastSeq)
                val beforeCount = agentImages.size
                extractGenerateContentImageData(response).forEach { base64 ->
                    runCatching {
                        totalBytes += persistImage(
                            base64, effectiveBasePath, overwrite, agentImages,
                            savedDisplayPaths, filesList, totalBytes
                        )
                    }.onFailure { FileLogger.w(logTag, "处理 Gemini 改图结果失败", it) }
                }
                if (agentImages.size == beforeCount) failedCount++
                if (index == 0 && agentImages.isEmpty() && n > 1) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val enriched = e.enrichWithHttpErrorBody()
            if (enriched.isKeySwitchFailure()) keyRotator.reportFailure(provider.id, sessionId, apiKey)
            FileLogger.e(logTag, "editImage(Gemini) 失败", enriched)
            AILogger.logError(sessionId, provider.id, enriched, lastSeq)
            if (agentImages.isNotEmpty()) {
                return buildGeminiEditResult(
                    agentImages, savedDisplayPaths, filesList, model, n, outputPath,
                    failedCount.coerceAtLeast(n - agentImages.size), enriched.message
                )
            }
            return ToolResult.Error(enriched.message ?: "Gemini 改图调用失败", "IMAGE_EDIT_FAILED")
        }
        return buildGeminiEditResult(agentImages, savedDisplayPaths, filesList, model, n, outputPath, failedCount)
    }

    private fun buildGeminiEditResult(
        agentImages: List<com.aiverse.feature.agent.domain.model.AgentImage>,
        savedDisplayPaths: List<String>,
        filesList: List<KtxJsonObject>,
        model: String,
        n: Int,
        outputPath: String?,
        failedCount: Int,
        failureReason: String? = null
    ): ToolResult {
        if (agentImages.isEmpty()) {
            return ToolResult.Error("Gemini 改图未返回图片数据（模型可能只回复了文本，或参考图无效或过大）", "EMPTY_RESULT")
        }
        return buildImageResult(
            agentImages, savedDisplayPaths, filesList, model, n,
            outputPath, failedCount, failureReason, generatedVerb = "已编辑"
        )
    }

    private fun extractGenerateContentImageData(response: JsonObject): List<String> {
        val candidates = response.getAsJsonArray("candidates") ?: return emptyList()
        if (candidates.size() == 0) return emptyList()
        val parts = candidates[0].asJsonObject
            .getAsJsonObject("content")?.getAsJsonArray("parts") ?: return emptyList()
        val result = mutableListOf<String>()
        parts.forEach { partEl ->
            val part = partEl.asJsonObject
            val inline = part.getAsJsonObject("inlineData") ?: part.getAsJsonObject("inline_data") ?: return@forEach
            val data = inline.get("data")?.takeIf { it.isJsonPrimitive }?.asString
            if (!data.isNullOrBlank()) result.add(data)
        }
        return result
    }

    private fun supportsEdit(model: String): Boolean {
        val m = model.lowercase()
        return m.startsWith("gpt-image") ||
            m.startsWith("dall-e-2") ||
            m.contains("image") ||      // 通用兜底：网关自定义「xx-image / flux-image」等
            m.contains("imagen") ||
            m.contains("flux") ||
            m.contains("nano-banana") ||
            m.contains("gemini")
    }

    private data class ReferenceImage(val path: String, val mime: String, val bytes: ByteArray)

    companion object {
        const val TAG = "EditImageTool"

        /** paths 既接受逗号分隔字符串，也接受 JSON 字符串数组，统一解析为路径列表。 */
        internal fun parsePaths(element: JsonElement?): List<String> {
            val raw = when (element) {
                is JsonArray -> element.mapNotNull { it.jsonPrimitive?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                else -> emptyList()
            }
            return raw.flatMap { seg ->
                seg.split(',', '，', '\n')
            }.map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
