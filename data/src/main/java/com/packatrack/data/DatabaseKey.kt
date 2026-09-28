package com.packatrack.data

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Supplies the 256-bit passphrase used to encrypt the SQLCipher database.
 *
 * The random key is generated once and stored wrapped by the Android Keystore (via
 * [KeystoreCrypto]), so it never touches disk in the clear.
 *
 * If the wrapped key can definitively no longer be unwrapped (the Keystore entry was reset, so
 * the GCM tag no longer verifies, or the stored blob is malformed) the encrypted database can
 * never be opened again: [onKeyLost] is invoked so the caller can discard it, and a new key is
 * generated. Any other failure (e.g. a transient Keystore error) is rethrown *without*
 * replacing the stored key, so a later launch can still open the existing database.
 */
object DatabaseKey {
    private const val PREFS_NAME = "packatrack_db_key"
    private const val KEY = "wrapped_db_key"
    private const val KEY_BYTES = 32

    fun getOrCreate(context: Context, onKeyLost: () -> Unit = {}): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { wrapped ->
            try {
                return KeystoreCrypto.decryptFromBase64(wrapped)
            } catch (_: AEADBadTagException) {
                onKeyLost()
            } catch (_: IllegalArgumentException) {
                onKeyLost()
            }
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        // commit() rather than apply(): the key must be durable before anything is encrypted with it.
        prefs.edit(commit = true) { putString(KEY, KeystoreCrypto.encryptToBase64(key)) }
        return key
    }
}
