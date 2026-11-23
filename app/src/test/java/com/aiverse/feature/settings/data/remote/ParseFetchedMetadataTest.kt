package com.aiverse.feature.settings.data.remote

import com.aiverse.feature.settings.domain.model.ModelMetadata
import com.aiverse.feature.settings.domain.model.ProviderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证模型列表接口响应里自带元数据的解析（issue #211）：
 * OpenAI 兼容/聚合网关、Anthropic、Gemini 三种形态，以及「接口未提供字段不臆断」。
 */
class ParseFetchedMetadataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String, type: ProviderType) =
        ModelApiService.parseFetchedMetadata(json.parseToJsonElement(raw).jsonArray, type)

    @Test
    fun openAICompatible_relayGateway_parsesContextAndTools() {
        // 聚合网关返回的目录外模型：带 context_length 与工具支持
        val raw = """
            [
              {"id": "deepseek-v4.1-flash", "context_length": 1000000, "capabilities": {"tool_call": true}},
              {"id": "gpt-x", "context_window": 200000, "tool_call": false}
            ]
        """.trimIndent()

        val result = parse(raw, ProviderType.OPENAI)

        val deepseek = result["deepseek-v4.1-flash"]!!
        assertEquals(1_000_000, deepseek.contextTokens)
        assertEquals(1_000_000, deepseek.inputTokens)
        assertTrue(deepseek.supportsTools)

        val gptx = result["gpt-x"]!!
        assertEquals(200_000, gptx.contextTokens)
    }

    @Test
    fun anthropic_maxTokens_treatedAsOutputNotContext() {
        // Anthropic 的 max_tokens 是输出上限（如 8192），不能误当上下文窗口
        val raw = """
            [
              {"id": "claude-x", "display_name": "Claude X", "max_tokens": 8192}
            ]
        """.trimIndent()

        val result = parse(raw, ProviderType.ANTHROPIC)

        val claude = result["claude-x"]!!
        assertEquals("Claude X", claude.displayName)
        assertEquals(0, claude.contextTokens) // 未提供上下文，留空回退目录/默认
        assertEquals(8192, claude.outputTokens)
    }

    @Test
    fun gemini_stripsModelsPrefixAndParsesLimitsAndTools() {
        val raw = """
            [
              {
                "name": "models/gemini-2.5-pro",
                "displayName": "Gemini 2.5 Pro",
                "inputTokenLimit": 1048576,
                "outputTokenLimit": 65536,
                "supportedGenerationMethods": ["generateContent", "tools"]
              }
            ]
        """.trimIndent()

        val result = parse(raw, ProviderType.GEMINI)

        val gemini = result["gemini-2.5-pro"]!! // models/ 前缀已剥
        assertEquals("Gemini 2.5 Pro", gemini.displayName) // displayName 不带 models/
        assertEquals(1_048_576, gemini.contextTokens)
        assertEquals(65_536, gemini.outputTokens)
        assertTrue(gemini.supportsTools)
    }

    @Test
    fun missingFields_defaultToZeroAndFalse_notInferred() {
        // 接口什么能力都没说：上下文留 0、能力 false，由上层回退目录/默认，不臆断
        val raw = """[{"id": "mystery-model"}]"""

        val result = parse(raw, ProviderType.OPENAI)

        val mystery = result["mystery-model"]!!
        assertEquals(0, mystery.contextTokens)
        assertEquals(false, mystery.supportsTools)
        assertEquals(false, mystery.supportsVision)
        assertEquals(ModelMetadata.Source.INFERRED, mystery.source)
    }

    @Test
    fun doubleFormattedContext_isAccepted() {
        // 某些网关把上下文返回成 double 形式（1000000.0）
        val raw = """[{"id": "m", "context_length": 1000000.0}]"""

        val result = parse(raw, ProviderType.OPENAI)

        assertEquals(1_000_000, result["m"]!!.contextTokens)
    }
}
