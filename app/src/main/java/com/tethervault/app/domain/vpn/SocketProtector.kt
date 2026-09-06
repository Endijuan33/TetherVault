package com.tethervault.app.domain.vpn

interface SocketProtector {

    fun protect(socketFd: Int): Boolean
}
