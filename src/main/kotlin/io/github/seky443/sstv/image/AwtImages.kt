@file:JvmName("AwtImages")

package io.github.seky443.sstv.image

import io.github.seky443.sstv.SstvEncoder
import io.github.seky443.sstv.SstvMode
import io.github.seky443.sstv.SstvSignal
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Desktop conveniences for turning an ordinary image file into something [SstvEncoder] can send.
 *
 * These sit in their own file because they use `java.awt` and `javax.imageio`, which exist on the
 * JVM but not on Android. The core encoder stays free of them, so an Android consumer can simply
 * never reference this file and supply its own pixels instead.
 */

/** How an image that is not already the mode's aspect ratio gets fitted to the frame. */
public enum class Scaling {
    /** Scale to fit inside the frame and pad the remainder with the background colour. */
    FIT,

    /** Scale to cover the frame and crop the overflow, centred. */
    FILL,

    /** Stretch to the frame's exact dimensions, distorting the aspect ratio. */
    STRETCH
}

/** Opaque black, the default padding for [Scaling.FIT]. */
public const val DEFAULT_BACKGROUND: Int = 0xFF000000.toInt()

/**
 * Resamples this image to [mode]'s frame size and returns its pixels as row-major `0xAARRGGBB`
 * ints, which is exactly what [SstvEncoder.encode] takes.
 *
 * @param background colour used to pad the frame under [Scaling.FIT]. Ignored otherwise.
 */
@JvmOverloads
public fun BufferedImage.toSstvPixels(
    mode: SstvMode = SstvMode.MARTIN_M1,
    scaling: Scaling = Scaling.FIT,
    background: Int = DEFAULT_BACKGROUND
): IntArray {
    val frame = BufferedImage(mode.width, mode.height, BufferedImage.TYPE_INT_RGB)
    val g = frame.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        g.color = Color(background, true)
        g.fillRect(0, 0, mode.width, mode.height)

        when (scaling) {
            Scaling.STRETCH -> g.drawImage(this, 0, 0, mode.width, mode.height, null)

            Scaling.FIT -> {
                val scale = min(mode.width / width.toDouble(), mode.height / height.toDouble())
                val w = (width * scale).roundToInt()
                val h = (height * scale).roundToInt()
                g.drawImage(this, (mode.width - w) / 2, (mode.height - h) / 2, w, h, null)
            }

            Scaling.FILL -> {
                val scale = max(mode.width / width.toDouble(), mode.height / height.toDouble())
                val w = (width * scale).roundToInt()
                val h = (height * scale).roundToInt()
                g.drawImage(this, (mode.width - w) / 2, (mode.height - h) / 2, w, h, null)
            }
        }
    } finally {
        g.dispose()
    }

    return frame.getRGB(0, 0, mode.width, mode.height, null, 0, mode.width)
}

/** Encodes [image] directly, resampling it to the frame size first. */
@JvmOverloads
public fun SstvEncoder.encode(
    image: BufferedImage,
    mode: SstvMode = SstvMode.MARTIN_M1,
    scaling: Scaling = Scaling.FIT,
    background: Int = DEFAULT_BACKGROUND
): SstvSignal = encode(image.toSstvPixels(mode, scaling, background), mode)

/**
 * Reads an image file (PNG, JPEG, GIF, BMP — whatever ImageIO supports) and resamples it to
 * [mode]'s frame size.
 *
 * @throws IllegalArgumentException if the file holds no image ImageIO can decode.
 */
@JvmOverloads
public fun readSstvPixels(
    file: File,
    mode: SstvMode = SstvMode.MARTIN_M1,
    scaling: Scaling = Scaling.FIT,
    background: Int = DEFAULT_BACKGROUND
): IntArray {
    val image = requireNotNull(ImageIO.read(file)) { "Not a readable image: $file" }
    return image.toSstvPixels(mode, scaling, background)
}

/** As [readSstvPixels], reading from an already-open [stream]. The stream is not closed. */
@JvmOverloads
public fun readSstvPixels(
    stream: InputStream,
    mode: SstvMode = SstvMode.MARTIN_M1,
    scaling: Scaling = Scaling.FIT,
    background: Int = DEFAULT_BACKGROUND
): IntArray {
    val image = requireNotNull(ImageIO.read(stream)) { "Stream held no readable image" }
    return image.toSstvPixels(mode, scaling, background)
}