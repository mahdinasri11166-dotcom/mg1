package com.ghostmago.app.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES key in Android Keystore; prefers StrongBox when available.
 */
object HardwareKeystore {
    private const val ALIAS = "ghostmago_hw_aes"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun isStrongBoxAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            } catch (_: Exception) { false }
        } else false
    }

    fun ensureKey(preferStrongBox: Boolean = true): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) return true
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
            if (preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { builder.setIsStrongBoxBacked(true) } catch (_: Exception) {}
            }
            try {
                kg.init(builder.build())
                kg.generateKey()
            } catch (_: Exception) {
                // fallback without StrongBox
                val b2 = KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                kg.init(b2.build())
                kg.generateKey()
            }
            true
        } catch (_: Exception) { false }
    }

    private fun secretKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    /** Encrypt with HW key; returns iv(12) || ciphertext+tag */
    fun encrypt(plain: ByteArray): ByteArray? {
        return try {
            ensureKey()
            val key = secretKey() ?: return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key)
            val iv = c.iv
            val ct = c.doFinal(plain)
            iv + ct
        } catch (_: Exception) { null }
    }

    fun decrypt(data: ByteArray): ByteArray? {
        return try {
            if (data.size < 13) return null
            ensureKey()
            val key = secretKey() ?: return null
            val iv = data.copyOfRange(0, 12)
            val ct = data.copyOfRange(12, data.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.doFinal(ct)
        } catch (_: Exception) { null }
    }

    fun requireOrThrow(context: Context) {
        if (!ensureKey(true)) {
            throw IllegalStateException("Hardware Keystore در دسترس نیست")
        }
    }

    fun status(context: Context): String {
        val ok = ensureKey(true)
        val sb = isStrongBoxAvailable(context)
        return when {
            ok && sb -> "Keystore+StrongBox آماده"
            ok -> "Keystore نرمافزاری/TEE آماده"
            else -> "Keystore در دسترس نیست"
        }
    }
}
