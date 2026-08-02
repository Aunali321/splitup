package app.splitup

import android.app.Application
import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.data.sync.SecretStore
import app.splitup.shared.di.sharedModules
import app.splitup.ui.di.uiModule
import app.splitup.ui.oauth.AndroidBrowserLauncher
import app.splitup.ui.oauth.BrowserLauncher
import app.splitup.ui.platform.AndroidAppLock
import app.splitup.ui.platform.AndroidFileSharer
import app.splitup.ui.platform.AndroidImagePicker
import app.splitup.ui.platform.AppLock
import app.splitup.ui.platform.FileSharer
import app.splitup.ui.platform.ImagePicker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SplitUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SplitUpApp)
            modules(
                module {
                    single { DatabaseFactory(get()) }
                    single { SecretStore(get()) }
                    single<BrowserLauncher> { AndroidBrowserLauncher(get()) }
                    single<FileSharer> { AndroidFileSharer(get()) }
                    single<ImagePicker> { AndroidImagePicker(get()) }
                    single<AppLock> { AndroidAppLock(get()) }
                },
                *sharedModules.toTypedArray(),
                uiModule,
            )
        }
    }
}
