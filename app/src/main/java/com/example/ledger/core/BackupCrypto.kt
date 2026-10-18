package com.example.ledger.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file: "WMB1" + salt (16) + nonce (12) + AES-256-GCM(gzip(content)), with the key made
 * from the person's password (PBKDF2-HMAC-SHA256). Without the password the file cannot be read,
 * and any change to it is detected.
 */
object BackupCrypto {
    private val MAGIC = "WMB1".toByteArray()
    private const val ITERATIONS = 150_000

    class WrongPasswordOrFile : Exception("Wrong password, or this is not a Wealth Manager backup")

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(content: ByteArray, password: CharArray): ByteArray {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(content) } }.toByteArray()
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return MAGIC + salt + nonce + cipher.doFinal(zipped)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < 4 + 16 + 12 + 16 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) throw WrongPasswordOrFile()
        val salt = file.copyOfRange(4, 20)
        val nonce = file.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        val zipped = try {
            cipher.doFinal(file, 32, file.size - 32)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordOrFile()
        }
        return GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }
    }
}
