package app.splitup.ui.platform

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Bridges the suspend picker API to Android's activity-result contracts.
 * MainActivity calls [attach] in onCreate and [detach] in onDestroy; launchers
 * die with the activity and are re-registered on recreation.
 */
class AndroidImagePicker(private val context: Context) : ImagePicker {

    private var pickLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var cameraLauncher: ActivityResultLauncher<Uri>? = null
    private var pendingPick: CompletableDeferred<Uri?>? = null
    private var pendingCamera: CompletableDeferred<Boolean>? = null

    override val canPickImage = true
    override val canTakePhoto = true

    fun attach(activity: ComponentActivity) {
        pickLauncher = activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> pendingPick?.complete(uri) }
        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { saved -> pendingCamera?.complete(saved) }
    }

    fun detach() {
        pickLauncher = null
        cameraLauncher = null
    }

    override suspend fun pickFromGallery(): String? {
        val launcher = pickLauncher ?: return null
        val result = CompletableDeferred<Uri?>()
        pendingPick = result
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val uri = result.await() ?: return null

        return withContext(Dispatchers.IO) {
            val target = newReceiptFile()
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            if (copied == null) null else "file://${target.absolutePath}"
        }
    }

    override suspend fun takePhoto(): String? {
        val launcher = cameraLauncher ?: return null
        val target = newReceiptFile()
        val targetUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val result = CompletableDeferred<Boolean>()
        pendingCamera = result
        launcher.launch(targetUri)
        return if (result.await()) "file://${target.absolutePath}" else {
            withContext(Dispatchers.IO) { target.delete() }
            null
        }
    }

    override fun discard(url: String) {
        val file = File(url.removePrefix("file://"))
        if (file.parentFile == receiptsDir()) file.delete()
    }

    private fun receiptsDir(): File = File(context.filesDir, "receipts")

    private fun newReceiptFile(): File {
        val dir = receiptsDir().apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }
}
