package com.runestone.app.engine

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tiny single-purpose HTTP server for serving one game directory over
 * `http://127.0.0.1:PORT/` with the COOP/COEP headers required for
 * cross-origin isolation.
 *
 * Why this exists: the Android system WebView treats `file://` as a
 * null origin and refuses to set COOP/COEP on the main document, which
 * means `crossOriginIsolated` is false and `SharedArrayBuffer` is
 * unavailable. WebAssembly modules that use shared memory (Effekseer's
 * WASM runtime, in particular) silently fail to instantiate, which
 * hangs the game's main loop on a loading spinner with no console
 * error.
 *
 * Serving the game over `http://127.0.0.1:PORT/` from a single
 * configured root gives the WebView a real origin. We then set
 * `Cross-Origin-Opener-Policy: same-origin` and
 * `Cross-Origin-Embedder-Policy: require-corp` on every response,
 * which enables `crossOriginIsolated` and unlocks `SharedArrayBuffer`.
 * The CORP header is also added so cross-origin-isolated subresource
 * requests from the page's own scripts succeed.
 *
 * Security: the server binds to `127.0.0.1` only. It serves exactly
 * one configured root directory. Path traversal is rejected (any URL
 * with `..` segments returns 404). There is no way for a remote
 * network host to reach it.
 *
 * Lifecycle: the owning activity should call [start] in onCreate and
 * [stop] in onDestroy. The server runs on daemon threads and exits
 * cleanly when the socket is closed.
 */
class LocalServer(private val rootDir: File) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicLong(0)
    private val connectionCounter = AtomicLong(0)

    /** Local port the server is listening on. 0 before [start] or after [stop]. */
    val port: Int get() = serverSocket?.localPort ?: 0

    /** Bind and start accepting connections. Idempotent. */
    fun start() {
        if (running.getAndSet(1L) == 1L) return
        val sock = ServerSocket(0, BACKLOG, InetAddress.getByName(LOCALHOST))
        serverSocket = sock
        val acceptThread = Thread({ acceptLoop(sock) }, "Runestone-LocalServer-accept")
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    /** Stop the server. Safe to call from any thread. */
    fun stop() {
        if (running.getAndSet(0L) == 0L) return
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun acceptLoop(sock: ServerSocket) {
        while (running.get() == 1L) {
            val client = try { sock.accept() } catch (_: Exception) { return }
            val id = connectionCounter.incrementAndGet()
            val handler = Thread({ handleClient(client, id) }, "Runestone-LocalServer-$id")
            handler.isDaemon = true
            handler.start()
        }
    }

    private fun handleClient(socket: Socket, id: Long) {
        try {
            socket.use { s ->
                val input = s.getInputStream()
                val output = s.getOutputStream()

                // Read request line + headers (cap at 16 KB to avoid
                // malicious clients flooding us with headers).
                val headerBytes = readHeaders(input)
                if (headerBytes.isEmpty()) {
                    writeEmpty(output, 400, "Bad Request")
                    return
                }
                val headerText = String(headerBytes, Charsets.ISO_8859_1)
                val firstLine = headerText.lineSequence().firstOrNull() ?: run {
                    writeEmpty(output, 400, "Bad Request")
                    return
                }
                val parts = firstLine.split(' ')
                if (parts.size < 2) {
                    writeEmpty(output, 400, "Bad Request")
                    return
                }
                val method = parts[0]
                val rawPath = parts[1]
                if (method != "GET" && method != "HEAD") {
                    writeEmpty(output, 405, "Method Not Allowed")
                    return
                }

                val file = resolveFile(rawPath) ?: run {
                    writeEmpty(output, 404, "Not Found")
                    return
                }
                if (!file.exists() || !file.isFile) {
                    writeEmpty(output, 404, "Not Found")
                    return
                }

                val mime = mimeFor(file.name)
                val headers = buildResponseHeaders(mime, file.length())
                writeResponse(output, method == "HEAD", 200, "OK", headers, file)
            }
        } catch (e: Exception) {
            // Don't crash the thread on a single bad request.
            try {
                writeEmpty(socket.getOutputStream(), 500, "Internal Server Error")
            } catch (_: Exception) { }
        }
    }

    private fun readHeaders(input: InputStream): ByteArray {
        val buf = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < MAX_HEADER_BYTES) {
            val n = try { input.read(buf) } catch (_: Exception) { return out.toByteArray() }
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            val cur = out.toByteArray()
            if (cur.size >= 4) {
                // Look for end-of-headers CRLF CRLF
                val needle = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
                if (endsWith(cur, needle)) {
                    return cur
                }
            }
        }
        return out.toByteArray()
    }

    private fun endsWith(haystack: ByteArray, needle: ByteArray): Boolean {
        if (haystack.size < needle.size) return false
        for (i in 0 until needle.size) {
            if (haystack[haystack.size - needle.size + i] != needle[i]) return false
        }
        return true
    }

    /**
     * Resolve a request path to a file under [rootDir]. Rejects path
     * traversal (any segment equal to `..` or absolute paths).
     */
    internal fun resolveFile(rawPath: String): File? {
        val noQuery = rawPath.substringBefore('?')
        val noFrag = noQuery.substringBefore('#')
        if (noFrag.contains("..")) return null
        if (noFrag.startsWith("/")) {
            val rel = noFrag.substring(1)
            if (rel.startsWith("/") || rel.startsWith("\\")) return null
            val f = File(rootDir, rel)
            // Double-check the resolved file is still under rootDir.
            val canonicalRoot = try { rootDir.canonicalPath } catch (_: Exception) { rootDir.absolutePath }
            val canonicalFile = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
            if (!canonicalFile.startsWith(canonicalRoot)) return null
            return f
        }
        return null
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".html", true) || name.endsWith(".htm", true) -> "text/html"
        name.endsWith(".js", true) || name.endsWith(".mjs", true) -> "application/javascript"
        name.endsWith(".css", true) -> "text/css"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".wasm", true) -> "application/wasm"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".svg", true) -> "image/svg+xml"
        name.endsWith(".ico", true) -> "image/x-icon"
        name.endsWith(".ogg", true) -> "audio/ogg"
        name.endsWith(".opus", true) -> "audio/ogg"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".wav", true) -> "audio/wav"
        name.endsWith(".m4a", true) -> "audio/mp4"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".xml", true) -> "application/xml"
        name.endsWith(".ttf", true) -> "font/ttf"
        name.endsWith(".otf", true) -> "font/otf"
        name.endsWith(".woff", true) -> "font/woff"
        name.endsWith(".woff2", true) -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun buildResponseHeaders(mime: String, length: Long): List<Pair<String, String>> {
        val headers = mutableListOf(
            "Content-Type" to mime,
            "Content-Length" to length.toString(),
            "Connection" to "close",
            "Cache-Control" to "no-store",
            // COOP + COEP: enable cross-origin isolation. Without these,
            // the page cannot use SharedArrayBuffer, which is required
            // by Effekseer's WASM runtime and any other WASM module
            // that needs shared memory.
            "Cross-Origin-Opener-Policy" to "same-origin",
            "Cross-Origin-Embedder-Policy" to "require-corp",
            // CORP: allow our own subresources to be loaded by a
            // cross-origin-isolated page. We set this on every
            // response so even subresources pulled in by Service
            // Workers / Workers / SharedWorkers succeed.
            "Cross-Origin-Resource-Policy" to "cross-origin",
        )
        return headers
    }

    private fun writeResponse(
        output: OutputStream,
        headOnly: Boolean,
        status: Int,
        reason: String,
        headers: List<Pair<String, String>>,
        body: File,
    ) {
        writeStatusLine(output, status, reason)
        for ((k, v) in headers) {
            output.write("$k: $v\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        output.write("\r\n".toByteArray(Charsets.ISO_8859_1))
        output.flush()
        if (headOnly) return
        FileInputStream(body).use { it.copyTo(output) }
        output.flush()
    }

    private fun writeEmpty(output: OutputStream, status: Int, reason: String) {
        writeStatusLine(output, status, reason)
        output.write("Content-Length: 0\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Connection: close\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("\r\n".toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun writeStatusLine(output: OutputStream, status: Int, reason: String) {
        output.write("HTTP/1.1 $status $reason\r\n".toByteArray(Charsets.ISO_8859_1))
    }

    companion object {
        private const val LOCALHOST = "127.0.0.1"
        private const val BACKLOG = 50
        private const val MAX_HEADER_BYTES = 16 * 1024
    }
}
