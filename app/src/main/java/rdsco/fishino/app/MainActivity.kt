package rdsco.fishino.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import rdsco.fishino.app.employer.Employer
import rdsco.fishino.app.ui.home.HomeScreen
import rdsco.fishino.app.ui.onboarding.EmployerCodeScreen
import rdsco.fishino.app.ui.onboarding.EmployerCodeViewModel
import rdsco.fishino.app.ui.theme.FishinoTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface EmployerState {
    data object Loading : EmployerState
    data class Ready(val employer: Employer?) : EmployerState
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Light UI only: dark system-bar icons on the light background.
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)

        val container = (application as FishinoApp).container

        setContent {
            FishinoTheme {
                val scope = rememberCoroutineScope()
                val employerFlow = remember { container.employerStore.employer.map<Employer?, EmployerState> { EmployerState.Ready(it) } }
                val state by employerFlow.collectAsStateWithLifecycle(initialValue = EmployerState.Loading)

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (val current = state) {
                        EmployerState.Loading -> Unit
                        is EmployerState.Ready -> {
                            val employer = current.employer

                            if (employer == null) {
                                val onboarding: EmployerCodeViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer { EmployerCodeViewModel(container.employerDirectory, container.employerStore) }
                                    },
                                )
                                EmployerCodeScreen(onboarding)
                            } else {
                                HomeScreen(
                                    employer = employer,
                                    onChangeEmployer = { scope.launch { container.employerStore.clear() } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
