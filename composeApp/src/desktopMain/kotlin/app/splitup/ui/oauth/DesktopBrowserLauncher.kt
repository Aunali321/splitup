package app.splitup.ui.oauth

import java.awt.Desktop
import java.net.URI

class DesktopBrowserLauncher : BrowserLauncher {
    override fun open(url: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
