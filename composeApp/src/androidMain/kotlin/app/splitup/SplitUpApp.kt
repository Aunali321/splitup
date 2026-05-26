package app.splitup

import android.app.Application
import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.di.sharedModules
import app.splitup.ui.di.uiModule
import app.splitup.ui.oauth.AndroidBrowserLauncher
import app.splitup.ui.oauth.BrowserLauncher
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
                    single<BrowserLauncher> { AndroidBrowserLauncher(get()) }
                },
                *sharedModules.toTypedArray(),
                uiModule,
            )
        }
    }
}
