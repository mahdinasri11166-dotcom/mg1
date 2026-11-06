package com.ghostmago.app.crypto

/**
 * JNI bridge — sensitive pepper/hint live in native .so (harder for pure-Java decompilers).
 */
object NativeBridge {
    @Volatile private var loaded = false

    init {
        try {
            System.loadLibrary("ghostmago_native")
            loaded = true
        } catch (_: UnsatisfiedLinkError) {
            loaded = false
        }
    }

    fun isAvailable(): Boolean = loaded

    external fun nativePepper(): String
    external fun nativeIntegrityHint(): Boolean

    /** Pepper for KDF mixing; falls back if NDK missing */
    fun pepper(): String = try {
        if (loaded) nativePepper() else "GhostMago|fallback|soft"
    } catch (_: Exception) {
        "GhostMago|fallback|soft"
    }
}
