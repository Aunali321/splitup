package app.splitup.ui.oauth

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosBrowserLauncher : BrowserLauncher {
    override fun open(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    // No Xcode shell registers the splitup:// scheme yet, so the OAuth redirect
    // can't come back into the app. Import uses a pasted API key instead.
    override val handlesOAuthRedirect = false
}
