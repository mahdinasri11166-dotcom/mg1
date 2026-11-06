package com.ghostmago.app.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Signal-style X3DH key agreement (simplified but real X25519 DH combination).
 * IK = identity key, SPK = signed prekey, EK = ephemeral
 * SK = KDF( DH(IKa,SPKb) || DH(EKa,IKb) || DH(EKa,SPKb) )
 */
object X3DH {
    data class PreKeyBundle(
        val identityPub: ByteArray,
        val signedPreKeyPub: ByteArray,
        val preKeyId: Int
    )

    data class LocalKeys(
        val identity: E2E.KeyPairBytes,
        val signedPreKey: E2E.KeyPairBytes,
        val preKeyId: Int
    )

    fun generateLocal(): LocalKeys {
        val id = E2E.generateKeyPair()
        val spk = E2E.generateKeyPair()
        val idNum = SecureRandom().nextInt(1_000_000)
        return LocalKeys(id, spk, idNum)
    }

    fun bundle(local: LocalKeys) = PreKeyBundle(
        local.identity.publicKey,
        local.signedPreKey.publicKey,
        local.preKeyId
    )

    /** Alice initiates toward Bob's bundle → shared secret + Alice ephemeral pub to send */
    fun initiate(alice: LocalKeys, bob: PreKeyBundle): Pair<ByteArray, ByteArray> {
        val eph = E2E.generateKeyPair()
        val dh1 = E2E.sharedSecret(alice.identity.privateKey, bob.signedPreKeyPub)
        val dh2 = E2E.sharedSecret(eph.privateKey, bob.identityPub)
        val dh3 = E2E.sharedSecret(eph.privateKey, bob.signedPreKeyPub)
        val sk = kdf(dh1 + dh2 + dh3)
        return sk to eph.publicKey
    }

    /** Bob responds using Alice identity + ephemeral */
    fun respond(
        bob: LocalKeys,
        aliceIdentityPub: ByteArray,
        aliceEphPub: ByteArray
    ): ByteArray {
        val dh1 = E2E.sharedSecret(bob.signedPreKey.privateKey, aliceIdentityPub)
        val dh2 = E2E.sharedSecret(bob.identity.privateKey, aliceEphPub)
        val dh3 = E2E.sharedSecret(bob.signedPreKey.privateKey, aliceEphPub)
        return kdf(dh1 + dh2 + dh3)
    }

    private fun kdf(ikm: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("X3DH-GhostMago".toByteArray() + ikm)
    }
}
