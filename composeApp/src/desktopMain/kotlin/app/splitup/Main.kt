package app.splitup

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.data.sync.SecretStore
import app.splitup.shared.di.sharedModules
import app.splitup.ui.App
import app.splitup.ui.di.uiModule
import app.splitup.ui.oauth.BrowserLauncher
import app.splitup.ui.oauth.DesktopBrowserLauncher
import app.splitup.ui.platform.AppLock
import app.splitup.ui.platform.DesktopAppLock
import app.splitup.ui.platform.DesktopFileSharer
import app.splitup.ui.platform.DesktopImagePicker
import app.splitup.ui.platform.FileSharer
import app.splitup.ui.platform.ImagePicker
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    startKoin {
        modules(
            module {
                single { DatabaseFactory() }
                single { SecretStore() }
                single<BrowserLauncher> { DesktopBrowserLauncher() }
                single<FileSharer> { DesktopFileSharer() }
                single<ImagePicker> { DesktopImagePicker() }
                single<AppLock> { DesktopAppLock() }
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
