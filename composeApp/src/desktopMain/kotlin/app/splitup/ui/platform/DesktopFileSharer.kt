package app.splitup.ui.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class DesktopFileSharer : FileSharer {
    override fun shareTextFile(fileName: String, mimeType: String, content: String) {
        val dialog = FileDialog(null as Frame?, "Save $fileName", FileDialog.SAVE).apply {
            file = fileName
            isVisible = true
        }
        val chosenName = dialog.file ?: return
        File(dialog.directory, chosenName).writeText(content)
    }
}
