package hev.socks5.tunnel

object Tun2Socks {
    init { try { System.loadLibrary("hev-socks5-tunnel") } catch (e: Exception) { } }
    external fun Start(fd: Int, mtu: Int, ipv4: String, ipv6: String, socks5Addr: String, socks5Port: Int)
    external fun Stop()
}
