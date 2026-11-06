# ═══════════════════════════════════════════════════════════
# مشکات (Mishkat) — ProGuard / R8 rules for release
# ═══════════════════════════════════════════════════════════

# ── Strip verbose logs (keep w/e for rare diagnostics) ──
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── BouncyCastle (Argon2id, providers) ──
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }

# ── Google Tink / AndroidX Security Crypto (if present) ──
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }

# ── JNI / NDK ──
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.ghostmago.app.crypto.NativeBridge {
    *;
}
-keepclassmembers class com.ghostmago.app.crypto.NativeBridge {
    native <methods>;
}

# ── Core app entry points (must not be renamed) ──
-keep class com.ghostmago.app.MainActivity { *; }
-keep class com.ghostmago.app.GhostMagoApp { *; }
-keep class com.ghostmago.app.data.AppState { *; }
-keep class com.ghostmago.app.data.AppState$* { *; }

# ── Manifest / components ──
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Application { *; }

# ── Parcelable ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Kotlin / Coroutines ──
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── Jetpack Compose ──
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ── ZXing / barcode ──
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# ── CameraX ──
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }

# ── WebRTC (if linked) ──
-dontwarn org.webrtc.**
-keep class org.webrtc.** { *; }

# ── Serialization / JSON (org.json is platform) ──
-dontwarn org.json.**

# Keep line numbers for crash reports (optional, helps debugging release)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
