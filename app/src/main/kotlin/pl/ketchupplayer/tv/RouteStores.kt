package pl.ketchupplayer.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** Magazyny ViewModeli tras; sam przeżywa zmianę konfiguracji, więc trasa po obrocie dostaje te same ViewModele. */
internal class RouteStores : ViewModel() {
    val stores = mutableMapOf<String, ViewModelStore>()

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

private class RouteOwner(
    parent: ViewModelStoreOwner,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    private val defaults = parent as HasDefaultViewModelProviderFactory
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = defaults.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras = defaults.defaultViewModelCreationExtras
}

/**
 * Własny zakres ViewModeli dla trasy. Nawigacja to zwykły stos tekstów (bez NavHost), więc `hiltViewModel()` byłby
 * związany z całą Activity i nigdy nie dostałby `onCleared()`: odtwarzacz po BACK grałby w tle. Tu ViewModele znikają
 * razem z trasą (ale nie przy obrocie ekranu).
 */
@Composable
internal fun RouteScope(
    key: String,
    content: @Composable () -> Unit,
) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "Brak ViewModelStoreOwner" }
    val holder: RouteStores = viewModel()
    val owner = remember(key) { RouteOwner(parent, holder.stores.getOrPut(key) { ViewModelStore() }) }
    val activity = LocalContext.current.findActivity()
    DisposableEffect(key) {
        onDispose {
            if (activity?.isChangingConfigurations != true) holder.stores.remove(key)?.clear()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
