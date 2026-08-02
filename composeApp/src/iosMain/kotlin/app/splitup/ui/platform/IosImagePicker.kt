package app.splitup.ui.platform

/**
 * Receipt picking needs PHPicker/camera delegates hosted by the Xcode shell,
 * which does not exist yet (no macOS in this environment). Until then the
 * picker reports itself unavailable and the receipt chip is hidden on iOS.
 */
class IosImagePicker : ImagePicker {
    override val canPickImage = false
    override val canTakePhoto = false
    override suspend fun pickFromGallery(): String? = null
    override suspend fun takePhoto(): String? = null
    override fun discard(url: String) = Unit
}
