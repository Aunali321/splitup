package app.splitup.ui.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidBrowserLauncher(private val context: Context) : BrowserLauncher {
    override fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
