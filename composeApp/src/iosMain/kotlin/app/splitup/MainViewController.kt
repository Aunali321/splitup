package app.splitup

import androidx.compose.ui.window.ComposeUIViewController
import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.data.sync.SecretStore
import app.splitup.shared.di.sharedModules
import app.splitup.ui.App
import app.splitup.ui.di.uiModule
import app.splitup.ui.oauth.BrowserLauncher
import app.splitup.ui.oauth.IosBrowserLauncher
import app.splitup.ui.platform.AppLock
import app.splitup.ui.platform.FileSharer
import app.splitup.ui.platform.ImagePicker
import app.splitup.ui.platform.IosAppLock
import app.splitup.ui.platform.IosFileSharer
import app.splitup.ui.platform.IosImagePicker
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.UIKit.UIViewController

/** Entry point for the iOS shell: `ComposeApp.MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}

private var koinStarted = false

private fun initKoin() {
    if (koinStarted) return
    koinStarted = true
    startKoin {
        modules(
            module {
                single { DatabaseFactory() }
                single { SecretStore() }
                single<BrowserLauncher> { IosBrowserLauncher() }
                single<FileSharer> { IosFileSharer() }
                single<ImagePicker> { IosImagePicker() }
                single<AppLock> { IosAppLock() }
            },
            *sharedModules.toTypedArray(),
            uiModule,
        )
    }
}
