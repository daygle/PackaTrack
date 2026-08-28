package com.packatrack.app.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based AES-GCM envelope for portable backups.
 *
 * The on-disk layout is `MAGIC | version | salt | iv | ciphertext(+GCM tag)`. The key is
 * derived from the passphrase with PBKDF2-HMAC-SHA256. This object is deliberately free of
 * Android and Room dependencies so the crypto can be exercised by plain JVM unit tests.
 */
object BackupCodec {
    const val VERSION = 2
    const val MIN_PASSPHRASE_LENGTH = 12

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2 = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG_LENGTH = 16
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    val MAGIC: ByteArray = "PKTB".toByteArray(Charsets.US_ASCII)

    /** Encrypts [plaintext] and returns the complete backup-file bytes. */
    fun seal(plaintext: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
        }
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return MAGIC + byteArrayOf(VERSION.toByte()) + salt + iv + cipher.doFinal(plaintext)
    }

    /**
     * Validates the header and decrypts a backup file. Throws [IllegalArgumentException] for a
     * malformed or unsupported file, or a crypto exception (e.g. bad GCM tag) when the passphrase
     * is wrong or the ciphertext was tampered with.
     */
    fun open(fileBytes: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
        }
        val headerSize = MAGIC.size + 1 + SALT_LENGTH + IV_LENGTH
        require(fileBytes.size > headerSize + TAG_LENGTH) { "Backup file is incomplete" }
        require(fileBytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a PackaTrack backup" }
        require(fileBytes[MAGIC.size].toInt() == VERSION) { "Unsupported backup version" }
        val saltStart = MAGIC.size + 1
        val salt = fileBytes.copyOfRange(saltStart, saltStart + SALT_LENGTH)
        val ivStart = saltStart + SALT_LENGTH
        val iv = fileBytes.copyOfRange(ivStart, ivStart + IV_LENGTH)
        val ciphertext = fileBytes.copyOfRange(ivStart + IV_LENGTH, fileBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
