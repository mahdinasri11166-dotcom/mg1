package com.ghostmago.app.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Arrays

/**
 * هسته رمزنگاری ترکیبی از Ghost SMS و MGO
 * AES-256-GCM + Argon2id + HMAC
 */
object CryptoEngine {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val random = SecureRandom()
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_LEN = 128 // bits
    private const val KEY_LEN = 32

    fun deriveKey(password: CharArray, salt: ByteArray, memoryKb: Int = 65536, iterations: Int = 3): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(2)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(KEY_LEN)
        val mixed = (String(password) + "|" + NativeBridge.pepper()).toByteArray(Charsets.UTF_8)
        gen.generateBytes(mixed, out)
        Arrays.fill(password, '\u0000')
        return out
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val out = encryptWithKey(plaintext, key, salt)
        Arrays.fill(key, 0)
        return out
    }

    /** Fast path for mesh: AES-GCM only (no Argon2). Key must be 32 bytes. */
    fun encryptWithKey(plaintext: ByteArray, key: ByteArray, salt: ByteArray = ByteArray(SALT_LEN)): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN, nonce))
        val ct = cipher.doFinal(plaintext)
        return salt + nonce + ct
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size > SALT_LEN + NONCE_LEN + 16) { "داده نامعتبر" }
        val salt = data.copyOfRange(0, SALT_LEN)
        val nonce = data.copyOfRange(SALT_LEN, SALT_LEN + NONCE_LEN)
        val ct = data.copyOfRange(SALT_LEN + NONCE_LEN, data.size)
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN, nonce))
            return cipher.doFinal(ct)
        } finally {
            Arrays.fill(key, 0)
        }
    }

    fun decryptWithKey(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size > SALT_LEN + NONCE_LEN + 16) { "داده نامعتبر" }
        val nonce = data.copyOfRange(SALT_LEN, SALT_LEN + NONCE_LEN)
        val ct = data.copyOfRange(SALT_LEN + NONCE_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN, nonce))
        return cipher.doFinal(ct)
    }

    /**
     * One-time mesh session key: Argon2id (lighter params) → 32-byte AES key.
     * Cached by MeshNetwork so packets only use AES-256-GCM (no per-message Argon2).
     */
    fun meshSessionKey(password: String, groupId: String): ByteArray {
        val salt = sha256(("MISHKAT-SALT|" + groupId + "|" + NativeBridge.pepper()).toByteArray()).copyOf(SALT_LEN)
        // 16MB / 2 iter — strong enough, once per join (not per packet)
        return deriveKey(password.toCharArray(), salt, memoryKb = 16_384, iterations = 2)
    }

    fun encryptString(text: String, password: String): String {
        val enc = encrypt(text.toByteArray(Charsets.UTF_8), password.toCharArray())
        return android.util.Base64.encodeToString(enc, android.util.Base64.NO_WRAP)
    }

    fun decryptString(b64: String, password: String): String {
        val data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        return String(decrypt(data, password.toCharArray()), Charsets.UTF_8)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun secureRandomBytes(len: Int): ByteArray = ByteArray(len).also { random.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    fun wipe(arr: ByteArray) = Arrays.fill(arr, 0)
}
