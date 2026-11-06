package com.ghostmago.app.security

import com.ghostmago.app.crypto.CryptoEngine
import java.io.File

object FileCrypto {
    private val MAGIC = "GMFILE1".toByteArray(Charsets.US_ASCII)

    fun encryptFile(input: File, output: File, password: String) {
        val plain = input.readBytes()
        val enc = CryptoEngine.encrypt(plain, password.toCharArray())
        output.writeBytes(MAGIC + enc)
    }

    fun decryptFile(input: File, output: File, password: String) {
        val all = input.readBytes()
        require(all.size > MAGIC.size) { "فایل نامعتبر" }
        require(all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "این فایل رمزنگاری مشکات نیست" }
        val enc = all.copyOfRange(MAGIC.size, all.size)
        val plain = CryptoEngine.decrypt(enc, password.toCharArray())
        output.writeBytes(plain)
    }
}
