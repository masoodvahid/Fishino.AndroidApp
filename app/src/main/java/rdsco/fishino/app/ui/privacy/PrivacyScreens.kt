package rdsco.fishino.app.ui.privacy

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import rdsco.fishino.app.R
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.PanelCard
import java.io.IOException

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy")

/**
 * Whether the user accepted the privacy notice (Bazaar: shown where the
 * user surely sees it, at first run). A new [VERSION] asks again.
 */
class PrivacyConsent(context: Context) {

    private val dataStore = context.applicationContext.privacyDataStore

    val accepted: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { (it[ACCEPTED] ?: 0) >= VERSION }

    suspend fun accept() {
        dataStore.edit { it[ACCEPTED] = VERSION }
    }

    private companion object {
        const val VERSION = 1
        val ACCEPTED = intPreferencesKey("accepted_version")
    }
}

private val sections = listOf(
    R.string.privacy_s1_title to R.string.privacy_s1_body,
    R.string.privacy_s2_title to R.string.privacy_s2_body,
    R.string.privacy_s3_title to R.string.privacy_s3_body,
    R.string.privacy_s4_title to R.string.privacy_s4_body,
    R.string.privacy_s5_title to R.string.privacy_s5_body,
    R.string.privacy_s6_title to R.string.privacy_s6_body,
    R.string.privacy_s7_title to R.string.privacy_s7_body,
    R.string.privacy_s8_title to R.string.privacy_s8_body,
)

/** What the app receives and keeps, and what for. */
@Composable
fun ColumnScope.PrivacyText() {
    Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)
    sections.forEach { (title, body) ->
        PanelCard {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** First run: the notice, then accept to go on or leave the app. */
@Composable
fun PrivacyConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PrivacyText()
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAccept, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.privacy_accept))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_decline)) }
        }
    }
}

/** The same notice from the menu, any time. */
@Composable
fun PrivacyScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrivacyText()
        }
    }
}
