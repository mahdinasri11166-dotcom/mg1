package com.ghostmago.app.security

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

object SecureWipe {
    private val rnd = SecureRandom()

    /** Multi-pass overwrite then delete. Best-effort on flash storage. */
    fun wipe(file: File, passes: Int = 3): Boolean {
        if (!file.exists() || !file.isFile) return false
        val len = file.length()
        if (len <= 0) return file.delete()
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                val buf = ByteArray(8192)
                repeat(passes.coerceIn(1, 7)) {
                    raf.seek(0)
                    var left = len
                    while (left > 0) {
                        val n = minOf(buf.size.toLong(), left).toInt()
                        rnd.nextBytes(buf)
                        raf.write(buf, 0, n)
                        left -= n
                    }
                    raf.fd.sync()
                }
            }
            file.delete()
        } catch (_: Exception) {
            try {
                file.delete()
            } catch (_: Exception) {
                false
            }
        }
    }
}
