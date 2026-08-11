package com.swimweek.app.data

import android.util.Log
import com.swimweek.app.BuildConfig
import com.swimweek.app.domain.CrowdOccupancy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

/**
 * One-shot CrowdMonitor WebSocket client.
 *
 * Protocol (same as stadt-zuerich.ch):
 *  - connect to [WS_HOST]:[WS_PORT]/[WS_PATH]
 *  - send text frame `"all"`
 *  - receive JSON array of {uid,name,currentfill,maxspace,freespace}
 *
 * Failures never throw to callers — returns Result.failure.
 */
@Singleton
class CrowdMonitorClient @Inject constructor() {

    suspend fun fetchAll(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result<List<CrowdOccupancy>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(timeoutMs) {
                    openAndFetch()
                }
            }.onFailure { e ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CrowdMonitor fetch failed: ${e.message}")
                }
            }
        }

    private fun openAndFetch(): List<CrowdOccupancy> {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket(WS_HOST, WS_PORT) as javax.net.ssl.SSLSocket
        socket.soTimeout = SOCKET_READ_TIMEOUT_MS
        try {
            socket.startHandshake()
            val out = socket.outputStream
            val input = socket.inputStream
            performHttpUpgrade(out, input)
            // City site waits 500ms; short delay is enough for this server
            Thread.sleep(200)
            writeTextFrame(out, "all")
            val payload = readFullTextMessage(input)
            return parsePayload(payload)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun performHttpUpgrade(out: OutputStream, input: InputStream) {
        val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val req = buildString {
            append("GET $WS_PATH HTTP/1.1\r\n")
            append("Host: $WS_HOST:$WS_PORT\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $key\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        out.write(req.toByteArray(Charsets.US_ASCII))
        out.flush()

        val header = readHttpHeaders(input)
        if (!header.contains("101") || !header.lowercase().contains("upgrade")) {
            throw IllegalStateException("WebSocket upgrade failed: ${header.lines().firstOrNull()}")
        }
    }

    private fun readHttpHeaders(input: InputStream): String {
        val buf = ByteArrayOutputStream()
        var prev = 0
        var count = 0
        while (count < 8192) {
            val b = input.read()
            if (b < 0) break
            buf.write(b)
            count++
            if (prev == '\r'.code && b == '\n'.code) {
                val bytes = buf.toByteArray()
                if (bytes.size >= 4 &&
                    bytes[bytes.size - 4] == '\r'.code.toByte() &&
                    bytes[bytes.size - 3] == '\n'.code.toByte() &&
                    bytes[bytes.size - 2] == '\r'.code.toByte() &&
                    bytes[bytes.size - 1] == '\n'.code.toByte()
                ) {
                    break
                }
            }
            prev = b
        }
        return buf.toString(Charsets.US_ASCII.name())
    }

    private fun writeTextFrame(out: OutputStream, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val masked = ByteArray(payload.size) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        val header = ByteArrayOutputStream()
        header.write(0x81) // FIN + text
        val n = payload.size
        when {
            n < 126 -> header.write(0x80 or n)
            n < 65536 -> {
                header.write(0x80 or 126)
                header.write((n shr 8) and 0xff)
                header.write(n and 0xff)
            }
            else -> {
                header.write(0x80 or 127)
                val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(n.toLong())
                header.write(bb.array())
            }
        }
        header.write(mask)
        header.write(masked)
        out.write(header.toByteArray())
        out.flush()
    }

    private fun readFullTextMessage(input: InputStream): String {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val b1 = input.read()
            if (b1 < 0) throw IllegalStateException("socket closed before message")
            val b2 = input.read()
            if (b2 < 0) throw IllegalStateException("truncated frame header")
            val fin = (b1 and 0x80) != 0
            val opcode = b1 and 0x0f
            val masked = (b2 and 0x80) != 0
            var length = (b2 and 0x7f).toLong()
            when (length) {
                126L -> length = readExact(input, 2).let {
                    ((it[0].toInt() and 0xff) shl 8 or (it[1].toInt() and 0xff)).toLong()
                }
                127L -> {
                    val ext = readExact(input, 8)
                    length = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                }
            }
            val mask = if (masked) readExact(input, 4) else null
            val data = readExact(input, length.toInt())
            val payload = if (mask != null) {
                ByteArray(data.size) { i -> (data[i].toInt() xor mask[i % 4].toInt()).toByte() }
            } else {
                data
            }
            when (opcode) {
                0x8 -> throw IllegalStateException("server closed WebSocket")
                0x9 -> { /* ping — ignore for one-shot */ }
                0xA -> { /* pong */ }
                0x1, 0x0, 0x2 -> {
                    chunks.add(payload)
                    if (fin) {
                        val all = chunks.fold(ByteArray(0)) { acc, c -> acc + c }
                        return all.toString(Charsets.UTF_8)
                    }
                }
            }
        }
    }

    private fun readExact(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw IllegalStateException("unexpected EOF reading $n bytes")
            off += r
        }
        return buf
    }

    private fun parsePayload(json: String): List<CrowdOccupancy> {
        val arr = JSONArray(json)
        val out = ArrayList<CrowdOccupancy>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val uid = o.optString("uid", "")
            if (uid.isBlank()) continue
            val current = o.optString("currentfill", "0").toDoubleOrNull()
                ?: o.optDouble("currentfill", 0.0)
            val max = o.optDouble("maxspace", 0.0)
            val free = o.optDouble("freespace", 0.0)
            out += CrowdOccupancy.fromRaw(
                uid = uid,
                name = o.optString("name").takeIf { it.isNotBlank() },
                currentFill = current,
                maxSpace = max,
                freeSpace = free,
            )
        }
        return out
    }

    companion object {
        private const val TAG = "CrowdMonitorClient"
        const val WS_HOST = "badi-public.crowdmonitor.ch"
        const val WS_PORT = 9591
        const val WS_PATH = "/api"
        const val DEFAULT_TIMEOUT_MS = 12_000L
        const val SOCKET_READ_TIMEOUT_MS = 10_000
    }
}
