package com.tethervault.app.data.vpn

import android.util.Log
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import com.tethervault.app.domain.usecase.AuthenticateDeviceUseCase
import com.tethervault.app.domain.vpn.LocalProxyServer
import com.tethervault.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.io.PushbackInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalProxyServerImpl @Inject constructor(
    private val authenticateDeviceUseCase: AuthenticateDeviceUseCase,
    private val connectedDeviceRepository: ConnectedDeviceRepository
) : LocalProxyServer {

    override val port: Int = Constants.PROXY_PORT

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    override fun start() {
        if (serverJob?.isActive == true) return
        serverJob = serverScope.launch {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                Log.i(TAG, "Proxy server running on port $port")
                while (currentCoroutineContext().isActive) {
                    val client = socket.accept()
                    serverScope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    Log.w(TAG, "Proxy server stopped unexpectedly: ${e.message}")
                }
            } finally {
                runCatching { serverSocket?.close() }
                serverSocket = null
                Log.i(TAG, "Proxy server stopped")
            }
        }
    }

    override fun stop() {
        serverJob?.cancel()
        serverJob = null
        // Closing the socket unblocks a pending accept.
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val pushbackInput =
                PushbackInputStream(client.getInputStream(), SOCKS5_GREETING_SIZE)
            val rawOutput = client.getOutputStream()

            performSocks5Handshake(pushbackInput, rawOutput)

            val reader = BufferedReader(InputStreamReader(pushbackInput))
            val writer = PrintWriter(rawOutput, false)

            val requestLine = reader.readLine() ?: return
            val clientIp = client.inetAddress.hostAddress ?: "unknown"

            val requestParts = requestLine.split(" ")
            val method = requestParts.getOrNull(0).orEmpty()
            val path = requestParts.getOrNull(1).orEmpty()

            val contentLength = readHeaders(reader)

            val device = connectedDeviceRepository.findByIpAddress(clientIp)
            val isAuthenticated = device?.isAuthenticated == true

            when {
                isAuthenticated -> writeOk(
                    writer = writer,
                    contentType = "text/plain",
                    body = "Internet Access Granted (Placeholder for actual proxy forwarding)"
                )

                method == "POST" && path == "/login" -> {
                    val body = readBody(reader, contentLength)
                    val voucherCode = parseFormValue(body, "voucher")
                    val authenticated = voucherCode != null &&
                        authenticateDeviceUseCase(
                            ipAddress = clientIp,
                            voucherCode = voucherCode
                        ).isSuccess
                    if (authenticated) {
                        writeRedirect(writer, "/success")
                    } else {
                        writeRedirect(writer, "/?error=1")
                    }
                }

                method == "GET" && path == "/success" ->
                    writeOk(writer, "text/html", SUCCESS_HTML)

                else -> writeOk(writer, "text/html", LOGIN_HTML)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client handling failed: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    // hev-socks5-tunnel forwards TUN traffic as SOCKS5 connections, so the
    // handshake must complete before any HTTP payload can be read. Plain
    // HTTP clients (no SOCKS5 byte) get their bytes pushed back and fall
    // through to the captive portal handling.
    private fun performSocks5Handshake(input: PushbackInputStream, output: OutputStream) {
        val greeting = ByteArray(SOCKS5_GREETING_SIZE)
        val greetingLength = readFully(input, greeting)
        if (greetingLength < 0 || greeting[0] != SOCKS5_VERSION) {
            input.unread(greeting, 0, maxOf(greetingLength, 0))
            return
        }
        // Method selection reply: no authentication required.
        output.write(byteArrayOf(SOCKS5_VERSION, NO_AUTH_REQUIRED))
        output.flush()

        // SOCKS5 request (VER CMD RSV ATYP ADDR PORT) — 10 bytes for IPv4.
        val request = ByteArray(SOCKS5_REQUEST_SIZE)
        readFully(input, request)

        // Reply: succeeded, bound address 0.0.0.0:0.
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count < 0) {
                if (read == 0) return -1
                throw IOException("Connection closed mid-read")
            }
            read += count
        }
        return read
    }

    private fun readHeaders(reader: BufferedReader): Int {
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        return contentLength
    }

    private fun readBody(reader: BufferedReader, contentLength: Int): String {
        if (contentLength <= 0) return ""
        val chars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = reader.read(chars, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        return String(chars, 0, read)
    }

    private fun parseFormValue(body: String, key: String): String? {
        val raw = body.split('&').firstNotNullOfOrNull { pair ->
            val index = pair.indexOf('=')
            if (index > 0 && pair.substring(0, index) == key) pair.substring(index + 1) else null
        } ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun writeOk(writer: PrintWriter, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: $contentType; charset=utf-8\r\n")
        writer.print("Content-Length: ${bodyBytes.size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun writeRedirect(writer: PrintWriter, location: String) {
        writer.print("HTTP/1.1 302 Found\r\n")
        writer.print("Location: $location\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
    }

    private companion object {
        const val TAG = "TetherVaultProxy"
        const val SOCKET_TIMEOUT_MS = 3000
        const val SOCKS5_VERSION: Byte = 0x05
        const val NO_AUTH_REQUIRED: Byte = 0x00
        const val SOCKS5_GREETING_SIZE = 3
        const val SOCKS5_REQUEST_SIZE = 10
        const val LOGIN_HTML =
            "<html><body><h1>TetherVault Login</h1>" +
                "<form method='POST' action='/login'>" +
                "<input type='text' name='voucher' placeholder='Voucher Code' required/>" +
                "<button type='submit'>Connect</button>" +
                "</form></body></html>"
        const val SUCCESS_HTML =
            "<html><body><h1>Connected!</h1>" +
                "<p>You can now use the internet.</p></body></html>"
    }
}
