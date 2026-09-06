# Tun2Socks JNI wrapper: native code looks up these methods by their exact
# names, so they must not be renamed or stripped by R8.
-keep class com.tethervault.app.data.vpn.jni.Tun2SocksJniWrapper { *; }

# Room and Hilt ship their own consumer ProGuard rules, so entities and
# generated implementations survive obfuscation without extra keeps.
