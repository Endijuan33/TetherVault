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
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
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
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), false)

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
