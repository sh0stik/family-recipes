package com.recipebook.recipe_backend.photo

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Pictures made on the fly for the photo tests. */
object TestImages {
    /** [w]×[h], white, with its top-left quarter red: shows which way the picture ended up. */
    fun marked(w: Int, h: Int): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.color = Color.RED
        g.fillRect(0, 0, w / 2, h / 2)
        g.dispose()
        return image
    }

    fun png(w: Int, h: Int): ByteArray = encode(marked(w, h), "png")

    fun jpeg(w: Int, h: Int): ByteArray = encode(marked(w, h), "jpeg")

    /**
     * A JPEG carrying an EXIF block, the way a phone writes one: the orientation tag and an image
     * description holding [secret], which stands in for the GPS position a phone records.
     */
    fun jpegWithExif(w: Int, h: Int, orientation: Int, secret: String): ByteArray {
        val plain = jpeg(w, h)
        val text = (secret + "\u0000").toByteArray(Charsets.US_ASCII)
        // Big-endian TIFF: header, one IFD with two entries, then the description text.
        val tiff = ByteArrayOutputStream()
        fun u16(v: Int) { tiff.write(v shr 8 and 0xFF); tiff.write(v and 0xFF) }
        fun u32(v: Int) { u16(v shr 16 and 0xFFFF); u16(v and 0xFFFF) }
        tiff.write('M'.code); tiff.write('M'.code); u16(42); u32(8)
        u16(2)
        u16(0x010E); u16(2); u32(text.size); u32(8 + 2 + 2 * 12 + 4) // ImageDescription, ASCII, at the end
        u16(0x0112); u16(3); u32(1); u16(orientation); u16(0)         // Orientation, SHORT
        u32(0)                                                        // no next IFD
        tiff.write(text)
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff.toByteArray()
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()) + payload
        // Right after the SOI marker.
        return plain.copyOfRange(0, 2) + app1 + plain.copyOfRange(2, plain.size)
    }

    fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))!!

    fun isRed(rgb: Int): Boolean {
        val c = Color(rgb)
        return c.red > 200 && c.green < 60 && c.blue < 60
    }

    private fun encode(image: BufferedImage, format: String): ByteArray {
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, format, out))
        return out.toByteArray()
    }
}
