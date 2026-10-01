package rw.inama.app.domain.image

import rw.inama.app.domain.ai.ImageFeatures
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device photo measurements: colour ratios (green / brown / yellow / pale) and quality
 * (brightness, sharpness). Runs on a downscaled copy of the photo in a few milliseconds, before
 * anything is uploaded — so a blurry or dark photo is caught while the farmer is still in the field.
 *
 * Pure Kotlin over ARGB pixels; the Android layer only decodes the bitmap (core.image.ImageTools).
 * Colour ratios are measured in the central 60% of the frame, where the camera guide asks the farmer
 * to place the sick leaf, so soil and sky at the edges don't count. The sharpness score is the RMS
 * of a 4-neighbour Laplacian on luminance, mapped to 0..1 — a standard, lightweight blur measure. Thresholds live in the knowledge base (engine.quality).
 */
object PhotoAnalyzer {

    fun analyze(argb: IntArray, width: Int, height: Int): ImageFeatures {
        require(width > 2 && height > 2 && argb.size >= width * height) { "image too small" }
        val n = width * height
        val luma = DoubleArray(n)
        val x0 = width / 5
        val x1 = width - width / 5
        val y0 = height / 5
        val y1 = height - height / 5
        var centre = 0
        var green = 0
        var brown = 0
        var yellow = 0
        var white = 0
        var lumaSum = 0.0
        val hsv = DoubleArray(3)
        for (i in 0 until n) {
            val p = argb[i]
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            luma[i] = y
            lumaSum += y
            val px = i % width
            val py = i / width
            if (px < x0 || px >= x1 || py < y0 || py >= y1) continue
            centre++
            toHsv(r, g, b, hsv)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]
            when {
                s < 0.15 && v > 0.8 -> white++
                h in 70.0..170.0 && s > 0.2 && v > 0.18 -> green++
                h in 45.0..70.0 && s > 0.35 && v > 0.5 -> yellow++
                (h < 45.0 || h > 340.0) && s > 0.3 && v in 0.12..0.75 -> brown++
            }
        }
        // Sharpness: RMS of the Laplacian over interior pixels.
        var sq = 0.0
        var count = 0
        for (yy in 1 until height - 1) {
            for (xx in 1 until width - 1) {
                val i = yy * width + xx
                val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
                sq += lap * lap
                count++
            }
        }
        val rms = sqrt(sq / max(1, count))
        val area = max(1, centre).toDouble()
        return ImageFeatures(
            brownRatio = brown / area,
            yellowRatio = yellow / area,
            whiteRatio = white / area,
            greenRatio = green / area,
            brightness = lumaSum / n,
            sharpness = sharpnessScore(rms),
        )
    }

    /** Maps Laplacian RMS to 0..1; ~0.12 is where photos start to look soft on a phone screen. */
    fun sharpnessScore(rms: Double): Double = min(1.0, rms / 0.08)

    private fun toHsv(r: Double, g: Double, b: Double, out: DoubleArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = when {
            d == 0.0 -> 0.0
            mx == r -> 60 * (((g - b) / d) % 6)
            mx == g -> 60 * (((b - r) / d) + 2)
            else -> 60 * (((r - g) / d) + 4)
        }
        if (h < 0) h += 360.0
        out[0] = h
        out[1] = if (mx == 0.0) 0.0 else d / mx
        out[2] = mx
    }
}
