package com.recipebook.recipe_backend.photo

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** Why an upload was refused. [code] goes to the apps as `{"code": …}`. */
class PhotoRejectedException(val code: String, message: String) : RuntimeException(message)

/**
 * Turns whatever was uploaded into the one kind of file the server keeps: an upright JPEG at most
 * [MAX_SIDE] pixels on its long side, with nothing but pixels in it.
 *
 * The image is really decoded, not trusted by its name or content type: a file that does not
 * decode as JPEG or PNG is refused. Writing a fresh JPEG from the decoded pixels drops every bit
 * of metadata the original carried — EXIF with the phone's GPS position, camera serials, comments.
 * The EXIF orientation is read first and applied to the pixels, so a phone photo taken sideways
 * is still upright once that tag is gone.
 *
 * Plain JDK ImageIO, no native libraries: it reads JPEG and PNG. WebP is accepted only if an
 * ImageIO reader for it is installed (none is today); HEIC, GIF and WebP otherwise get
 * UNSUPPORTED_FORMAT. The apps convert to JPEG before uploading, so only an unusual client sees it.
 */
object PhotoImages {
    const val MAX_SIDE = 2048
    const val JPEG_QUALITY = 0.85f

    fun toJpeg(bytes: ByteArray): ByteArray {
        val format = sniff(bytes)
        when (format) {
            Format.JPEG, Format.PNG -> {}
            Format.WEBP -> if (!ImageIO.getImageReadersByFormatName("webp").hasNext()) unsupported("WebP")
            Format.GIF -> unsupported("GIF")
            Format.HEIF -> unsupported("HEIC/AVIF")
            Format.UNKNOWN -> notAnImage()
        }
        val decoded = decode(bytes)
        val orientation = if (format == Format.JPEG) exifOrientation(bytes) else 1
        val scaled = scaleToRgb(decoded, MAX_SIDE)
        return writeJpeg(orient(scaled, orientation))
    }

    internal enum class Format { JPEG, PNG, WEBP, GIF, HEIF, UNKNOWN }

    internal fun sniff(b: ByteArray): Format {
        fun at(i: Int, vararg v: Int) = b.size >= i + v.size && v.indices.all { (b[i + it].toInt() and 0xFF) == v[it] }
        fun ascii(i: Int, s: String) = at(i, *s.map { it.code }.toIntArray())
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> Format.JPEG
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Format.PNG
            ascii(0, "RIFF") && ascii(8, "WEBP") -> Format.WEBP
            ascii(0, "GIF8") -> Format.GIF
            ascii(4, "ftyp") -> Format.HEIF
            else -> Format.UNKNOWN
        }
    }

    /**
     * Decodes [bytes], reading only every n-th pixel of a very large picture: a 200-megapixel
     * phone photo would otherwise need close to a gigabyte of memory. It is still read at twice
     * the final size or more, so the smooth downscale afterwards has enough detail.
     */
    private fun decode(bytes: ByteArray): BufferedImage {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: notAnImage()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0) notAnImage()
                    val param = reader.defaultReadParam
                    val step = max(1, max(width, height) / (MAX_SIDE * 2))
                    if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                    return reader.read(0, param) ?: notAnImage()
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: PhotoRejectedException) {
            throw e
        } catch (e: Exception) {
            // Truncated or corrupt data, a CMYK JPEG ImageIO cannot convert, a lying header.
            notAnImage()
        }
    }

    /** At most [maxSide] on the long side, as opaque RGB: transparent PNG areas become white. */
    internal fun scaleToRgb(source: BufferedImage, maxSide: Int): BufferedImage {
        var current = source
        val longSide = max(source.width, source.height)
        val scale = if (longSide > maxSide) maxSide.toDouble() / longSide else 1.0
        val targetW = max(1, (source.width * scale).roundToInt())
        val targetH = max(1, (source.height * scale).roundToInt())
        // Halve in steps first: one big bilinear step from far above the target size aliases.
        while (current.width / 2 >= targetW && current.height / 2 >= targetH) {
            current = draw(current, current.width / 2, current.height / 2)
        }
        return draw(current, targetW, targetH)
    }

    private fun draw(source: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color.WHITE
            g.fillRect(0, 0, w, h)
            g.drawImage(source, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /**
     * [image] turned the way EXIF [orientation] (1–8) says it should be displayed. 1 is already
     * upright; 2–4 mirror or turn it half way; 5–8 swap width and height.
     */
    internal fun orient(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return image
        val w = image.width
        val h = image.height
        val src = (image.raster.dataBuffer as DataBufferInt).data
        val swap = orientation >= 5
        val outW = if (swap) h else w
        val outH = if (swap) w else h
        val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB)
        val dst = (out.raster.dataBuffer as DataBufferInt).data
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val (sx, sy) = when (orientation) {
                    2 -> (w - 1 - x) to y               // mirrored left-right
                    3 -> (w - 1 - x) to (h - 1 - y)     // upside down
                    4 -> x to (h - 1 - y)               // mirrored top-bottom
                    5 -> y to x                         // transposed
                    6 -> y to (h - 1 - x)               // needs a quarter turn clockwise
                    7 -> (w - 1 - y) to (h - 1 - x)     // transversed
                    else -> (w - 1 - y) to x            // 8: needs a quarter turn anticlockwise
                }
                dst[y * outW + x] = src[sy * w + sx]
            }
        }
        return out
    }

    private fun writeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = JPEG_QUALITY
                // No metadata passed: the file gets only the JFIF header ImageIO always writes.
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    /**
     * The EXIF orientation tag (0x0112) of a JPEG, or 1 (upright) when there is none or the data
     * cannot be read. Walks the JPEG segments up to the image data looking for the APP1 "Exif"
     * segment, then reads the first IFD of the TIFF structure inside it.
     */
    internal fun exifOrientation(b: ByteArray): Int {
        fun u8(i: Int) = b[i].toInt() and 0xFF
        fun u16be(i: Int) = (u8(i) shl 8) or u8(i + 1)
        if (b.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return 1
        var i = 2
        while (i + 4 <= b.size) {
            if (u8(i) != 0xFF) return 1
            val marker = u8(i + 1)
            when {
                marker == 0xFF -> { i += 1; continue }                       // fill byte
                marker == 0x01 || marker in 0xD0..0xD8 -> { i += 2; continue } // no length
                marker == 0xDA || marker == 0xD9 -> return 1                  // image data: no EXIF
            }
            val length = u16be(i + 2)
            val start = i + 4
            val end = i + 2 + length
            if (length < 2 || end > b.size) return 1
            val exifHeader = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) // "Exif\0\0"
            if (marker == 0xE1 && length >= 8 && exifHeader.indices.all { b[start + it] == exifHeader[it] }) {
                return tiffOrientation(b, start + 6, end)
            }
            i = end
        }
        return 1
    }

    private fun tiffOrientation(b: ByteArray, tiff: Int, end: Int): Int {
        if (tiff + 8 > end) return 1
        val little = when {
            b[tiff] == 'I'.code.toByte() && b[tiff + 1] == 'I'.code.toByte() -> true
            b[tiff] == 'M'.code.toByte() && b[tiff + 1] == 'M'.code.toByte() -> false
            else -> return 1
        }
        fun u8(i: Int) = b[i].toInt() and 0xFF
        fun u16(i: Int) = if (little) u8(i) or (u8(i + 1) shl 8) else (u8(i) shl 8) or u8(i + 1)
        fun u32(i: Int): Long = if (little) {
            u16(i).toLong() or (u16(i + 2).toLong() shl 16)
        } else {
            (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        }
        val ifdOffset = u32(tiff + 4)
        if (ifdOffset < 8 || tiff + ifdOffset + 2 > end) return 1
        val ifd = tiff + ifdOffset.toInt()
        val count = u16(ifd)
        for (k in 0 until count) {
            val entry = ifd + 2 + k * 12
            if (entry + 12 > end) return 1
            if (u16(entry) == 0x0112) {
                val value = u16(entry + 8)
                return if (value in 1..8) value else 1
            }
        }
        return 1
    }

    private fun notAnImage(): Nothing = throw PhotoRejectedException("NOT_AN_IMAGE", "Not a JPEG or PNG image")

    private fun unsupported(what: String): Nothing =
        throw PhotoRejectedException("UNSUPPORTED_FORMAT", "$what images are not accepted; upload a JPEG or PNG")
}
