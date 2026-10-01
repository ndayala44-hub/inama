package rw.inama.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.navigation.InamaNavHost
import rw.inama.app.ui.theme.InamaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InamaApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CompositionLocalProvider(LocalAppContainer provides container) {
                InamaTheme(textScale = settings.textScale.factor, highContrast = settings.highContrast) {
                    InamaNavHost()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as InamaApp).container.speechOutput.stop()
    }
}
