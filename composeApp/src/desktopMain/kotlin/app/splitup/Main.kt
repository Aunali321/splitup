package app.splitup

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.di.sharedModules
import app.splitup.ui.App
import app.splitup.ui.di.uiModule
import app.splitup.ui.oauth.BrowserLauncher
import app.splitup.ui.oauth.DesktopBrowserLauncher
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    startKoin {
        modules(
            module {
                single { DatabaseFactory() }
                single<BrowserLauncher> { DesktopBrowserLauncher() }
            },
            *sharedModules.toTypedArray(),
            uiModule,
        )
    }
    application {
        val state = rememberWindowState(width = 480.dp, height = 800.dp)
        Window(onCloseRequest = ::exitApplication, title = "SplitUp!", state = state) {
            App()
        }
    }
}
