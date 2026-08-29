package com.packatrack.app.data

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

/**
 * Supplies the 256-bit passphrase used to encrypt the SQLCipher database.
 *
 * The random key is generated once and stored wrapped by the Android Keystore (via
 * [KeystoreCrypto]), so it never touches disk in the clear. If the Keystore entry becomes
 * unreadable (e.g. a device Keystore reset) the wrapped key can't be recovered and the
 * encrypted database is unreadable - acceptable here as there is no released data to preserve.
 */
object DatabaseKey {
    private const val PREFS_NAME = "packatrack_db_key"
    private const val KEY = "wrapped_db_key"
    private const val KEY_BYTES = 32

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { wrapped ->
            runCatching { KeystoreCrypto.decryptFromBase64(wrapped) }.getOrNull()?.let { return it }
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString(KEY, KeystoreCrypto.encryptToBase64(key)) }
        return key
    }
}
