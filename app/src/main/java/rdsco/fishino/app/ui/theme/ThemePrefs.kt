package rdsco.fishino.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(name = "display")

/** Light, dark or as the phone: kept on this phone for every account; light until the user chooses. */
class ThemePrefs(context: Context) {

    private val dataStore = context.applicationContext.displayDataStore

    val mode: Flow<ThemeMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> ThemeMode.entries.firstOrNull { it.name == preferences[MODE] } ?: ThemeMode.Light }

    suspend fun set(mode: ThemeMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("theme_mode")
    }
}
