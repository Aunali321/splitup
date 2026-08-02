package app.splitup.ui.oauth

import java.awt.Desktop
import java.net.URI

class DesktopBrowserLauncher : BrowserLauncher {
    override fun open(url: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }

    // No splitup:// scheme handler is registered on desktop, so the OAuth
    // redirect would dead-end in the OS. Import uses a pasted API key instead.
    override val handlesOAuthRedirect = false
}
