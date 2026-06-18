package com.runestone.app.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalServerTest {

    private lateinit var tmpDir: File
    private lateinit var server: LocalServer

    @Before
    fun setUp() {
        tmpDir = createTempDir(prefix = "runestone-local-server-test-")
        File(tmpDir, "index.html").writeText("<html><body>hello</body></html>")
        File(tmpDir, "game.js").writeText("console.log('hi');")
        File(tmpDir, "data.bin").writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        server = LocalServer(tmpDir)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        tmpDir.deleteRecursively()
    }

    @Test
    fun binds_to_localhost_only() {
        // We can connect to 127.0.0.1:port but should NOT be able to
        // reach the server from any other interface.
        val url = URL("http://127.0.0.1:${server.port}/index.html")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        conn.disconnect()

        // The address should resolve to 127.0.0.1 specifically. We
        // can't easily test "can't connect to other interfaces" without
        // knowing the device's network config, but we can at least
        // check that the server's local address is loopback.
        assertEquals("127.0.0.1", server.javaClass
            .getDeclaredField("serverSocket")
            .apply { isAccessible = true }
            .let { (it.get(server) as java.net.ServerSocket).inetAddress.hostAddress })
    }

    @Test
    fun serves_index_html_with_coop_and_coep_headers() {
        val conn = open("/index.html")
        assertEquals(200, conn.responseCode)
        assertEquals("text/html", conn.contentType?.substringBefore(";"))
        assertEquals("same-origin-allow-popups", conn.getHeaderField("Cross-Origin-Opener-Policy"))
        assertEquals("require-corp", conn.getHeaderField("Cross-Origin-Embedder-Policy"))
        assertEquals("cross-origin", conn.getHeaderField("Cross-Origin-Resource-Policy"))
        val body = conn.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue("body should contain hello", body.contains("hello"))
    }

    @Test
    fun serves_js_with_correct_mime() {
        val conn = open("/game.js")
        assertEquals(200, conn.responseCode)
        assertEquals("application/javascript", conn.contentType?.substringBefore(";"))
    }

    @Test
    fun serves_binary_files() {
        val conn = open("/data.bin")
        assertEquals(200, conn.responseCode)
        val bytes = conn.inputStream.readBytes()
        assertEquals(5, bytes.size)
        assertEquals(0, bytes[0].toInt())
        assertEquals(4, bytes[4].toInt())
    }

    @Test
    fun returns_404_for_missing_files() {
        val conn = open("/nope.html")
        assertEquals(404, conn.responseCode)
    }

    @Test
    fun rejects_path_traversal_with_dotdot() {
        val conn = open("/../etc/passwd")
        // The URL class normalizes this client-side so the
        // server might receive "/etc/passwd" instead. Either way
        // we should NOT see a 200.
        assertTrue(
            "expected 4xx, got ${conn.responseCode}",
            conn.responseCode in 400..499,
        )
    }

    @Test
    fun rejects_path_traversal_via_embedded_segments() {
        val conn = open("/foo/../../etc/passwd")
        // The URL class may also normalize this. The important thing
        // is that we don't serve a file outside tmpDir.
        assertTrue(conn.responseCode in 400..599)
    }

    @Test
    fun resolveFile_returns_file_for_simple_path() {
        val f = server.resolveFile("/index.html")
        assertNotNull(f)
        assertTrue(f!!.exists())
    }

    @Test
    fun resolveFile_rejects_dotdot() {
        assertNull(server.resolveFile("/../etc/passwd"))
    }

    @Test
    fun resolveFile_handles_query_and_fragment() {
        val f = server.resolveFile("/index.html?renderer=webgl2#section")
        assertNotNull(f)
        assertTrue(f!!.exists())
    }

    @Test
    fun head_request_returns_headers_only() {
        val conn = URL("http://127.0.0.1:${server.port}/index.html").openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        assertEquals(200, conn.responseCode)
        val body = try { conn.inputStream.readBytes() } catch (_: Exception) { ByteArray(0) }
        assertEquals("HEAD should have no body", 0, body.size)
    }

    @Test
    fun rejects_post_method() {
        val conn = URL("http://127.0.0.1:${server.port}/index.html").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        try { conn.outputStream.write("x".toByteArray()) } catch (_: Exception) {}
        assertEquals(405, conn.responseCode)
    }

    private fun open(path: String): HttpURLConnection {
        val conn = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        return conn
    }
}
