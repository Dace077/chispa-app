package com.chispa.ingles.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.AppInfo
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.prefs.Accent
import com.chispa.ingles.data.prefs.Settings
import com.chispa.ingles.data.prefs.ThemeMode
import com.chispa.ingles.domain.DailyGoal
import com.chispa.ingles.notifications.ReminderScheduler
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val dailyGoalXp: Int = 20,
    val supportedAccents: List<Accent> = Accent.entries
)

class SettingsViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locator.settingsStore.settings.collect { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    supportedAccents = locator.tts.supportedAccents()
                )
                // Cualquier cambio en preferencias reprograma los recordatorios.
                ReminderScheduler.applyAll(locator.appContext, settings)
            }
        }
        viewModelScope.launch {
            locator.progressRepository.profile.collect { profile ->
                _state.value = _state.value.copy(dailyGoalXp = profile.dailyGoalXp)
            }
        }
    }

    private fun edit(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun setTheme(mode: ThemeMode) = edit { locator.settingsStore.setThemeMode(mode) }
    fun setNotifications(enabled: Boolean) = edit { locator.settingsStore.setNotificationsEnabled(enabled) }
    fun setReminderTime(hour: Int, minute: Int) = edit { locator.settingsStore.setReminderTime(hour, minute) }
    fun setStreakAlerts(enabled: Boolean) = edit { locator.settingsStore.setStreakAlerts(enabled) }
    fun setReviewAlerts(enabled: Boolean) = edit { locator.settingsStore.setReviewAlerts(enabled) }
    fun setComebackAlerts(enabled: Boolean) = edit { locator.settingsStore.setComebackAlerts(enabled) }
    fun setAutoPlay(enabled: Boolean) = edit { locator.settingsStore.setAutoPlay(enabled) }
    fun setSpeechRate(rate: Float) = edit { locator.settingsStore.setSpeechRate(rate) }
    fun setAccent(accent: Accent) = edit { locator.settingsStore.setAccent(accent) }
    fun setDailyGoal(goal: DailyGoal) = edit { locator.progressRepository.setDailyGoal(goal.xp) }

    fun previewVoice() {
        locator.tts.speak(
            "Hello! This is how I will sound.",
            _state.value.settings.accent,
            _state.value.settings.speechRate
        )
    }

    fun resetProgress(onDone: () -> Unit) = edit {
        locator.progressRepository.resetEverything()
        onDone()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = chispaViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    val colors = ChispaThemeTokens.colors

    var showTimePicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var sinNavegador by remember { mutableStateOf(false) }
    val contexto = LocalContext.current

    // En Android 13+ activar el interruptor no basta: hace falta el permiso del
    // sistema. Se pide justo aquí, que es donde el usuario ha expresado interés.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Si lo deniega, el interruptor queda activo pero no llegará nada. */ }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 44.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Configuración", style = MaterialTheme.typography.headlineMedium)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            /* ---------------- Meta diaria ---------------- */
            SectionTitle("Tu meta diaria")
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    DailyGoal.entries.forEach { goal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setDailyGoal(goal) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioDot(selected = state.dailyGoalXp == goal.xp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(goal.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    goal.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${goal.xp} XP",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.xp
                            )
                        }
                    }
                }
            }

            /* ---------------- Recordatorios ---------------- */
            SectionTitle("Recordatorios")
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        title = "Activar notificaciones",
                        subtitle = "Todo local. Nada sale de tu teléfono.",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setNotifications(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    if (settings.notificationsEnabled) {
                        Divider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Hora del recordatorio", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Te avisaré si aún no cumpliste la meta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Divider()
                        SwitchRow(
                            title = "Aviso de racha en riesgo",
                            subtitle = "Un empujón a las 21:30 si te falta poco",
                            checked = settings.streakAlertsEnabled,
                            onCheckedChange = viewModel::setStreakAlerts
                        )
                        Divider()
                        SwitchRow(
                            title = "Aviso de repaso listo",
                            subtitle = "Cuando se acumulan palabras por repasar",
                            checked = settings.reviewAlertsEnabled,
                            onCheckedChange = viewModel::setReviewAlerts
                        )
                        Divider()
                        SwitchRow(
                            title = "Mensajes si desapareces",
                            subtitle = "A los 3, 7 y 14 días sin abrir la app",
                            checked = settings.comebackAlertsEnabled,
                            onCheckedChange = viewModel::setComebackAlerts
                        )
                    }
                }
            }

            /* ---------------- Voz ---------------- */
            SectionTitle("Voz y audio")
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Acento", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Accent.entries.forEach { accent ->
                            val available = accent in state.supportedAccents
                            AccentChip(
                                accent = accent,
                                selected = settings.accent == accent,
                                enabled = available,
                                onClick = { viewModel.setAccent(accent) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(10.dp))

                    Text("Velocidad de la voz", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.speechRate,
                        onValueChange = viewModel::setSpeechRate,
                        valueRange = 0.5f..1.3f,
                        steps = 7
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lenta", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "%.1fx".format(settings.speechRate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Rápida", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { viewModel.previewVoice() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Probar voz",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Divider()
                    SwitchRow(
                        title = "Reproducir audio solo",
                        subtitle = "En los ejercicios de escucha, sin tener que pulsar",
                        checked = settings.autoPlayAudio,
                        onCheckedChange = viewModel::setAutoPlay
                    )
                }
            }

            /* ---------------- Apariencia ---------------- */
            SectionTitle("Apariencia")
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setTheme(mode) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioDot(selected = settings.themeMode == mode)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Seguir al sistema"
                                    ThemeMode.LIGHT -> "Siempre claro"
                                    ThemeMode.DARK -> "Siempre oscuro"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            /* ---------------- Datos ---------------- */
            SectionTitle("Datos")
            ChispaCard(borderColor = colors.wrong.copy(alpha = 0.4f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reiniciar todo el progreso", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Borra XP, racha, lecciones completadas y vocabulario aprendido. " +
                            "No se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showResetDialog = true }) {
                        Text("Reiniciar progreso", color = colors.wrong)
                    }
                }
            }

            /* ---------------- Actualizaciones ---------------- */
            SectionTitle("Actualizaciones")
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Versión instalada", style = MaterialTheme.typography.titleSmall)
                            Text(
                                AppInfo.versionName(contexto),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // El texto cambia según de dónde vino esta copia: quien la
                    // instaló de Play ya se actualiza solo y no hay que mandarlo
                    // a ninguna otra parte.
                    val desdePlay = remember { AppInfo.installedFromPlay(contexto) }
                    Text(
                        if (desdePlay) {
                            "Google Play te actualiza Chispa solo, en segundo plano. No " +
                                "tienes que hacer nada. Si quieres comprobarlo ahora mismo, " +
                                "este botón abre su ficha en la tienda."
                        } else {
                            "Chispa no puede conectarse a internet, así que no comprueba " +
                                "actualizaciones por su cuenta. Este botón abre la página de " +
                                "descargas en tu navegador: si hay una versión más nueva, la " +
                                "verás ahí. Instálala encima y conservas todo tu progreso."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    ChispaButton(
                        text = if (desdePlay) "Ver en Google Play" else "Buscar actualizaciones",
                        icon = Icons.Filled.SystemUpdate,
                        onClick = { sinNavegador = !AppInfo.openUpdatePage(contexto) }
                    )
                    if (sinNavegador) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (desdePlay) {
                                "No pude abrir Google Play en este dispositivo."
                            } else {
                                "No encontré ningún navegador en el dispositivo. La dirección es:\n" +
                                    AppInfo.RELEASES_URL
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.wrong
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Al instalar encima conservas tu racha, tu XP y todo tu progreso.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.correct
                    )
                }
            }

            /* ---------------- Acerca de ---------------- */
            SectionTitle("Acerca de Chispa")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Text(
                    "Chispa ${AppInfo.versionName(contexto)}\n\n" +
                        "Funciona 100% sin conexión. No pide permiso de internet, no tiene " +
                        "anuncios, no tiene compras y no recoge ningún dato tuyo.\n\n" +
                        "La voz y el reconocimiento de habla son los que ya trae tu Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.reminderHour,
            initialMinute = settings.reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("¿A qué hora te aviso?") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderTime(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("¿Borrar todo tu progreso?") },
            text = {
                Text(
                    "Perderás la racha de ${state.dailyGoalXp} XP diarios, tu XP acumulada, " +
                        "las lecciones completadas y el vocabulario aprendido. Esto no tiene vuelta atrás."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetProgress { onBack() }
                }) { Text("Sí, borrar todo", color = colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/* =========================================================================
 *  Piezas
 * ========================================================================= */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = ChispaThemeTokens.colors
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else colors.lockedContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@Composable
private fun AccentChip(
    accent: Accent,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChispaThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> colors.lockedContainer
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> colors.surfaceElevated
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(accent.flag, style = MaterialTheme.typography.titleLarge)
        Text(
            accent.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.locked
        )
        if (!enabled) {
            Text(
                "no instalado",
                style = MaterialTheme.typography.labelSmall,
                color = colors.locked
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChispaThemeTokens.colors.cardStroke)
    )
}
