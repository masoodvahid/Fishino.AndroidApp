package rdsco.fishino.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.AppUser
import rdsco.fishino.app.employer.Employer

/**
 * Placeholder until the app's features are built: who is signed in, for
 * which employer, and sign-out.
 */
@Composable
fun HomeScreen(
    employer: Employer,
    user: AppUser,
    appVersion: String,
    onCheckUpdates: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.home_greeting, user.fullName.ifBlank { user.username }),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    employer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.home_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_logout))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.home_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onCheckUpdates != null) {
                    TextButton(onClick = onCheckUpdates) {
                        Text(stringResource(R.string.home_check_updates))
                    }
                }
            }
        }
    }
}
