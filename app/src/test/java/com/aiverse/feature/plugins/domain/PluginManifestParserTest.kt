package com.aiverse.feature.plugins.domain

import org.junit.Assert.assertEquals
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
}
