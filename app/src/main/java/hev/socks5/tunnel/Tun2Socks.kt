package hev.socks5.tunnel

// Method names, signatures, and return types mirror the JNINativeMethod
// table registered by hev-jni.c in JNI_OnLoad: the library registers all
// four methods on this class, so every one must be declared with an exact
// match or RegisterNatives (and therefore loadLibrary) fails.
object Tun2Socks {
    init { try { System.loadLibrary("hev-socks5-tunnel") } catch (e: Exception) { } }
    external fun TProxyStartService(configPath: String, fd: Int): Boolean
    external fun TProxyStopService(): Boolean
    external fun TProxyIsRunning(): Boolean
    external fun TProxyGetStats(): LongArray?
}
