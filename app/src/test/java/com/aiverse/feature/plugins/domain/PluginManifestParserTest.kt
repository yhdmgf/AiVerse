package com.aiverse.feature.plugins.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestParserTest {

    private fun parse(text: String) = PluginManifestParser.parse(text)

    private fun success(text: String): PluginManifest {
        val result = parse(text)
        check(result is ManifestParseResult.Success) { "应解析成功: $result" }
        return result.manifest
    }

    @Test
    fun sessionSourceContribution_isParsed() {
        val json = """
            {
              "manifestVersion": 1,
              "id": "demo.remote",
              "name": "Remote",
              "version": "1.0.0",
              "publisher": "demo",
              "repository": "https://example.com/demo/remote",
              "description": "Remote sessions",
              "license": "MIT",
              "engines": {"pluginApi": "^1.0.0"},
              "runtime": {"type": "javascript", "entry": "dist/main.js"},
              "contributes": {
                "sessionSources": [
                  {
                    "id": "ds-harness",
                    "title": {"default": "DS Harness Remote", "zh-CN": "DS Harness 远程"},
                    "handlers": {
                      "listSessions": "sessionSource.listSessions",
                      "readSession": "sessionSource.readSession",
                      "sendMessage": "sessionSource.sendMessage",
                      "interruptSession": "sessionSource.interruptSession",
                      "createSession": "sessionSource.createSession",
                      "refresh": "sessionSource.refresh"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val manifest = success(json)
        val source = manifest.sessionSources.single()
        assertEquals("ds-harness", source.id)
        assertEquals("DS Harness 远程", source.title.resolve("zh-CN"))
        assertEquals("sessionSource.listSessions", source.handlers.listSessions)
        assertEquals("sessionSource.readSession", source.handlers.readSession)
        assertEquals("sessionSource.refresh", source.handlers.refresh)
        assertEquals(1, manifest.contributionCount)
    }

    @Test
    fun sessionSource_withoutRuntime_isRejected() {
        val json = """
            {
              "manifestVersion": 1,
              "id": "demo.remote",
              "name": "Remote",
              "version": "1.0.0",
              "publisher": "demo",
              "repository": "https://example.com/demo/remote",
              "description": "Remote sessions",
              "license": "MIT",
              "engines": {"pluginApi": "^1.0.0"},
              "contributes": {
                "sessionSources": [
                  {
                    "id": "ds-harness",
                    "title": {"default": "Remote"},
                    "handlers": {
                      "listSessions": "sessionSource.listSessions",
                      "readSession": "sessionSource.readSession"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parse(json)
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(result is ManifestParseResult.Failure && result.errors.any { it.detail == "runtime" })
    }

    // ── contributes.ssh 声明 ──

    /** 只含 ssh 声明的最小清单（无其它贡献），验证 ssh 单独即可满足 contributes 非空。 */
    private fun sshManifest(purposeJson: String, extraField: String = "") = """
        {
          "manifestVersion": 1,
          "id": "demo.ssh",
          "name": "Ssh",
          "version": "1.0.0",
          "publisher": "demo",
          "repository": "https://example.com/demo/ssh",
          "description": "Ssh demo",
          "license": "MIT",
          "engines": {"pluginApi": "^1.0.0"},
          "contributes": {
            "ssh": { "purpose": $purposeJson $extraField}
          }
        }
    """.trimIndent()

    @Test
    fun sshContribution_isParsed() {
        val manifest = success(sshManifest("\"在已配置服务器执行命令\""))
        val ssh = manifest.ssh
        assertTrue(ssh != null)
        assertEquals("在已配置服务器执行命令", ssh?.purpose)
        assertEquals(1, manifest.contributionCount)
    }

    @Test
    fun sshContribution_emptyPurpose_isRejected() {
        val result = parse(sshManifest("\"   \""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(result is ManifestParseResult.Failure && result.errors.any { it.detail == "contributes.ssh.purpose" })
    }

    @Test
    fun sshContribution_missingPurpose_isRejected() {
        val json = """
            {
              "manifestVersion": 1,
              "id": "demo.ssh",
              "name": "Ssh",
              "version": "1.0.0",
              "publisher": "demo",
              "repository": "https://example.com/demo/ssh",
              "description": "Ssh demo",
              "license": "MIT",
              "engines": {"pluginApi": "^1.0.0"},
              "contributes": { "ssh": {} }
            }
        """.trimIndent()
        val result = parse(json)
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(result is ManifestParseResult.Failure && result.errors.any { it.detail == "contributes.ssh.purpose" })
    }

    @Test
    fun sshContribution_overlongPurpose_isRejected() {
        val long = "\"" + "a".repeat(201) + "\""
        val result = parse(sshManifest(long))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(result is ManifestParseResult.Failure && result.errors.any { it.detail == "contributes.ssh.purpose" })
    }

    @Test
    fun sshContribution_unsupportedField_isRejected() {
        val result = parse(sshManifest("\"ok\"", extraField = ", \"host\": \"evil.example\""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any { it.detail == "contributes.ssh.host" }
        )
    }

    /** 未声明 ssh 时清单仍合法，ssh 字段为 null。 */
    @Test
    fun ssh_absent_isNull() {
        val json = """
            {
              "manifestVersion": 1,
              "id": "demo.plain",
              "name": "Plain",
              "version": "1.0.0",
              "publisher": "demo",
              "repository": "https://example.com/demo/plain",
              "description": "No ssh",
              "license": "MIT",
              "engines": {"pluginApi": "^1.0.0"},
              "contributes": { "agents": [ {"id": "agent-remote", "file": "agent.md"} ] }
            }
        """.trimIndent()
        val manifest = success(json)
        assertNull(manifest.ssh)
    }

    // ── contributes.styleTokens 声明 ──

    /** 只含 styleTokens 声明的最小清单（无其它贡献）。 */
    private fun styleTokensManifest(styleTokensJson: String) = """
        {
          "manifestVersion": 1,
          "id": "demo.style",
          "name": "Style",
          "version": "1.0.0",
          "publisher": "demo",
          "repository": "https://example.com/demo/style",
          "description": "Style demo",
          "license": "MIT",
          "engines": {"pluginApi": "^1.0.0"},
          "contributes": { "styleTokens": $styleTokensJson }
        }
    """.trimIndent()

    @Test
    fun styleTokens_isParsed() {
        val manifest = success(
            styleTokensManifest(
                """{
                    "cardRadius": 20,
                    "userBubbleOpacity": 80,
                    "bubbleUserColor": "primary",
                    "topBarTextColor": "#FF8800",
                    "chatInputMinHeight": 56,
                    "chatInputShadow": 12
                }"""
            )
        )
        val tokens = manifest.styleTokens
        assertTrue(tokens != null)
        assertEquals(20, tokens?.cardRadius)
        assertEquals(80, tokens?.userBubbleOpacity)
        assertEquals("primary", tokens?.bubbleUserColor)
        assertEquals("#FF8800", tokens?.topBarTextColor)
        assertEquals(56, tokens?.chatInputMinHeight)
        assertEquals(12, tokens?.chatInputShadow)
        assertEquals(1, manifest.contributionCount)
    }

    @Test
    fun styleTokens_boundaryValues_areAccepted() {
        val manifest = success(
            styleTokensManifest(
                """{
                    "cardRadius": 0,
                    "imageRadius": 48,
                    "userBubbleOpacity": 10,
                    "chatPanelOpacity": 100,
                    "chatInputMinHeight": 160,
                    "chatInputShadow": 40
                }"""
            )
        )
        val tokens = manifest.styleTokens
        assertEquals(0, tokens?.cardRadius)
        assertEquals(48, tokens?.imageRadius)
        assertEquals(10, tokens?.userBubbleOpacity)
        assertEquals(100, tokens?.chatPanelOpacity)
        assertEquals(160, tokens?.chatInputMinHeight)
        assertEquals(40, tokens?.chatInputShadow)
    }

    @Test
    fun styleTokens_outOfRange_isRejected() {
        val result = parse(styleTokensManifest("""{ "cardRadius": 49 }"""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any { it.detail == "contributes.styleTokens.cardRadius" }
        )
    }

    @Test
    fun styleTokens_opacityBelowMinimum_isRejected() {
        val result = parse(styleTokensManifest("""{ "userBubbleOpacity": 9 }"""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any { it.detail == "contributes.styleTokens.userBubbleOpacity" }
        )
    }

    @Test
    fun styleTokens_invalidColor_isRejected() {
        val result = parse(styleTokensManifest("""{ "topBarColor": "#12345" }"""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any { it.detail == "contributes.styleTokens.topBarColor" }
        )
    }

    @Test
    fun styleTokens_unknownSemanticColor_isRejected() {
        val result = parse(styleTokensManifest("""{ "iconColor": "neon" }"""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any { it.detail == "contributes.styleTokens.iconColor" }
        )
    }

    @Test
    fun styleTokens_unsupportedField_isRejected() {
        val result = parse(styleTokensManifest("""{ "cardRadius": 16, "fontSize": 20 }"""))
        assertTrue(result is ManifestParseResult.Failure)
        assertTrue(
            result is ManifestParseResult.Failure &&
                result.errors.any {
                    it.code == PluginErrorCode.UNSUPPORTED_FIELD && it.detail == "contributes.styleTokens.fontSize"
                }
        )
    }

    /** 未声明 styleTokens 时清单仍合法，字段为 null，不计入贡献数。 */
    @Test
    fun styleTokens_absent_isNull() {
        val json = """
            {
              "manifestVersion": 1,
              "id": "demo.plain",
              "name": "Plain",
              "version": "1.0.0",
              "publisher": "demo",
              "repository": "https://example.com/demo/plain",
              "description": "No style",
              "license": "MIT",
              "engines": {"pluginApi": "^1.0.0"},
              "contributes": { "agents": [ {"id": "agent-remote", "file": "agent.md"} ] }
            }
        """.trimIndent()
        val manifest = success(json)
        assertNull(manifest.styleTokens)
        assertEquals(1, manifest.contributionCount)
    }

    @Test
    fun mergeStyleTokens_takesFirstNonNullPerField() {
        val first = PluginStyleTokensRef(cardRadius = 20, bubbleUserColor = "primary")
        val second = PluginStyleTokensRef(cardRadius = 32, buttonRadius = 12, bubbleUserColor = "#FF8800")
        val third = PluginStyleTokensRef(buttonRadius = 24, iconColor = "onSurface")

        val merged = PluginStyleTokensRef.mergeStyleTokens(listOf(first, second, third))

        assertEquals(20, merged.cardRadius)
        assertEquals(12, merged.buttonRadius)
        assertEquals("primary", merged.bubbleUserColor)
        assertEquals("onSurface", merged.iconColor)
    }

    @Test
    fun mergeStyleTokens_skipsNullEntriesAndEmptyList() {
        val later = PluginStyleTokensRef(cardRadius = 24)
        val merged = PluginStyleTokensRef.mergeStyleTokens(listOf(null, later, null))
        assertEquals(24, merged.cardRadius)

        val empty = PluginStyleTokensRef.mergeStyleTokens(emptyList())
        assertNull(empty.cardRadius)
        assertNull(empty.bubbleUserColor)
    }
}
