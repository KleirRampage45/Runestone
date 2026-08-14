package com.runestone.app.engine

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EngineRegistryTest {

    /** Base class for test engines to avoid repeating launch() override. */
    private abstract class TestEngine : GameEngine {
        override fun launch(context: Context, gameFolder: File, config: GameConfig) {}
    }

    private lateinit var mockMvEngine: GameEngine
    private lateinit var mockMkxpEngine: GameEngine
    private lateinit var mockHtmlEngine: GameEngine

    @Before
    fun setUp() {
        val field = EngineRegistry::class.java.getDeclaredField("engines")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(EngineRegistry) as MutableMap<String, GameEngine>
        map.clear()

        mockMvEngine = object : TestEngine() {
            override val id = "webview-mv"
            override val name = "RPG Maker MV"
            override val version = "1.0"
            override val priority = 30

            override fun canRun(gameFolder: File): Boolean =
                File(gameFolder, "www/index.html").exists() &&
                    File(gameFolder, "www/data/System.json").exists()
        }

        mockMkxpEngine = object : TestEngine() {
            override val id = "mkxp-z"
            override val name = "RPG Maker XP/VX/VX Ace"
            override val version = "1.0"
            override val priority = 10

            override fun canRun(gameFolder: File): Boolean =
                File(gameFolder, "Game.ini").exists() &&
                    (File(gameFolder, "Game.rxproj").exists() ||
                        File(gameFolder, "Game.rvproj").exists() ||
                        File(gameFolder, "Game.rvproj2").exists())
        }

        mockHtmlEngine = object : TestEngine() {
            override val id = "html"
            override val name = "Generic HTML5"
            override val version = "1.0"
            override val priority = 50

            override fun canRun(gameFolder: File): Boolean =
                File(gameFolder, "index.html").exists()
        }
    }

    @Test
    fun `register adds engine and get returns it`() {
        EngineRegistry.register(mockMvEngine)
        val result = EngineRegistry.get("webview-mv")
        assertNotNull(result)
        assertEquals("RPG Maker MV", result?.name)
    }

    @Test
    fun `get returns null for unregistered engine`() {
        assertNull(EngineRegistry.get("nonexistent"))
    }

    @Test
    fun `all returns all registered engines`() {
        EngineRegistry.register(mockMvEngine)
        EngineRegistry.register(mockMkxpEngine)
        assertEquals(2, EngineRegistry.all().size)
    }

    @Test
    fun `all returns empty list when nothing registered`() {
        assertEquals(0, EngineRegistry.all().size)
    }

    @Test
    fun `detect returns highest-priority matching engine`() {
        EngineRegistry.register(mockMvEngine)
        EngineRegistry.register(mockMkxpEngine)
        EngineRegistry.register(mockHtmlEngine)

        val dir = createTempDir("mv-game")
        File(dir, "www").mkdirs()
        File(dir, "www/index.html").writeText("")
        File(dir, "www/data").mkdirs()
        File(dir, "www/data/System.json").writeText("{}")

        val result = EngineRegistry.detect(dir)
        assertNotNull(result)
        assertEquals("webview-mv", result?.id)

        dir.deleteRecursively()
    }

    @Test
    fun `detect respects priority ordering`() {
        EngineRegistry.register(mockMvEngine)
        EngineRegistry.register(mockMkxpEngine)

        val dir = createTempDir("dual-game")
        File(dir, "Game.ini").writeText("[Game]\nTitle=Test")
        File(dir, "Game.rvproj2").writeText("")
        File(dir, "www").mkdirs()
        File(dir, "www/index.html").writeText("")
        File(dir, "www/data").mkdirs()
        File(dir, "www/data/System.json").writeText("{}")

        val result = EngineRegistry.detect(dir)
        assertNotNull(result)
        assertEquals("mkxp-z", result?.id)

        dir.deleteRecursively()
    }

    @Test
    fun `detect returns null when no engine matches`() {
        EngineRegistry.register(mockMvEngine)
        EngineRegistry.register(mockMkxpEngine)

        val dir = createTempDir("unknown-game")
        File(dir, "some_random_file.bin").writeText("")

        assertNull(EngineRegistry.detect(dir))

        dir.deleteRecursively()
    }

    @Test
    fun `detect handles engine exception gracefully`() {
        val crashingEngine = object : TestEngine() {
            override val id = "crash"
            override val name = "Crash Engine"
            override val version = "1.0"
            override val priority = 5
            override fun canRun(gameFolder: File): Boolean = throw RuntimeException("BOOM")
        }

        EngineRegistry.register(crashingEngine)
        EngineRegistry.register(mockHtmlEngine)

        val dir = createTempDir("html-game")
        File(dir, "index.html").writeText("")

        val result = EngineRegistry.detect(dir)
        assertNotNull(result)
        assertEquals("html", result?.id)

        dir.deleteRecursively()
    }

    @Test
    fun `detect returns first matching engine sorted by priority`() {
        val engines = listOf(
            object : TestEngine() {
                override val id = "p10"
                override val name = "Priority 10"
                override val version = "1.0"
                override val priority = 10
                override fun canRun(gameFolder: File) = true
            },
            object : TestEngine() {
                override val id = "p30"
                override val name = "Priority 30"
                override val version = "1.0"
                override val priority = 30
                override fun canRun(gameFolder: File) = true
            },
            object : TestEngine() {
                override val id = "p50"
                override val name = "Priority 50"
                override val version = "1.0"
                override val priority = 50
                override fun canRun(gameFolder: File) = true
            },
        )
        engines.forEach { EngineRegistry.register(it) }

        val dir = createTempDir("any-game")
        val result = EngineRegistry.detect(dir)
        assertEquals("p10", result?.id)

        dir.deleteRecursively()
    }

    @Test
    fun `detectMetadata returns metadata from matching engine`() {
        EngineRegistry.register(mockMvEngine)

        val dir = createTempDir("mv-game")
        File(dir, "www").mkdirs()
        File(dir, "www/index.html").writeText("")
        File(dir, "www/data").mkdirs()
        File(dir, "www/data/System.json").writeText("{}")

        val metadata = EngineRegistry.detectMetadata(dir)
        assertNotNull(metadata)
        assertEquals("webview-mv", metadata?.engine)

        dir.deleteRecursively()
    }

    @Test
    fun `detectMetadata returns null when no engine matches`() {
        EngineRegistry.register(mockMvEngine)

        val dir = createTempDir("empty")
        assertNull(EngineRegistry.detectMetadata(dir))

        dir.deleteRecursively()
    }
}
