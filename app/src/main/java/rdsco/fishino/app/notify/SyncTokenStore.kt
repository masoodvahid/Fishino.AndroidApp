package rdsco.fishino.app.notify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.notifyDataStore: DataStore<Preferences> by preferencesDataStore(name = "notify")

/** What the background check needs: where to ask and with which token. */
data class SyncConfig(val apiBaseUrl: String, val token: String, val intervalSeconds: Long)

/**
 * The long-lived background token (it can only fetch message titles),
 * encrypted with a Keystore key so the stored bytes are useless off the
 * phone. Unlike the fingerprint key, this one needs no user check: the
 * check runs while nobody is looking at the phone.
 */
class SyncTokenStore(context: Context) {

    private val dataStore = context.applicationContext.notifyDataStore

    suspend fun save(apiBaseUrl: String, token: String, intervalSeconds: Long) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        dataStore.edit {
            it[API_BASE_URL] = apiBaseUrl
            it[IV] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            it[TOKEN] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            it[INTERVAL] = intervalSeconds
        }
    }

    suspend fun load(): SyncConfig? = runCatching {
        val prefs = dataStore.data.first()
        val apiBaseUrl = prefs[API_BASE_URL] ?: return null
        val iv = Base64.decode(prefs[IV] ?: return null, Base64.NO_WRAP)
        val ciphertext = Base64.decode(prefs[TOKEN] ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }

        SyncConfig(apiBaseUrl, String(cipher.doFinal(ciphertext), Charsets.UTF_8), prefs[INTERVAL] ?: DEFAULT_INTERVAL_SECONDS)
    }.getOrNull()

    suspend fun setInterval(seconds: Long) {
        dataStore.edit { it[INTERVAL] = seconds }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 6 * 3600L
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "fishino_sync_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val IV = stringPreferencesKey("iv")
        private val TOKEN = stringPreferencesKey("token")
        private val INTERVAL = longPreferencesKey("interval_seconds")
    }
}
