package xendroid.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.FrontendLaunch
import xendroid.compose.ui.library.ACTION_LAUNCH_GAME
import xendroid.compose.ui.library.EXTRA_GAME_URI
import xendroid.compose.core.SessionLogs
import xendroid.compose.ui.AppNavHost
import xendroid.compose.ui.theme.xendroidTheme
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.seedTouchOverlayDefault
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {

    companion object {
        // One rotation per main-process lifetime = the app-session boundary.
        private val sessionRotated = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same intent shape as the in-app launch, which routes through the
        // manifest filter into the host's own task. Finishing here instead
        // collapses that task and the emulator is paused before it draws.
        val frontendGame = FrontendLaunch.resolveGamePath(this, intent)
        if (frontendGame != null) {
            startActivity(
                Intent(ACTION_LAUNCH_GAME).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_GAME_URI, frontendGame)
                }
            )
        }

        if (!sessionRotated.getAndSet(true)) {
            val appContext = applicationContext

            thread(name = "SessionLogs") {
                runCatching { SessionLogs.startAppSession(appContext) }

                // Pre-warm so settings doesn't pay the delay-load System.loadLibrary.
                runCatching { EmulatorRuntime.ensureLoaded() }
                // Needs the native config, so it follows ensureLoaded on this same thread.
                runCatching { seedTouchOverlayDefault(appContext, ConfigStore(appContext)) }
            }
        }

        val container = AppContainer(applicationContext)

        enableEdgeToEdge()

       setContent {
            xendroidTheme {

                AppNavHost(container)

                // No startup update check: this fork ships without the INTERNET
                // permission, so asking GitHub could only ever fail.
            }
        }
    }
}