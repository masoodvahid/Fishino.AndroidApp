package rdsco.fishino.app.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/** When the app last asked GitHub, so it does not ask on every launch. */
class UpdateStore(context: Context) {

    private val dataStore = context.applicationContext.updateDataStore

    suspend fun lastCheckAt(): Long = runCatching { dataStore.data.first()[LAST_CHECK_AT] }.getOrNull() ?: 0L

    suspend fun markChecked(now: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_CHECK_AT] = now }
    }

    private companion object {
        val LAST_CHECK_AT = longPreferencesKey("last_check_at")
    }
}
