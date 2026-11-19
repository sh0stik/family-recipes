package com.recipebook.recipe_backend.photo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoImagesTest {

    @Test
    fun `the EXIF orientation is read from a phone-style JPEG`() {
        for (o in 1..8) {
            assertEquals(o, PhotoImages.exifOrientation(TestImages.jpegWithExif(40, 20, o, "x")), "orientation $o")
        }
        assertEquals(1, PhotoImages.exifOrientation(TestImages.jpeg(40, 20)))
        assertEquals(1, PhotoImages.exifOrientation(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun `each orientation puts the marked corner where it belongs`() {
        // The red quarter is stored top-left. Where it should be once displayed (x, y of a corner):
        val expected = mapOf(
            1 to "top-left", 2 to "top-right", 3 to "bottom-right", 4 to "bottom-left",
            5 to "top-left", 6 to "top-right", 7 to "bottom-right", 8 to "bottom-left",
        )
        for ((o, corner) in expected) {
            val out = TestImages.decode(PhotoImages.toJpeg(TestImages.jpegWithExif(60, 40, o, "x")))
            val (w, h) = if (o >= 5) 40 to 60 else 60 to 40
            assertEquals(w to h, out.width to out.height, "size for orientation $o")
            val red = mapOf(
                "top-left" to out.getRGB(5, 5), "top-right" to out.getRGB(w - 6, 5),
                "bottom-left" to out.getRGB(5, h - 6), "bottom-right" to out.getRGB(w - 6, h - 6),
            ).filterValues(TestImages::isRed).keys
            assertEquals(setOf(corner), red, "orientation $o")
        }
    }

    @Test
    fun `the result carries no EXIF and nothing from the original's metadata`() {
        val out = PhotoImages.toJpeg(TestImages.jpegWithExif(64, 48, 6, "GPS 48.8584N 2.2945E"))
        val text = String(out, Charsets.ISO_8859_1)
        assertFalse(text.contains("Exif"))
        assertFalse(text.contains("48.8584"))
        assertEquals(1, PhotoImages.exifOrientation(out))
    }

    @Test
    fun `big pictures shrink to 2048 on the long side and small ones keep their size`() {
        val big = TestImages.decode(PhotoImages.toJpeg(TestImages.png(3000, 1000)))
        assertEquals(2048 to 683, big.width to big.height)
        val tall = TestImages.decode(PhotoImages.toJpeg(TestImages.jpeg(900, 5000)))
        assertEquals(369 to 2048, tall.width to tall.height)
        val small = TestImages.decode(PhotoImages.toJpeg(TestImages.png(300, 200)))
        assertEquals(300 to 200, small.width to small.height)
    }

    @Test
    fun `anything that is not a JPEG or PNG is refused`() {
        fun code(bytes: ByteArray) = assertThrows<PhotoRejectedException> { PhotoImages.toJpeg(bytes) }.code
        assertEquals("NOT_AN_IMAGE", code("hello, not a picture".toByteArray()))
        assertEquals("NOT_AN_IMAGE", code(TestImages.jpeg(50, 50).copyOfRange(0, 20)))
        assertEquals("NOT_AN_IMAGE", code(byteArrayOf()))
        assertEquals("UNSUPPORTED_FORMAT", code("GIF89a....".toByteArray()))
        assertEquals("UNSUPPORTED_FORMAT", code("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertEquals("UNSUPPORTED_FORMAT", code("\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
    }

    @Test
    fun `the output is always a JPEG`() {
        assertTrue(PhotoImages.sniff(PhotoImages.toJpeg(TestImages.png(20, 20))) == PhotoImages.Format.JPEG)
    }
}
