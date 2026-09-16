package dev.ybdn.ciaocloud.presentation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.di.AppContainer

/** Factory manuelle simple : pas de framework DI, câblage explicite AppContainer -> ViewModel. */
class CiaoCloudViewModelFactory(
    private val application: Application,
    private val appContainer: AppContainer,
    private val create: (AppContainer, Application) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        create(appContainer, application) as T
}

@Composable
inline fun <reified T : ViewModel> ciaoCloudViewModel(
    crossinline create: (AppContainer, Application) -> T,
): T {
    val context = LocalContext.current.applicationContext as CiaoCloudApplication
    val factory = CiaoCloudViewModelFactory(context, context.appContainer) { container, app ->
        create(container, app)
    }
    return viewModel(factory = factory)
}
