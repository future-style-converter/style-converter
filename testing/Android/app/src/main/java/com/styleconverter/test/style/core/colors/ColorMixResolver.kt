package com.styleconverter.test.style.core.colors

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Resolves CSS color-mix() function at runtime.
 *
 * ## CSS Syntax
 * ```css
 * color-mix(in srgb, red 50%, blue 50%)
 * color-mix(in oklch, #ff0000, #0000ff)
 * color-mix(in srgb, red, blue 25%)  /* 75% red, 25% blue */
 * color-mix(in hsl, hsl(120 100% 50%), hsl(240 100% 50%))
 * ```
 *
 * ## Supported Color Spaces
 * - srgb (default): Standard RGB interpolation
 * - srgb-linear: Linear RGB (gamma-corrected)
 * - lab: CIELAB color space
 * - oklch: OKLab cylindrical (perceptually uniform)
 * - oklab: OKLab color space
 * - hsl: Hue, Saturation, Lightness
 * - hwb: Hue, Whiteness, Blackness
 *
 * ## Usage
 * ```kotlin
 * val mixed = ColorMixResolver.resolve(
 *     expression = "color-mix(in srgb, red 50%, blue 50%)"
 * )
 * // Returns Color(0.5f, 0f, 0.5f) - purple
 *
 * val themed = ColorMixResolver.mix(
 *     color1 = MaterialTheme.colorScheme.primary,
 *     color2 = MaterialTheme.colorScheme.secondary,
 *     percentage1 = 70f,
 *     colorSpace = ColorMixSpace.OKLCH
 * )
 * ```
 */
object ColorMixResolver {

    // Regex to parse color-mix() expression
    private val COLOR_MIX_REGEX = Regex(
        """color-mix\(\s*in\s+(\w+(?:-\w+)?)\s*,\s*(.+?)\s*,\s*(.+?)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // Regex to parse color with optional percentage
    private val COLOR_PERCENTAGE_REGEX = Regex(
        """(.+?)\s+(\d+(?:\.\d+)?)\s*%?$"""
    )

    /**
     * Resolve a color-mix() expression to a Color.
     *
     * @param expression The color-mix() CSS expression
     * @return Resolved Color, or null if parsing fails
     */
    fun resolve(expression: String): Color? {
        val match = COLOR_MIX_REGEX.find(expression.trim()) ?: return null

        val colorSpace = match.groupValues[1].lowercase()
        val colorExpr1 = match.groupValues[2].trim()
        val colorExpr2 = match.groupValues[3].trim()

        // Parse colors and percentages
        val (color1, percent1) = parseColorWithPercentage(colorExpr1)
        val (color2, percent2) = parseColorWithPercentage(colorExpr2)

        if (color1 == null || color2 == null) return null

        // Calculate effective percentages
        val (effectiveP1, effectiveP2) = normalizePercentages(percent1, percent2)

        // Get the color space for mixing
        val mixSpace = ColorMixSpace.fromString(colorSpace)

        return mix(color1, color2, effectiveP1, mixSpace)
    }

    /**
     * Mix two colors with specified percentages and color space.
     *
     * @param color1 First color
     * @param color2 Second color
     * @param percentage1 Percentage of first color (0-100)
     * @param colorSpace Color space for interpolation
     * @return Mixed color
     */
    fun mix(
        color1: Color,
        color2: Color,
        percentage1: Float,
        colorSpace: ColorMixSpace = ColorMixSpace.SRGB
    ): Color {
        val p1 = (percentage1 / 100f).coerceIn(0f, 1f)
        val p2 = 1f - p1

        return when (colorSpace) {
            ColorMixSpace.SRGB -> mixInSrgb(color1, color2, p1, p2)
            ColorMixSpace.SRGB_LINEAR -> mixInSrgbLinear(color1, color2, p1, p2)
            ColorMixSpace.HSL -> mixInHsl(color1, color2, p1, p2)
            ColorMixSpace.HWB -> mixInHwb(color1, color2, p1, p2)
            ColorMixSpace.LAB -> mixInLab(color1, color2, p1, p2)
            ColorMixSpace.OKLAB -> mixInOklab(color1, color2, p1, p2)
            ColorMixSpace.OKLCH -> mixInOklch(color1, color2, p1, p2)
            ColorMixSpace.LCH -> mixInLch(color1, color2, p1, p2)
        }
    }

    /**
     * Mix colors in sRGB color space.
     */
    private fun mixInSrgb(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        return Color(
            red = c1.red * p1 + c2.red * p2,
            green = c1.green * p1 + c2.green * p2,
            blue = c1.blue * p1 + c2.blue * p2,
            alpha = c1.alpha * p1 + c2.alpha * p2
        )
    }

    /**
     * Mix colors in linear sRGB (gamma-corrected).
     */
    private fun mixInSrgbLinear(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        // Convert to linear
        fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        fun fromLinear(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

        val r1 = toLinear(c1.red)
        val g1 = toLinear(c1.green)
        val b1 = toLinear(c1.blue)

        val r2 = toLinear(c2.red)
        val g2 = toLinear(c2.green)
        val b2 = toLinear(c2.blue)

        return Color(
            red = fromLinear(r1 * p1 + r2 * p2).coerceIn(0f, 1f),
            green = fromLinear(g1 * p1 + g2 * p2).coerceIn(0f, 1f),
            blue = fromLinear(b1 * p1 + b2 * p2).coerceIn(0f, 1f),
            alpha = c1.alpha * p1 + c2.alpha * p2
        )
    }

    /**
     * Mix colors in HSL color space.
     */
    private fun mixInHsl(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        val hsl1 = rgbToHsl(c1.red, c1.green, c1.blue)
        val hsl2 = rgbToHsl(c2.red, c2.green, c2.blue)

        // Interpolate hue on the shorter path
        var h1 = hsl1[0]
        var h2 = hsl2[0]
        if (kotlin.math.abs(h1 - h2) > 180f) {
            if (h1 > h2) h2 += 360f else h1 += 360f
        }

        val h = ((h1 * p1 + h2 * p2) % 360f + 360f) % 360f
        val s = hsl1[1] * p1 + hsl2[1] * p2
        val l = hsl1[2] * p1 + hsl2[2] * p2

        return hslToColor(h, s, l, c1.alpha * p1 + c2.alpha * p2)
    }

    /**
     * Mix colors in HWB color space.
     */
    private fun mixInHwb(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        // HWB is similar to HSL but uses whiteness and blackness
        val hwb1 = rgbToHwb(c1.red, c1.green, c1.blue)
        val hwb2 = rgbToHwb(c2.red, c2.green, c2.blue)

        var h1 = hwb1[0]
        var h2 = hwb2[0]
        if (kotlin.math.abs(h1 - h2) > 180f) {
            if (h1 > h2) h2 += 360f else h1 += 360f
        }

        val h = ((h1 * p1 + h2 * p2) % 360f + 360f) % 360f
        val w = hwb1[1] * p1 + hwb2[1] * p2
        val b = hwb1[2] * p1 + hwb2[2] * p2

        return hwbToColor(h, w, b, c1.alpha * p1 + c2.alpha * p2)
    }

    /**
     * Mix colors in CIELAB color space.
     */
    private fun mixInLab(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        val lab1 = rgbToLab(c1.red, c1.green, c1.blue)
        val lab2 = rgbToLab(c2.red, c2.green, c2.blue)

        val l = lab1[0] * p1 + lab2[0] * p2
        val a = lab1[1] * p1 + lab2[1] * p2
        val b = lab1[2] * p1 + lab2[2] * p2

        return labToColor(l, a, b, c1.alpha * p1 + c2.alpha * p2)
    }

    /**
     * Mix colors in OKLab color space.
     */
    private fun mixInOklab(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        val oklab1 = rgbToOklab(c1.red, c1.green, c1.blue)
        val oklab2 = rgbToOklab(c2.red, c2.green, c2.blue)

        val l = oklab1[0] * p1 + oklab2[0] * p2
        val a = oklab1[1] * p1 + oklab2[1] * p2
        val b = oklab1[2] * p1 + oklab2[2] * p2

        return oklabToColor(l, a, b, c1.alpha * p1 + c2.alpha * p2)
    }

    /**
     * Mix colors in OKLCH color space (perceptually uniform).
     */
    private fun mixInOklch(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        val oklab1 = rgbToOklab(c1.red, c1.green, c1.blue)
        val oklab2 = rgbToOklab(c2.red, c2.green, c2.blue)

        // Convert to LCH
        val oklch1 = oklabToOklch(oklab1)
        val oklch2 = oklabToOklch(oklab2)

        // Interpolate hue on shorter path
        var h1 = oklch1[2]
        var h2 = oklch2[2]
        if (kotlin.math.abs(h1 - h2) > 180f) {
            if (h1 > h2) h2 += 360f else h1 += 360f
        }

        val l = oklch1[0] * p1 + oklch2[0] * p2
        val c = oklch1[1] * p1 + oklch2[1] * p2
        val h = ((h1 * p1 + h2 * p2) % 360f + 360f) % 360f

        return oklchToColor(l, c, h, c1.alpha * p1 + c2.alpha * p2)
    }

    /**
     * Mix colors in LCH color space.
     */
    private fun mixInLch(c1: Color, c2: Color, p1: Float, p2: Float): Color {
        val lab1 = rgbToLab(c1.red, c1.green, c1.blue)
        val lab2 = rgbToLab(c2.red, c2.green, c2.blue)

        // Convert to LCH
        val lch1 = labToLch(lab1)
        val lch2 = labToLch(lab2)

        var h1 = lch1[2]
        var h2 = lch2[2]
        if (kotlin.math.abs(h1 - h2) > 180f) {
            if (h1 > h2) h2 += 360f else h1 += 360f
        }

        val l = lch1[0] * p1 + lch2[0] * p2
        val c = lch1[1] * p1 + lch2[1] * p2
        val h = ((h1 * p1 + h2 * p2) % 360f + 360f) % 360f

        return lchToColor(l, c, h, c1.alpha * p1 + c2.alpha * p2)
    }

    // ========== COLOR SPACE CONVERSION HELPERS ==========

    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f

        if (max == min) return floatArrayOf(0f, 0f, l)

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }

        return floatArrayOf(h, s, l)
    }

    private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float): Color {
        if (s == 0f) return Color(l, l, l, alpha)

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        fun hueToRgb(t: Float): Float {
            var t2 = t
            if (t2 < 0f) t2 += 1f
            if (t2 > 1f) t2 -= 1f
            return when {
                t2 < 1f / 6f -> p + (q - p) * 6f * t2
                t2 < 1f / 2f -> q
                t2 < 2f / 3f -> p + (q - p) * (2f / 3f - t2) * 6f
                else -> p
            }
        }

        return Color(
            red = hueToRgb(h / 360f + 1f / 3f),
            green = hueToRgb(h / 360f),
            blue = hueToRgb(h / 360f - 1f / 3f),
            alpha = alpha
        )
    }

    private fun rgbToHwb(r: Float, g: Float, b: Float): FloatArray {
        val hsl = rgbToHsl(r, g, b)
        val w = minOf(r, g, b)
        val bk = 1f - maxOf(r, g, b)
        return floatArrayOf(hsl[0], w, bk)
    }

    private fun hwbToColor(h: Float, w: Float, bk: Float, alpha: Float): Color {
        if (w + bk >= 1f) {
            val gray = w / (w + bk)
            return Color(gray, gray, gray, alpha)
        }

        val rgb = hslToColor(h, 1f, 0.5f, 1f)
        val factor = 1f - w - bk
        return Color(
            red = rgb.red * factor + w,
            green = rgb.green * factor + w,
            blue = rgb.blue * factor + w,
            alpha = alpha
        )
    }

    private fun rgbToLab(r: Float, g: Float, b: Float): FloatArray {
        // sRGB to XYZ
        fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

        val lr = toLinear(r)
        val lg = toLinear(g)
        val lb = toLinear(b)

        val x = (0.4124564f * lr + 0.3575761f * lg + 0.1804375f * lb) / 0.95047f
        val y = (0.2126729f * lr + 0.7151522f * lg + 0.0721750f * lb)
        val z = (0.0193339f * lr + 0.1191920f * lg + 0.9503041f * lb) / 1.08883f

        fun f(t: Float): Float {
            val delta = 6f / 29f
            return if (t > delta * delta * delta) t.pow(1f / 3f) else t / (3f * delta * delta) + 4f / 29f
        }

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        return floatArrayOf(
            116f * fy - 16f,
            500f * (fx - fy),
            200f * (fy - fz)
        )
    }

    private fun labToColor(l: Float, a: Float, b: Float, alpha: Float): Color {
        fun fInv(t: Float): Float {
            val delta = 6f / 29f
            return if (t > delta) t * t * t else 3f * delta * delta * (t - 4f / 29f)
        }

        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - b / 200f

        val x = fInv(fx) * 0.95047f
        val y = fInv(fy)
        val z = fInv(fz) * 1.08883f

        val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
        val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
        val blue = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z

        fun fromLinear(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

        return Color(
            red = fromLinear(r).coerceIn(0f, 1f),
            green = fromLinear(g).coerceIn(0f, 1f),
            blue = fromLinear(blue).coerceIn(0f, 1f),
            alpha = alpha
        )
    }

    private fun labToLch(lab: FloatArray): FloatArray {
        val c = kotlin.math.sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        val h = (kotlin.math.atan2(lab[2], lab[1]) * 180f / kotlin.math.PI.toFloat() + 360f) % 360f
        return floatArrayOf(lab[0], c, h)
    }

    private fun lchToColor(l: Float, c: Float, h: Float, alpha: Float): Color {
        val hRad = h * kotlin.math.PI.toFloat() / 180f
        val a = c * kotlin.math.cos(hRad)
        val b = c * kotlin.math.sin(hRad)
        return labToColor(l, a, b, alpha)
    }

    private fun rgbToOklab(r: Float, g: Float, b: Float): FloatArray {
        fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

        val lr = toLinear(r)
        val lg = toLinear(g)
        val lb = toLinear(b)

        val l_ = (0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb).pow(1f / 3f)
        val m_ = (0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb).pow(1f / 3f)
        val s_ = (0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb).pow(1f / 3f)

        return floatArrayOf(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        )
    }

    private fun oklabToColor(l: Float, a: Float, b: Float, alpha: Float): Color {
        val l_ = l + 0.3963377774f * a + 0.2158037573f * b
        val m_ = l - 0.1055613458f * a - 0.0638541728f * b
        val s_ = l - 0.0894841775f * a - 1.2914855480f * b

        val l3 = l_ * l_ * l_
        val m3 = m_ * m_ * m_
        val s3 = s_ * s_ * s_

        val lr = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val lg = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val lb = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3

        fun fromLinear(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

        return Color(
            red = fromLinear(lr).coerceIn(0f, 1f),
            green = fromLinear(lg).coerceIn(0f, 1f),
            blue = fromLinear(lb).coerceIn(0f, 1f),
            alpha = alpha
        )
    }

    private fun oklabToOklch(oklab: FloatArray): FloatArray {
        val c = kotlin.math.sqrt(oklab[1] * oklab[1] + oklab[2] * oklab[2])
        val h = (kotlin.math.atan2(oklab[2], oklab[1]) * 180f / kotlin.math.PI.toFloat() + 360f) % 360f
        return floatArrayOf(oklab[0], c, h)
    }

    private fun oklchToColor(l: Float, c: Float, h: Float, alpha: Float): Color {
        val hRad = h * kotlin.math.PI.toFloat() / 180f
        val a = c * kotlin.math.cos(hRad)
        val b = c * kotlin.math.sin(hRad)
        return oklabToColor(l, a, b, alpha)
    }

    // ========== PARSING HELPERS ==========

    private fun parseColorWithPercentage(expr: String): Pair<Color?, Float?> {
        val match = COLOR_PERCENTAGE_REGEX.find(expr)
        return if (match != null) {
            val colorStr = match.groupValues[1].trim()
            val percentage = match.groupValues[2].toFloatOrNull()
            Pair(parseColorString(colorStr), percentage)
        } else {
            Pair(parseColorString(expr.trim()), null)
        }
    }

    /**
     * Parse a CSS color string.
     */
    private fun parseColorString(value: String): Color? {
        val trimmed = value.trim().lowercase()

        // Check for hex color
        if (trimmed.startsWith("#")) {
            return parseHexColor(trimmed.substring(1))
        }

        // Check for rgb/rgba
        val rgbMatch = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+))?\s*\)""")
            .find(trimmed)
        if (rgbMatch != null) {
            val r = rgbMatch.groupValues[1].toIntOrNull() ?: return null
            val g = rgbMatch.groupValues[2].toIntOrNull() ?: return null
            val b = rgbMatch.groupValues[3].toIntOrNull() ?: return null
            val a = rgbMatch.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1f
            return Color(r, g, b, (a * 255).toInt())
        }

        // Check for named colors
        return NAMED_COLORS[trimmed]
    }

    private fun parseHexColor(hex: String): Color? {
        return when (hex.length) {
            3 -> {
                val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            4 -> {
                val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                val a = hex[3].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            6 -> {
                val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            8 -> {
                val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            else -> null
        }
    }

    private val NAMED_COLORS = mapOf(
        "red" to Color.Red,
        "green" to Color.Green,
        "blue" to Color.Blue,
        "white" to Color.White,
        "black" to Color.Black,
        "gray" to Color.Gray,
        "grey" to Color.Gray,
        "yellow" to Color.Yellow,
        "cyan" to Color.Cyan,
        "magenta" to Color.Magenta,
        "transparent" to Color.Transparent,
        // Common web colors
        "orange" to Color(0xFFFFA500),
        "purple" to Color(0xFF800080),
        "pink" to Color(0xFFFFC0CB),
        "brown" to Color(0xFFA52A2A),
        "lime" to Color(0xFF00FF00),
        "navy" to Color(0xFF000080),
        "teal" to Color(0xFF008080),
        "olive" to Color(0xFF808000),
        "maroon" to Color(0xFF800000),
        "aqua" to Color(0xFF00FFFF),
        "fuchsia" to Color(0xFFFF00FF),
        "silver" to Color(0xFFC0C0C0)
    )

    private fun normalizePercentages(p1: Float?, p2: Float?): Pair<Float, Float> {
        return when {
            p1 != null && p2 != null -> {
                val total = p1 + p2
                if (total > 0) Pair(p1 / total * 100f, p2 / total * 100f)
                else Pair(50f, 50f)
            }
            p1 != null -> Pair(p1, 100f - p1)
            p2 != null -> Pair(100f - p2, p2)
            else -> Pair(50f, 50f)
        }
    }

    /**
     * Color spaces supported for mixing.
     */
    enum class ColorMixSpace {
        SRGB,
        SRGB_LINEAR,
        HSL,
        HWB,
        LAB,
        LCH,
        OKLAB,
        OKLCH;

        companion object {
            fun fromString(s: String): ColorMixSpace = when (s.lowercase()) {
                "srgb" -> SRGB
                "srgb-linear" -> SRGB_LINEAR
                "hsl" -> HSL
                "hwb" -> HWB
                "lab" -> LAB
                "lch" -> LCH
                "oklab" -> OKLAB
                "oklch" -> OKLCH
                else -> SRGB
            }
        }
    }
}
