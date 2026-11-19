package data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** The iOS side of preparing a picked photo, on the simulator: UIImage decoding, orientation, size. */
@OptIn(ExperimentalForeignApi::class)
class PhotoPrepIosTest {

    /** A [w]×[h] picture saved as a JPEG whose EXIF says "turn it a quarter clockwise", as a phone does. */
    private fun sidewaysJpeg(w: Double, h: Double): NSData {
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0; opaque = true }
        val drawn = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format).imageWithActions { _ ->
            UIColor.whiteColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, w, h))
            UIColor.redColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, w / 2, h / 2))
        }
        val sideways = UIImage.imageWithCGImage(drawn.CGImage, scale = 1.0, orientation = UIImageOrientation.UIImageOrientationRight)
        return assertNotNull(UIImageJPEGRepresentation(sideways, 0.9))
    }

    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

    private fun sizeOf(jpeg: ByteArray): Pair<Int, Int> {
        val image = assertNotNull(UIImage.imageWithData(jpeg.toNSData()))
        assertEquals(UIImageOrientation.UIImageOrientationUp, image.imageOrientation, "no orientation left to apply")
        return image.size.useContents { width.toInt() to height.toInt() }
    }

    @Test
    fun aSidewaysPhoneJpegComesOutUprightWithNoOrientationTag() {
        val jpeg = assertNotNull(uploadJpeg(sidewaysJpeg(1600.0, 1200.0)))
        // Stored landscape, displayed portrait: the pixels themselves are now portrait.
        assertEquals(1200 to 1600, sizeOf(jpeg))
    }

    @Test
    fun aLargePictureIsScaledTo2048OnTheLongSide() {
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0; opaque = true }
        val big = UIGraphicsImageRenderer(size = CGSizeMake(3000.0, 2000.0), format = format).imageWithActions { _ ->
            UIColor.greenColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, 3000.0, 2000.0))
        }
        val jpeg = assertNotNull(uploadJpeg(assertNotNull(UIImageJPEGRepresentation(big, 0.9))))
        assertEquals(2048 to 1365, sizeOf(jpeg))
    }

    @Test
    fun somethingThatIsNotAPictureGivesNothing() {
        assertEquals(null, uploadJpeg("not a picture".encodeToByteArray().toNSData()))
    }

    @Test
    fun anUnreachableServerThrowsSoTheUploaderReportsANetworkFailure() = runBlocking {
        // Port 9 (discard) has nothing listening on the simulator host.
        assertFailsWith<Throwable> { postPhoto("http://127.0.0.1:9/api/photos", "t", byteArrayOf(1)) }
        val result = PhotoUploader("http://127.0.0.1:9/api/photos", { "t" }, {}).upload(byteArrayOf(1))
        assertEquals(true, result.exceptionOrNull() is UploadNetworkException)
    }
}
