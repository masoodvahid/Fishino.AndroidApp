package rdsco.fishino.app.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(name = "credential")

/** The password saved for fingerprint sign-in, encrypted; for one employer and one username. */
class SavedCredential(
    val apiBaseUrl: String,
    val username: String,
    internal val iv: ByteArray,
    internal val ciphertext: ByteArray,
)

/** What the screens need to know, without the encrypted password. */
data class VaultStatus(val apiBaseUrl: String?, val username: String?, val declined: Set<String>) {
    fun savedFor(apiBaseUrl: String): String? = username.takeIf { this.apiBaseUrl == apiBaseUrl }

    fun isDeclined(apiBaseUrl: String, username: String): Boolean = declinedKey(apiBaseUrl, username) in declined
}

/** The phone's fingerprints changed since the password was saved: it can no longer be read. */
class CredentialInvalidatedException : Exception()

/**
 * Fingerprint sign-in: the password is encrypted with an AES key in the
 * Android Keystore (hardware-backed where the phone has it) that can only be
 * used right after a strong biometric check, and that Android destroys when
 * a fingerprint is added or removed. Without the user's fingerprint the
 * stored bytes are useless — also to a backup (backups are off) or another
 * app. Only one account is kept: the last one the user enabled it for.
 */
class CredentialVault(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.credentialDataStore

    val status: Flow<VaultStatus> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val complete = prefs[CIPHERTEXT] != null && prefs[IV] != null
            VaultStatus(
                apiBaseUrl = prefs[API_BASE_URL]?.takeIf { complete },
                username = prefs[USERNAME]?.takeIf { complete },
                declined = prefs[DECLINED].orEmpty(),
            )
        }

    /** A strong biometric (fingerprint, or face where Android rates it strong) is set up. */
    fun isAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun saved(apiBaseUrl: String): SavedCredential? {
        val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return null
        if (prefs[API_BASE_URL] != apiBaseUrl) return null

        return runCatching {
            SavedCredential(
                apiBaseUrl = apiBaseUrl,
                username = prefs[USERNAME] ?: return null,
                iv = Base64.decode(prefs[IV] ?: return null, Base64.NO_WRAP),
                ciphertext = Base64.decode(prefs[CIPHERTEXT] ?: return null, Base64.NO_WRAP),
            )
        }.getOrNull()
    }

    /** Whether to offer fingerprint sign-in after this password login. */
    suspend fun shouldOffer(apiBaseUrl: String, username: String): Boolean {
        if (!isAvailable()) return false
        val status = status.first()
        return status.savedFor(apiBaseUrl) != username && !status.isDeclined(apiBaseUrl, username)
    }

    /** A cipher to hand to the biometric prompt before [save]. */
    fun encryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        }
        return cipher
    }

    /**
     * A cipher to hand to the biometric prompt before [decrypt].
     *
     * @throws CredentialInvalidatedException the key is gone; the saved password is deleted.
     */
    suspend fun decryptionCipher(saved: SavedCredential): Cipher {
        val key = runCatching { key(create = false) }.getOrNull()
        if (key == null) {
            clear()
            throw CredentialInvalidatedException()
        }

        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, saved.iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            throw CredentialInvalidatedException()
        }
    }

    /** [cipher] must have just been unlocked by the biometric prompt. */
    suspend fun save(apiBaseUrl: String, username: String, password: String, cipher: Cipher) {
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

        dataStore.edit {
            it[API_BASE_URL] = apiBaseUrl
            it[USERNAME] = username
            it[IV] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            it[CIPHERTEXT] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            it[DECLINED] = it[DECLINED].orEmpty() - declinedKey(apiBaseUrl, username)
        }
    }

    /** [cipher] must have just been unlocked by the biometric prompt. */
    fun decrypt(saved: SavedCredential, cipher: Cipher): String =
        String(cipher.doFinal(saved.ciphertext), Charsets.UTF_8)

    /** The user said "not now", or turned it off: do not offer it again for this account. */
    suspend fun setDeclined(apiBaseUrl: String, username: String, declined: Boolean) {
        val key = declinedKey(apiBaseUrl, username)
        dataStore.edit { it[DECLINED] = if (declined) it[DECLINED].orEmpty() + key else it[DECLINED].orEmpty() - key }
    }

    /** Forget the saved password (and the key, so nothing can decrypt an old copy). */
    suspend fun clear() {
        dataStore.edit {
            it.remove(API_BASE_URL)
            it.remove(USERNAME)
            it.remove(IV)
            it.remove(CIPHERTEXT)
        }
        deleteKey()
    }

    /** A different employer: nothing of the old one stays. */
    suspend fun clearAll() {
        clear()
        dataStore.edit { it.remove(DECLINED) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(create: Boolean): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "no key" }

        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Every use needs a fresh strong biometric check.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fishino_login_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128

        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val USERNAME = stringPreferencesKey("username")
        val IV = stringPreferencesKey("iv")
        val CIPHERTEXT = stringPreferencesKey("ciphertext")
        val DECLINED = stringSetPreferencesKey("declined")
    }
}

private fun declinedKey(apiBaseUrl: String, username: String): String = apiBaseUrl + "\n" + username.lowercase()
