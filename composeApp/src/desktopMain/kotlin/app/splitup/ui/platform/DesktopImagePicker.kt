package app.splitup.ui.platform

import app.splitup.shared.util.appDataDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID

class DesktopImagePicker : ImagePicker {

    override val canPickImage = true
    override val canTakePhoto = false

    override suspend fun pickFromGallery(): String? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD).apply {
            setFilenameFilter { _, name ->
                name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp")
            }
            isVisible = true
        }
        val name = dialog.file ?: return@withContext null
        val source = File(dialog.directory, name)

        val dir = File(appDataDir(), "receipts").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.${source.extension.lowercase()}")
        source.copyTo(target)
        "file://${target.absolutePath}"
    }

    override suspend fun takePhoto(): String? = null

    override fun discard(url: String) {
        val file = File(url.removePrefix("file://"))
        if (file.parentFile == File(appDataDir(), "receipts")) file.delete()
    }
}
