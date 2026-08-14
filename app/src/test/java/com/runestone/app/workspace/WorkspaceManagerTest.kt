package com.runestone.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WorkspaceManagerTest {

    private lateinit var workspaceManager: WorkspaceManager

    @Before
    fun setUp() {
        workspaceManager = WorkspaceManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `scanInstalledGames returns empty list when no games dir`() {
        assertTrue(workspaceManager.scanInstalledGames().isEmpty())
    }

    @Test
    fun `scanInstalledGames returns empty when games dir is empty`() {
        workspaceManager.gamesBaseDir.mkdirs()
        assertTrue(workspaceManager.scanInstalledGames().isEmpty())
    }

    @Test
    fun `allocateGameDir returns same path for same name`() {
        val dir1 = workspaceManager.allocateGameDir("test-game")
        val dir2 = workspaceManager.allocateGameDir("test-game")
        assertEquals(dir1.absolutePath, dir2.absolutePath)
    }

    @Test
    fun `allocateGameDir returns different path when first dir has manifest`() {
        val dir1 = workspaceManager.allocateGameDir("test-game")
        dir1.mkdirs()
        File(dir1, "manifest.json").writeText("{}")
        val dir2 = workspaceManager.allocateGameDir("test-game")
        assertTrue("dir1=$dir1 dir2=$dir2", dir1.absolutePath != dir2.absolutePath)
    }

    @Test
    fun `isInstalled returns false for non-existent game`() {
        assertFalse(workspaceManager.isInstalled("nonexistent"))
    }

    @Test
    fun `isInstalled returns true when game has original dir`() {
        val gameDir = workspaceManager.allocateGameDir("test-game").apply { mkdirs() }
        File(gameDir, "original").mkdirs()
        File(gameDir, "manifest.json").writeText("{}")
        assertTrue(workspaceManager.isInstalled(gameDir.name))
    }

    @Test
    fun `removeGame deletes game directory`() {
        val gameDir = workspaceManager.allocateGameDir("test-game").apply { mkdirs() }
        File(gameDir, "original").mkdirs()
        workspaceManager.removeGame(gameDir.name)
        assertFalse(gameDir.exists())
    }

    @Test
    fun `removeGame with keepSaves preserves saves`() {
        val gameDir = workspaceManager.allocateGameDir("test-game").apply { mkdirs() }
        File(gameDir, "original").mkdirs()
        File(gameDir, "saves").mkdirs()
        File(gameDir, "saves/Save1.lsd").writeText("data")
        workspaceManager.removeGame(gameDir.name, keepSaves = true)
        assertTrue(gameDir.exists())
        assertTrue(File(gameDir, "saves").exists())
    }

    @Test
    fun `ensureWorkspace creates all subdirectories`() {
        val gameDir = workspaceManager.allocateGameDir("test-game")
        workspaceManager.ensureWorkspace(gameDir.name)
        assertTrue(File(gameDir, "original").exists())
        assertTrue(File(gameDir, "incoming").exists())
        assertTrue(File(gameDir, "saves").exists())
        assertTrue(File(gameDir, "save_backups").exists())
        assertTrue(File(gameDir, "patches").exists())
    }

    @Test
    fun `ensureWorkspace creates nomedia files`() {
        val gameDir = workspaceManager.allocateGameDir("test-game")
        workspaceManager.ensureWorkspace(gameDir.name)
        assertTrue(File(gameDir, ".nomedia").exists())
        assertTrue(File(gameDir, "original/.nomedia").exists())
    }

    @Test
    fun `path helpers return correct paths`() {
        assertTrue(workspaceManager.gameDir("g").absolutePath.endsWith("games/g"))
        assertTrue(workspaceManager.originalDir("g").absolutePath.endsWith("games/g/original"))
        assertTrue(workspaceManager.savesDir("g").absolutePath.endsWith("games/g/saves"))
        assertTrue(workspaceManager.incomingDir("g").absolutePath.endsWith("games/g/incoming"))
    }

    @Test
    fun `removeGame on non-existent dir does not throw`() {
        workspaceManager.removeGame("nonexistent")
    }

    @Test
    fun `removeGame invalidates scan cache`() {
        val gameDir = workspaceManager.allocateGameDir("test-game").apply { mkdirs() }
        File(gameDir, "original").mkdirs()
        File(gameDir, "manifest.json").writeText("{}")
        workspaceManager.scanInstalledGames()
        workspaceManager.removeGame(gameDir.name)
        assertTrue(workspaceManager.scanInstalledGames().none { it.storageName == gameDir.name })
    }
}
