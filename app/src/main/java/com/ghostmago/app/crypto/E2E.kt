package com.ghostmago.app.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * X25519-style ECDH via BC + AES-GCM for private 1:1 messages
 */
object E2E {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(BouncyCastleProvider())
    }

    data class KeyPairBytes(val publicKey: ByteArray, val privateKey: ByteArray)

    fun generateKeyPair(): KeyPairBytes {
        val kpg = KeyPairGenerator.getInstance("X25519", BouncyCastleProvider.PROVIDER_NAME)
        val kp = kpg.generateKeyPair()
        return KeyPairBytes(kp.public.encoded, kp.private.encoded)
    }

    fun sharedSecret(myPrivate: ByteArray, peerPublic: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("X25519", BouncyCastleProvider.PROVIDER_NAME)
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(myPrivate))
        val pub = kf.generatePublic(X509EncodedKeySpec(peerPublic))
        val ka = KeyAgreement.getInstance("X25519", BouncyCastleProvider.PROVIDER_NAME)
        ka.init(priv)
        ka.doPhase(pub, true)
        val shared = ka.generateSecret()
        // Derive 32-byte key via SHA-256
        return java.security.MessageDigest.getInstance("SHA-256").digest(shared)
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct)
    }
}
