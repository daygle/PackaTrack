package com.packatrack.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val passphrase get() = "correct horse battery".toCharArray()

    @Test
    fun sealThenOpenRoundTripsThePlaintext() {
        val plaintext = """{"hello":"world","n":42}""".toByteArray()
        val sealed = BackupCodec.seal(plaintext, passphrase)
        val opened = BackupCodec.open(sealed, passphrase)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun sealedOutputStartsWithMagicAndVersionAndDiffersFromPlaintext() {
        val plaintext = "sensitive".toByteArray()
        val sealed = BackupCodec.seal(plaintext, passphrase)
        assertArrayEquals(BackupCodec.MAGIC, sealed.copyOfRange(0, BackupCodec.MAGIC.size))
        assertTrue(sealed[BackupCodec.MAGIC.size].toInt() == BackupCodec.VERSION)
        // Ciphertext must not contain the plaintext bytes verbatim.
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("sensitive"))
    }

    @Test
    fun twoSealsOfSameInputDifferDueToRandomSaltAndIv() {
        val plaintext = "repeat".toByteArray()
        val a = BackupCodec.seal(plaintext, passphrase)
        val b = BackupCodec.seal(plaintext, passphrase)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun openWithWrongPassphraseFails() {
        val sealed = BackupCodec.seal("data".toByteArray(), passphrase)
        assertThrows(Exception::class.java) {
            BackupCodec.open(sealed, "wrong passphrase!".toCharArray())
        }
    }

    @Test
    fun openRejectsTamperedCiphertext() {
        val sealed = BackupCodec.seal("data".toByteArray(), passphrase)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertThrows(Exception::class.java) { BackupCodec.open(sealed, passphrase) }
    }

    @Test
    fun openRejectsForeignMagic() {
        val sealed = BackupCodec.seal("data".toByteArray(), passphrase)
        sealed[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.open(sealed, passphrase) }
    }

    @Test
    fun shortPassphraseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.seal("data".toByteArray(), "short".toCharArray())
        }
    }
}
