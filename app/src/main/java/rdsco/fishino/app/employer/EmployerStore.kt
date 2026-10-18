package rdsco.fishino.app.employer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.employerDataStore: DataStore<Preferences> by preferencesDataStore(name = "employer")

/**
 * Persists the confirmed employer. Once saved, the app never asks for the
 * code again unless the user explicitly changes employer.
 */
class EmployerStore(context: Context) {

    private val dataStore = context.applicationContext.employerDataStore

    val employer: Flow<Employer?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val code = preferences[CODE]
            val name = preferences[NAME]
            val apiBaseUrl = preferences[API_BASE_URL]

            if (code != null && name != null && apiBaseUrl != null) Employer(code, name, apiBaseUrl) else null
        }

    suspend fun save(employer: Employer) {
        dataStore.edit {
            it[CODE] = employer.code
            it[NAME] = employer.name
            it[API_BASE_URL] = employer.apiBaseUrl
            it[CONFIRMED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val CODE = stringPreferencesKey("code")
        val NAME = stringPreferencesKey("name")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val CONFIRMED_AT = longPreferencesKey("confirmed_at")
    }
}
