package com.tethervault.app.data.vpn

import android.util.Log
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import com.tethervault.app.domain.usecase.AuthenticateDeviceUseCase
import com.tethervault.app.domain.vpn.LocalProxyServer
import com.tethervault.app.util.Constants
import com.tethervault.app.util.P2pAddressResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
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

    private data class Socks5Request(
        val command: Byte,
        val destinationAddress: String,
        val destinationPort: Int
    )

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
        var remote: Socket? = null
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val pushbackInput =
                PushbackInputStream(client.getInputStream(), SOCKS5_GREETING_SIZE)
            val rawOutput = client.getOutputStream()

            val greeting = ByteArray(SOCKS5_GREETING_SIZE)
            val greetingLength = readFully(pushbackInput, greeting)
            if (greetingLength < 0 || greeting[0] != SOCKS5_VERSION) {
                // Plain HTTP client (no SOCKS5): serve the captive portal.
                pushbackInput.unread(greeting, 0, maxOf(greetingLength, 0))
                serveCaptivePortal(pushbackInput, rawOutput, client)
                return
            }

            // Method selection reply: no authentication required.
            rawOutput.write(byteArrayOf(SOCKS5_VERSION, NO_AUTH_REQUIRED))
            rawOutput.flush()

            val request = parseSocks5Request(pushbackInput)

            if (request == null) {
                writeSocks5Reply(rawOutput, REPLY_GENERAL_FAILURE)
                return
            }

            val clientIp = client.inetAddress.hostAddress ?: "unknown"
            val device = connectedDeviceRepository.findByIpAddress(clientIp)
            val isAuthenticated = device?.isAuthenticated == true

            if (!isAuthenticated) {
                // Pretend the tunnel is up so the SOCKS5 client sends its
                // HTTP payload, which the captive portal then answers.
                writeSocks5Reply(rawOutput, REPLY_SUCCEEDED)
                serveCaptivePortal(pushbackInput, rawOutput, client)
                return
            }

            if (request.command != SOCKS5_CMD_CONNECT) {
                writeSocks5Reply(rawOutput, REPLY_COMMAND_NOT_SUPPORTED)
                return
            }

            val remoteSocket = try {
                Socket().apply {
                    connect(
                        InetSocketAddress(request.destinationAddress, request.destinationPort),
                        REMOTE_CONNECT_TIMEOUT_MS
                    )
                }
            } catch (e: IOException) {
                Log.w(
                    TAG,
                    "Failed to connect to ${request.destinationAddress}:${request.destinationPort}: ${e.message}"
                )
                writeSocks5Reply(rawOutput, REPLY_GENERAL_FAILURE)
                return
            }
            remote = remoteSocket

            writeSocks5Reply(rawOutput, REPLY_SUCCEEDED)

            // Long-lived tunnel: the handshake read timeout would kill
            // idle connections, so disable it before piping.
            client.soTimeout = 0

            pipeBidirectionally(pushbackInput, rawOutput, remoteSocket)
        } catch (e: Exception) {
            Log.w(TAG, "Client handling failed: ${e.message}")
        } finally {
            runCatching { remote?.close() }
            runCatching { client.close() }
        }
    }

    private suspend fun pipeBidirectionally(
        clientInput: InputStream,
        clientOutput: OutputStream,
        remote: Socket
    ) = coroutineScope {
        val uploads = launch { clientInput.copyTo(remote.getOutputStream()) }
        val downloads = launch { remote.getInputStream().copyTo(clientOutput) }
        joinAll(uploads, downloads)
    }

    // SOCKS5 connection request: VER(1) CMD(1) RSV(1) ATYP(1)
    // DST.ADDR(var) DST.PORT(2).
    private fun parseSocks5Request(input: InputStream): Socks5Request? {
        val header = ByteArray(SOCKS5_REQUEST_HEADER_SIZE)
        if (readFully(input, header) < 0) return null
        if (header[0] != SOCKS5_VERSION) return null
        val command = header[1]
        val addressType = header[3]

        val address: String = when (addressType) {
            ADDRESS_TYPE_IPV4 -> {
                val bytes = ByteArray(4)
                if (readFully(input, bytes) < 0) return null
                bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }

            ADDRESS_TYPE_DOMAIN -> {
                val lengthBytes = ByteArray(1)
                if (readFully(input, lengthBytes) < 0) return null
                val length = lengthBytes[0].toInt() and 0xFF
                if (length == 0) return null
                val bytes = ByteArray(length)
                if (readFully(input, bytes) < 0) return null
                String(bytes, Charsets.US_ASCII)
            }

            ADDRESS_TYPE_IPV6 -> {
                val bytes = ByteArray(16)
                if (readFully(input, bytes) < 0) return null
                InetAddress.getByAddress(bytes).hostAddress ?: return null
            }

            else -> return null
        }

        val portBytes = ByteArray(2)
        if (readFully(input, portBytes) < 0) return null
        val port =
            ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

        return Socks5Request(command, address, port)
    }

    private fun writeSocks5Reply(output: OutputStream, replyCode: Byte) {
        output.write(
            byteArrayOf(
                SOCKS5_VERSION, replyCode, 0x00, ADDRESS_TYPE_IPV4, 0, 0, 0, 0, 0, 0
            )
        )
        output.flush()
    }

    private suspend fun serveCaptivePortal(
        input: InputStream,
        output: OutputStream,
        client: Socket
    ) {
        val reader = BufferedReader(InputStreamReader(input))
        val writer = PrintWriter(output, false)

        val requestLine = reader.readLine() ?: return
        val clientIp = client.inetAddress.hostAddress ?: "unknown"

        val requestParts = requestLine.split(" ")
        val method = requestParts.getOrNull(0).orEmpty()
        val path = requestParts.getOrNull(1).orEmpty()

        val contentLength = readHeaders(reader)

        val device = connectedDeviceRepository.findByIpAddress(clientIp)
        val isAuthenticated = device?.isAuthenticated == true

        when {
            isAuthenticated -> writeOk(writer, "text/html", SUCCESS_HTML)

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

            method == "GET" && path == "/portal" ->
                writeOk(writer, "text/html", buildLoginHtml())

            else -> {
                // Captive-portal probe (any other unauthenticated request):
                // redirect to the voucher login page on the group owner so
                // the OS "Sign in to network" / "Login to network" flow
                // opens the form directly in its captive portal mini-browser.
                writeRedirect(writer, "${portalBaseUrl()}/portal")
            }
        }
    }

    // The portal must be reachable directly (not only through the tunnel):
    // the group owner's own address is always on the same subnet as the
    // connected clients.
    private fun portalBaseUrl(): String {
        val groupOwnerAddress =
            P2pAddressResolver.getGroupOwnerAddress() ?: P2pAddressResolver.FALLBACK_GROUP_OWNER_IP
        return "http://$groupOwnerAddress:$port"
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
        writer.print("Cache-Control: no-store\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun writeRedirect(writer: PrintWriter, location: String) {
        writer.print("HTTP/1.1 302 Found\r\n")
        writer.print("Location: $location\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
    }

    // The login form posts to an absolute URL: captive-portal mini-browsers
    // may resolve relative paths against the original probe host instead
    // of the redirected portal host.
    private fun buildLoginHtml(): String =
        "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>" +
            "<title>TetherVault Login</title></head>" +
            "<body style='font-family:sans-serif;text-align:center;padding:40px;'>" +
            "<h1>TetherVault</h1>" +
            "<p>Enter your voucher code to access the internet.</p>" +
            "<form method='POST' action='${portalBaseUrl()}/login'>" +
            "<input type='text' name='voucher' placeholder='Voucher Code' required " +
            "style='font-size:18px;padding:12px;text-align:center;width:80%;'/>" +
            "<br/><br/>" +
            "<button type='submit' style='font-size:18px;padding:12px 32px;'>Connect</button>" +
            "</form></body></html>"

    private companion object {
        const val TAG = "TetherVaultProxy"
        const val SOCKET_TIMEOUT_MS = 3000
        const val REMOTE_CONNECT_TIMEOUT_MS = 10_000
        const val SOCKS5_VERSION: Byte = 0x05
        const val NO_AUTH_REQUIRED: Byte = 0x00
        const val SOCKS5_CMD_CONNECT: Byte = 0x01
        const val ADDRESS_TYPE_IPV4: Byte = 0x01
        const val ADDRESS_TYPE_DOMAIN: Byte = 0x03
        const val ADDRESS_TYPE_IPV6: Byte = 0x04
        const val REPLY_SUCCEEDED: Byte = 0x00
        const val REPLY_GENERAL_FAILURE: Byte = 0x01
        const val REPLY_COMMAND_NOT_SUPPORTED: Byte = 0x07
        const val SOCKS5_GREETING_SIZE = 3
        const val SOCKS5_REQUEST_HEADER_SIZE = 4
        const val SUCCESS_HTML =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>" +
                "<title>Connected</title></head>" +
                "<body style='font-family:sans-serif;text-align:center;padding:40px;'>" +
                "<h1>Connected!</h1>" +
                "<p>You can now use the internet.</p></body></html>"
    }
}
