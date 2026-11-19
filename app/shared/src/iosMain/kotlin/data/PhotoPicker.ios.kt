package data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.roundToInt

@Composable
actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (() -> Unit)? {
    val latest = rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly, so the screen keeps it alive.
    val delegate = remember { PhotoPickerDelegate { latest.value(it) } }
    return remember(delegate) {
        {
            // PHPicker runs outside the app and needs no photo library permission.
            val configuration = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PhotoPickerDelegate(
    private val onPicked: (PickedPhoto) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        // "public.image" covers HEIC, JPEG and PNG; UIImage decodes all of them.
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val jpeg = data?.let { runCatching { uploadJpeg(it) }.getOrNull() }
            dispatch_async(dispatch_get_main_queue()) {
                onPicked(if (jpeg != null) PickedPhoto.Ready(jpeg) else PickedPhoto.Unreadable)
            }
        }
    }
}

/**
 * [data] as an upright JPEG of at most [PhotoPrep.MAX_SIDE] px. Drawing the UIImage applies its
 * orientation (UIImage.size is already the upright size), on white for transparent pictures.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun uploadJpeg(data: NSData): ByteArray? {
    val image = UIImage.imageWithData(data) ?: return null
    val (w, h) = image.size.useContents { PhotoPrep.targetSize(width.roundToInt(), height.roundToInt()) }
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        scale = 1.0
        opaque = true
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w.toDouble(), h.toDouble()), format = format)
    val upright = renderer.imageWithActions { _ ->
        val rect = CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble())
        UIColor.whiteColor.setFill()
        UIRectFill(rect)
        image.drawInRect(rect)
    }
    val jpeg = UIImageJPEGRepresentation(upright, PhotoPrep.JPEG_QUALITY / 100.0) ?: return null
    return jpeg.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
