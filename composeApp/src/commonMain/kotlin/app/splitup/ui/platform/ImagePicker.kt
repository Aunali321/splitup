package app.splitup.ui.platform

/**
 * Per-target image acquisition for receipts. Implementations copy the picked
 * image into app-owned storage and return a `file://` URI, or null on cancel.
 */
interface ImagePicker {
    /** False when the platform shell can't host a picker yet — hide the affordance. */
    val canPickImage: Boolean
    val canTakePhoto: Boolean
    suspend fun pickFromGallery(): String?
    suspend fun takePhoto(): String?

    /** Deletes a previously picked receipt file; ignores anything not app-owned. */
    fun discard(url: String)
}

/** Receipt URLs are either remote (imported) or `file://` copies this app made. */
fun String.isLocalReceipt(): Boolean = startsWith("file://")
