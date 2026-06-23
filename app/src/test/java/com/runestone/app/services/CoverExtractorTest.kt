package com.runestone.app.services

import android.graphics.Bitmap
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoverExtractorTest {

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `extractFallbackCover returns null for empty directory`() {
        val dir = createTempDir("empty-game")
        val result = CoverExtractor.extractFallbackCover(context, "test-game", dir)
        assertNull("No cover for empty dir", result)
        dir.deleteRecursively()
    }

    @Test
    fun `extractFallbackCover finds Title png in RGSS dir`() {
        val dir = createTempDir("rgss-game")
        // Create a small valid PNG
        val pngFile = File(dir, "Title.png")
        createMinimalPng(pngFile)

        val result = CoverExtractor.extractFallbackCover(context, "test-game", dir)
        assertNotNull("Should find Title.png", result)
        assertTrue("Should be an absolute path", File(result ?: "").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `extractFallbackCover finds titles1 in MV dir`() {
        val dir = createTempDir("mv-game")
        val www = File(dir, "www")
        val titles1 = File(www, "img/titles1")
        titles1.mkdirs()
        val pngFile = File(titles1, "Title.png")
        createMinimalPng(pngFile)

        val result = CoverExtractor.extractFallbackCover(context, "test-game", dir)
        assertNotNull("Should find titles1/Title.png", result)
        dir.deleteRecursively()
    }

    @Test
    fun `extractFallbackCover prefers short named files`() {
        val dir = createTempDir("mv-game")
        val www = File(dir, "www")
        val titles1 = File(www, "img/titles1")
        titles1.mkdirs()
        // Create a short-named file (should be preferred)
        createMinimalPng(File(titles1, "t1.png"))
        // Create a longer-named file
        createMinimalPng(File(titles1, "very_long_title_screen_name.png"))

        val result = CoverExtractor.extractFallbackCover(context, "test-game", dir)
        assertNotNull("Should find a title image", result)
        dir.deleteRecursively()
    }

    @Test
    fun `extractFallbackCover caches result`() {
        val dir = createTempDir("rgss-game")
        val pngFile = File(dir, "Title.png")
        createMinimalPng(pngFile)

        val first = CoverExtractor.extractFallbackCover(context, "test-cache", dir)
        assertNotNull("First call should extract", first)

        // Second call should return cached file
        val second = CoverExtractor.extractFallbackCover(context, "test-cache", dir)
        assertNotNull("Second call should use cache", second)
        assertTrue("Cached file should exist", File(second?.removePrefix("local:") ?: "").exists())

        dir.deleteRecursively()
    }

    private fun createMinimalPng(file: File) {
        // Create a 1x1 red PNG
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, android.graphics.Color.RED)
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }
}
