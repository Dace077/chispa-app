package com.chispa.ingles.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chispa.ingles.core.ServiceLocator

/**
 * Puente entre el [ServiceLocator] y los ViewModel de Compose.
 *
 * En lugar de un grafo de inyección completo, cada pantalla declara qué
 * ViewModel quiere y lo construye con el localizador. Menos magia, cero
 * procesadores de anotaciones y el mismo resultado.
 */
@Composable
inline fun <reified VM : ViewModel> chispaViewModel(
    key: String? = null,
    crossinline factory: (ServiceLocator) -> VM
): VM {
    val locator = ServiceLocator.from(LocalContext.current)
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory(locator) as T
        }
    )
}
