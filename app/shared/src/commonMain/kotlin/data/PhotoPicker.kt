package data

import androidx.compose.runtime.Composable

/** A picture the person picked, already prepared for upload, or the reason it could not be. */
sealed interface PickedPhoto {
    /** An upright JPEG, at most [PhotoPrep.MAX_SIDE] px on its long side, quality [PhotoPrep.JPEG_QUALITY]. */
    class Ready(val jpeg: ByteArray) : PickedPhoto

    /** The file could not be read as a picture on this device. */
    data object Unreadable : PickedPhoto
}

/**
 * The system's own photo picker, images only, which needs no permission: the Android Photo
 * Picker, PHPickerViewController on iOS, a file input on the web. Returns what opens it, or null
 * where uploading is not offered (desktop).
 *
 * The picked picture is converted on the device before anything is sent: decoded (HEIC from an
 * iPhone or a Samsung included, where the platform can), turned upright from its EXIF orientation,
 * scaled to [PhotoPrep.MAX_SIDE] and saved as a JPEG. [onPicked] is called on the main thread;
 * nothing is called when the picker is cancelled.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (() -> Unit)?
