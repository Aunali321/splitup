package app.splitup.ui.platform

/**
 * Per-target way to hand a generated file to the user — Android opens the share
 * sheet, desktop opens a save dialog, iOS presents the activity controller.
 */
interface FileSharer {
    fun shareTextFile(fileName: String, mimeType: String, content: String)
}
