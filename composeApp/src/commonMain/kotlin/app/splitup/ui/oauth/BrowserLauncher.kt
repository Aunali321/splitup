package app.splitup.ui.oauth

/** Per-target way to open a URL in the user's browser (or Custom Tab on Android). */
interface BrowserLauncher {
    fun open(url: String)

    /** Whether this platform can route the splitup:// OAuth redirect back into the app. */
    val handlesOAuthRedirect: Boolean
}
