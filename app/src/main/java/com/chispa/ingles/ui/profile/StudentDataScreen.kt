package com.chispa.ingles.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.db.UserProfileEntity
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Datos del alumno.
 *
 * Existe por el certificado: una constancia sin nombre no le sirve a nadie, y
 * pedirle el nombre a alguien justo cuando acaba de terminar el nivel y quiere
 * su diploma es la peor forma de hacerlo.
 *
 * Se piden tres campos y ni uno más. Nada de correo, teléfono o fecha de
 * nacimiento: no hacen falta para imprimir una constancia y la app no tiene
 * dónde mandarlos aunque quisiera.
 */

/** Lo que el usuario está escribiendo. Se comparte entre onboarding y ajustes. */
class StudentDataState {
    var name by mutableStateOf("")
    var surname by mutableStateOf("")
    var city by mutableStateOf("")

    /** Con el nombre basta; el apellido y la ciudad mejoran el certificado. */
    val isValid: Boolean get() = name.trim().length >= 2

    val preview: String
        get() = listOf(name.trim(), surname.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    fun loadFrom(profile: UserProfileEntity) {
        name = profile.studentName
        surname = profile.studentSurname
        city = profile.studentCity
    }
}

@Composable
fun rememberStudentDataState(): StudentDataState = remember { StudentDataState() }

/**
 * Los campos sueltos, sin botones ni barra: así la misma hoja sirve dentro del
 * onboarding (como un paso más) y dentro de Configuración (como pantalla).
 */
@Composable
fun StudentDataFields(
    state: StudentDataState,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTitle) {
            Text("¿Cómo te llamas?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Es el nombre que llevará tu certificado cuando termines un nivel. " +
                    "Escríbelo como quieras verlo impreso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = { if (it.length <= MAX_FIELD) state.name = it },
            label = { Text("Nombre(s)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.surname,
            onValueChange = { if (it.length <= MAX_FIELD) state.surname = it },
            label = { Text("Apellidos") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.city,
            onValueChange = { if (it.length <= MAX_FIELD) state.city = it },
            label = { Text("Ciudad (opcional)") },
            supportingText = { Text("Aparece en la línea de lugar y fecha del certificado") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        CertificatePreviewNote(state.preview)
        Spacer(Modifier.height(12.dp))
        PrivacyNote()
    }
}

/** Enseñar cómo va a quedar evita el "ay, lo escribí en minúsculas" de después. */
@Composable
private fun CertificatePreviewNote(preview: String) {
    if (preview.isBlank()) return
    val colors = ChispaThemeTokens.colors

    ChispaCard(background = colors.surfaceElevated) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ASÍ SE VERÁ EN TU CERTIFICADO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                preview,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 8.dp)
        )
        Text(
            "Esto se queda en tu teléfono. Chispa no tiene permiso de internet, " +
                "así que no hay forma de que estos datos salgan de aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* =========================================================================
 *  Pantalla completa, para editar los datos desde Configuración
 * ========================================================================= */

class StudentDataViewModel(private val locator: ServiceLocator) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> = locator.progressRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(state: StudentDataState, onSaved: () -> Unit) {
        viewModelScope.launch {
            locator.progressRepository.saveStudentData(
                name = state.name,
                surname = state.surname,
                city = state.city
            )
            onSaved()
        }
    }
}

@Composable
fun StudentDataScreen(onBack: () -> Unit) {
    val viewModel: StudentDataViewModel = chispaViewModel { StudentDataViewModel(it) }
    val profile by viewModel.profile.collectAsState()
    val state = rememberStudentDataState()

    // Rellenar una sola vez con lo que ya hubiera guardado.
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(profile) {
        val p = profile
        if (p != null && !loaded) {
            state.loadFrom(p)
            loaded = true
        }
    }

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
            Spacer(Modifier.height(0.dp))
            Text("Tus datos", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "El nombre que se imprime en tus certificados. Puedes cambiarlo " +
                    "cuando quieras; los certificados ya emitidos conservan el nombre " +
                    "con el que se emitieron.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            StudentDataFields(state = state, showTitle = false)
            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChispaButton(
                text = "Guardar",
                enabled = state.isValid,
                onClick = { viewModel.save(state, onBack) }
            )
        }
    }
}

private const val MAX_FIELD = 40
