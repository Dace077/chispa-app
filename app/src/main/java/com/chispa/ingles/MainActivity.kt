package com.chispa.ingles

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.prefs.Settings
import com.chispa.ingles.data.prefs.ThemeMode
import com.chispa.ingles.ui.ChispaAppRoot
import com.chispa.ingles.ui.theme.ChispaTheme
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* El resultado se refleja solo: si lo deniega, simplemente no habrá avisos. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val locator = ServiceLocator.from(this)
        val startDestination = intent?.getStringExtra(EXTRA_DESTINATION)

        lifecycleScope.launch { locator.progressRepository.onAppOpen() }

        val settingsFlow = locator.settingsStore.settings.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = Settings()
        )

        setContent {
            val settings by settingsFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ChispaTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val initialRoute = remember { mutableStateOf(startDestination) }
                    ChispaAppRoot(
                        deepLink = initialRoute.value,
                        onDeepLinkConsumed = { initialRoute.value = null },
                        onRequestNotificationPermission = ::requestNotificationPermission
                    )
                }
            }
        }
        // El permiso de notificaciones NO se pide aquí a propósito: pedirlo antes
        // de que el usuario sepa qué es la app es la forma más rápida de que lo
        // deniegue para siempre. Se pide al final del onboarding, en contexto.
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStop() {
        super.onStop()
        // Cortamos cualquier lectura en curso al salir: nada peor que la app
        // hablando sola desde el segundo plano.
        ServiceLocator.from(this).tts.stop()
    }

    companion object {
        const val EXTRA_DESTINATION = "chispa_destination"
    }
}
