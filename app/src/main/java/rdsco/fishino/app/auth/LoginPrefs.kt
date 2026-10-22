package rdsco.fishino.app.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.loginDataStore: DataStore<Preferences> by preferencesDataStore(name = "login")

/**
 * Non-secret conveniences of the login screen: the method and username or
 * number used last time, and a random id of this installation (codes sent by
 * SMS are bound to it). Passwords and tokens are never stored.
 */
class LoginPrefs(context: Context) {

    private val dataStore = context.applicationContext.loginDataStore

    data class Remembered(val method: LoginMethod?, val username: String, val mobile: String)

    suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }

        val created = UUID.randomUUID().toString()
        dataStore.edit { it[DEVICE_ID] = it[DEVICE_ID] ?: created }
        return dataStore.data.first()[DEVICE_ID] ?: created
    }

    suspend fun remembered(): Remembered {
        val prefs = runCatching { dataStore.data.first() }.getOrNull()

        return Remembered(
            method = prefs?.get(METHOD)?.let { name -> LoginMethod.entries.firstOrNull { it.name == name } },
            username = prefs?.get(USERNAME).orEmpty(),
            mobile = prefs?.get(MOBILE).orEmpty(),
        )
    }

    suspend fun rememberPassword(username: String) {
        dataStore.edit {
            it[METHOD] = LoginMethod.Password.name
            it[USERNAME] = username
        }
    }

    suspend fun rememberOtp(mobile: String) {
        dataStore.edit {
            it[METHOD] = LoginMethod.Otp.name
            it[MOBILE] = mobile
        }
    }

    /** A new employer: their usernames mean nothing here. */
    suspend fun forgetAccount() {
        dataStore.edit {
            it.remove(METHOD)
            it.remove(USERNAME)
            it.remove(MOBILE)
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val METHOD = stringPreferencesKey("method")
        val USERNAME = stringPreferencesKey("username")
        val MOBILE = stringPreferencesKey("mobile")
    }
}
