package rw.inama.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import rw.inama.app.di.AppContainer

/** Gives screens access to the dependency container (ViewModels are created from it). */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Light (white) status-bar icons over dark headers; dark icons elsewhere. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(light) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !light
        onDispose { if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}
