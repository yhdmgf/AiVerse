package com.aiverse.feature.plugins.runtime

import com.aiverse.feature.plugins.data.PluginRecord
import com.aiverse.feature.plugins.domain.InstalledPlugin
import com.aiverse.feature.plugins.domain.LocalizedText
import com.aiverse.feature.plugins.domain.PluginInstallOrigin
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.PluginManifest
import com.aiverse.feature.plugins.domain.newmodel.PluginSessionSourceDefinition
import com.aiverse.feature.plugins.domain.PluginSessionSourceHandlersRef
import com.aiverse.feature.plugins.domain.newmodel.PluginContent
import com.aiverse.feature.plugins.domain.newmodel.PluginRuntime
import com.aiverse.feature.plugins.domain.session.PluginMessageBlock
import java.io.File
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionSourceServiceTest {

    @Test
    fun listAndRead_parsePluginPayload() = runBlocking {
        val pluginManager = mockk<PluginManager>(relaxed = true)
        val runtimeManager = mockk<PluginRuntimeManager>(relaxed = true)
        val runtime = PluginRuntime(
            pluginId = "test.remote",
            type = "javascript",
            installDir = File("."),
            entryFile = File("main.js"),
            command = listOf("node", "main.js")
        )
        val handlers = PluginSessionSourceHandlersRef(
            listSessions = "sessionSource.listSessions",
            readSession = "sessionSource.readSession",
            sendMessage = "sessionSource.sendMessage",
            interruptSession = "sessionSource.interruptSession",
            createSession = "sessionSource.createSession"
        )
        val content = PluginContent(
            runtime = runtime,
            sessionSources = listOf(
                PluginSessionSourceDefinition(
                    pluginId = "test.remote",
                    localId = "remote",
                    title = LocalizedText.single("Remote"),
                    handlers = handlers,
                    pluginVersion = "1.0.0"
                )
            )
        )
        val record = PluginRecord(
            id = "test.remote",
            version = "1.0.0",
            enabled = true,
            installedAt = 0L,
            origin = "local",
            archiveSha256 = "",
            manifestSha256 = ""
        )
        val installed = InstalledPlugin(
            manifest = mockk<PluginManifest>(relaxed = true),
            installDir = File("."),
            enabled = true,
            content = content,
            source = PluginInstallOrigin(kind = record.origin),
            record = record
        )
        every { pluginManager.plugins } returns MutableStateFlow(listOf(installed))
        every { pluginManager.ensureLoaded() } returns Unit

        val listJson = buildJsonObject {
            put("sessions", buildJsonArray {
                add(buildJsonObject {
                    put("sessionId", "session-1")
                    put("title", "Session 1")
                    put("status", "idle")
                    put("updatedAt", 123L)
                    put("hostName", "Host")
                    put("workspaceTitle", "Workspace")
                    put("group", "Host · Workspace")
                    put("capabilities", buildJsonArray { add(JsonPrimitive("read")); add(JsonPrimitive("send")) })
                })
            })
        }
        val readJson = buildJsonObject {
            put("sessionId", "session-1")
            put("title", "Session 1")
            put("status", "running")
            put("hasMore", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("id", "message-1")
                    put("role", "assistant")
                    put("createdAt", 456L)
                    put("status", "done")
                    put("blocks", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "hello")
                        })
                    })
                })
            })
        }
        coEvery {
            runtimeManager.invoke(any(), any(), any(), any(), any(), any(), any())
        } answers {
            if (arg<String>(3) == handlers.listSessions) listJson else readJson
        }

        val service = PluginSessionSourceService(pluginManager, runtimeManager, PluginSessionRegistry())
        val listed = service.listSessions()
        assertEquals(1, listed.sessions.size)
        assertEquals("test.remote:remote", listed.sessions.single().sourceKey)
        assertEquals("Session 1", listed.sessions.single().title)

        val page = service.readSession("test.remote:remote", "session-1")
        assertEquals(1, page.messages.size)
        assertEquals("hello", (page.messages.single().blocks.single() as PluginMessageBlock.Text).text)
        assertTrue(page.messages.single().id == "message-1")
    }
}
