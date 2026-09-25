package dev.ybdn.ciao.galerie.presentation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.ybdn.ciao.galerie.GalerieApplication
import dev.ybdn.ciao.galerie.di.AppContainer

/** Factory manuelle simple : pas de framework DI, câblage explicite AppContainer -> ViewModel. */
class GalerieViewModelFactory(
    private val application: Application,
    private val appContainer: AppContainer,
    private val create: (AppContainer, Application) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        create(appContainer, application) as T
}

@Composable
inline fun <reified T : ViewModel> galerieViewModel(
    crossinline create: (AppContainer, Application) -> T,
): T {
    val context = LocalContext.current.applicationContext as GalerieApplication
    val factory = GalerieViewModelFactory(context, context.appContainer) { container, app ->
        create(container, app)
    }
    return viewModel(factory = factory)
}

/** Factory d'un ViewModel qui conserve son état dans un `SavedStateHandle` (survie à la mort du processus). */
class GalerieSavedStateViewModelFactory(
    private val application: Application,
    private val appContainer: AppContainer,
    private val create: (AppContainer, Application, SavedStateHandle) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(appContainer, application, extras.createSavedStateHandle()) as T
}

@Composable
inline fun <reified T : ViewModel> galerieSavedStateViewModel(
    key: String,
    crossinline create: (AppContainer, Application, SavedStateHandle) -> T,
): T {
    val context = LocalContext.current.applicationContext as GalerieApplication
    val factory = GalerieSavedStateViewModelFactory(context, context.appContainer) { container, app, handle ->
        create(container, app, handle)
    }
    return viewModel(key = key, factory = factory)
}
