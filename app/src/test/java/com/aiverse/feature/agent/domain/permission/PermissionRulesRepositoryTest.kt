package com.aiverse.feature.agent.domain.permission

import android.content.Context
import com.aiverse.core.util.FileLogger
import com.aiverse.core.watch.FileChangeHub
import com.aiverse.feature.agent.domain.container.ContainerInstaller
import com.aiverse.feature.settings.data.repository.ContainerSettingsRepository
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.PathHomeResolver
import com.aiverse.feature.workspace.domain.ProjectAiVerseRoot
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PermissionRulesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repository: PermissionRulesRepository
    private lateinit var target: File
    private lateinit var link: File

    @Before
    fun setUp() {
        mockkObject(FileLogger)
        every { FileLogger.i(any(), any()) } returns Unit
        val context: Context = mockk()
        val workspace: WorkspaceRepository = mockk()
        val installer: ContainerInstaller = mockk()
        val settings: ContainerSettingsRepository = mockk()
        val home: PathHomeResolver = mockk()
        val projectRoot: ProjectAiVerseRoot = mockk()
        val changes: FileChangeHub = mockk()
        val filesDir = tmp.newFolder("files")
        val workspaceDir = tmp.newFolder("workspace")
        target = File(workspaceDir, "project/.aiverse").apply { mkdirs() }
        link = File(workspaceDir, ".aiverse")
        Files.createSymbolicLink(link.toPath(), File("/root/workspace/project/.aiverse").toPath())
        every { context.filesDir } returns filesDir
        every { workspace.currentPath() } returns workspaceDir.absolutePath
        every { workspace.current } returns MutableStateFlow(null)
        every { installer.aiverseDir } returns File(filesDir, "aiverse")
        every { installer.rootfsDirFor(any()) } returns File(filesDir, "rootfs")
        every { settings.activeProfileIdFlow } returns emptyFlow()
        every { home.home() } returns "/root"
        every { home.expandHome(any()) } answers { firstArg<String>().replaceFirst("~/", "/root/") }
        every { projectRoot.forPath(workspaceDir.absolutePath) } returns link
        val mapper = WorkspacePathMapper(workspace, installer, settings, home)
        repository = PermissionRulesRepository(context, workspace, projectRoot, changes, mapper)
    }

    @After
    fun tearDown() {
        unmockkObject(FileLogger)
    }

    @Test
    fun loadEffectiveRules_absoluteProjectLink_loadsExistingRules() = runBlocking {
        File(target, "permissions.json").writeText("""{"permissions":{"allow":["readFile"],"deny":["writeFile"]}}""")
        assertEquals(
            listOf(
                PermissionRule("readFile", "*"),
                PermissionRule("writeFile", "*", PermissionDecision.DENY)
            ),
            repository.loadEffectiveForCurrentProject()
        )
    }

    @Test
    fun addRule_absoluteProjectLink_createsFileAndPreservesLink() = runBlocking {
        val rule = PermissionRule("readFile", "*")
        repository.add(PermissionScope.PROJECT, rule)
        val saved = File(target, "permissions.json")
        assertTrue(saved.isFile)
        assertTrue(saved.readText().contains("readFile"))
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals(listOf(rule), repository.loadEffectiveForCurrentProject())
    }
}
