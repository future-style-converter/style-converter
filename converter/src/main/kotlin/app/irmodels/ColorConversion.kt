package app.irmodels

import kotlin.math.*

/**
 * Color space conversion utilities for normalizing colors to sRGB.
 *
 * All conversions target the SRGB type defined in ValueTypes.kt with components in 0.0-1.0 range.
 */
object ColorConversion {

    // ========== HEX ==========

    fun hexToSrgb(hex: String): SRGB {
        val cleaned = hex.removePrefix("#")
        return when (cleaned.length) {
            3 -> SRGB(
                r = cleaned[0].toString().repeat(2).toInt(16) / 255.0,
                g = cleaned[1].toString().repeat(2).toInt(16) / 255.0,
                b = cleaned[2].toString().repeat(2).toInt(16) / 255.0
            )
            4 -> SRGB(
                r = cleaned[0].toString().repeat(2).toInt(16) / 255.0,
                g = cleaned[1].toString().repeat(2).toInt(16) / 255.0,
                b = cleaned[2].toString().repeat(2).toInt(16) / 255.0,
                a = cleaned[3].toString().repeat(2).toInt(16) / 255.0
            )
            6 -> SRGB(
                r = cleaned.substring(0, 2).toInt(16) / 255.0,
                g = cleaned.substring(2, 4).toInt(16) / 255.0,
                b = cleaned.substring(4, 6).toInt(16) / 255.0
            )
            8 -> SRGB(
                r = cleaned.substring(0, 2).toInt(16) / 255.0,
                g = cleaned.substring(2, 4).toInt(16) / 255.0,
                b = cleaned.substring(4, 6).toInt(16) / 255.0,
                a = cleaned.substring(6, 8).toInt(16) / 255.0
            )
            else -> SRGB(0.0, 0.0, 0.0) // Fallback
        }
    }

    // ========== RGB (0-255) ==========

    fun rgb255ToSrgb(r: Int, g: Int, b: Int, a: Double = 1.0): SRGB {
        return SRGB(
            r = r.coerceIn(0, 255) / 255.0,
            g = g.coerceIn(0, 255) / 255.0,
            b = b.coerceIn(0, 255) / 255.0,
            a = a.coerceIn(0.0, 1.0)
        )
    }

    // ========== HSL ==========

    /**
     * Convert HSL to sRGB.
     * @param h Hue in degrees (0-360)
     * @param s Saturation (0-100)
     * @param l Lightness (0-100)
     * @param a Alpha (0-1)
     */
    fun hslToSrgb(h: Double, s: Double, l: Double, a: Double = 1.0): SRGB {
        val hNorm = ((h % 360) + 360) % 360 / 360.0
        val sNorm = s.coerceIn(0.0, 100.0) / 100.0
        val lNorm = l.coerceIn(0.0, 100.0) / 100.0

        if (sNorm == 0.0) {
            return SRGB(lNorm, lNorm, lNorm, a)
        }

        val q = if (lNorm < 0.5) lNorm * (1 + sNorm) else lNorm + sNorm - lNorm * sNorm
        val p = 2 * lNorm - q

        fun hueToRgb(p: Double, q: Double, t: Double): Double {
            var tNorm = t
            if (tNorm < 0) tNorm += 1
            if (tNorm > 1) tNorm -= 1
            return when {
                tNorm < 1.0 / 6 -> p + (q - p) * 6 * tNorm
                tNorm < 1.0 / 2 -> q
                tNorm < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - tNorm) * 6
                else -> p
            }
        }

        return SRGB(
            r = hueToRgb(p, q, hNorm + 1.0 / 3),
            g = hueToRgb(p, q, hNorm),
            b = hueToRgb(p, q, hNorm - 1.0 / 3),
            a = a
        )
    }

    // ========== HWB ==========

    /**
     * Convert HWB to sRGB.
     * @param h Hue in degrees (0-360)
     * @param w Whiteness (0-100)
     * @param b Blackness (0-100)
     * @param a Alpha (0-1)
     */
    fun hwbToSrgb(h: Double, w: Double, b: Double, a: Double = 1.0): SRGB {
        var white = w.coerceIn(0.0, 100.0) / 100.0
        var black = b.coerceIn(0.0, 100.0) / 100.0

        // Normalize if w + b > 1
        if (white + black > 1) {
            val total = white + black
            white /= total
            black /= total
        }

        // Get pure hue from HSL
        val rgb = hslToSrgb(h, 100.0, 50.0, a)

        // Apply whiteness and blackness
        val factor = 1 - white - black
        return SRGB(
            r = rgb.r * factor + white,
            g = rgb.g * factor + white,
            b = rgb.b * factor + white,
            a = a
        )
    }

    // ========== Lab (CIE Lab) ==========

    /**
     * Convert CIE Lab to sRGB via XYZ.
     *
     * Per CSS Color 4 §11 (https://www.w3.org/TR/css-color-4/#lab-to-lch and the
     * §17 sample "Lab_to_XYZ"), CSS `lab()` is CIELAB relative to the **D50** white
     * point. sRGB is a D65 space, so after decoding to XYZ(D50) we chromatically
     * adapt D50→D65 (Bradford) before the XYZ→sRGB matrix. (The old code used D65
     * directly with no adaptation, which drifted every lab()/lch() color slightly.)
     *
     * @param l Lightness (0-100)
     * @param a Green-red axis (-125 to 125)
     * @param b Blue-yellow axis (-125 to 125)
     * @param alpha Alpha (0-1)
     */
    fun labToSrgb(l: Double, a: Double, b: Double, alpha: Double = 1.0): SRGB {
        // CIELAB → XYZ, inverse of the CIE L*a*b* definition (CIE 15 §8.2.1).
        val fy = (l + 16) / 116      // f(Y/Yn)
        val fx = a / 500 + fy        // f(X/Xn)
        val fz = fy - b / 200        // f(Z/Zn)

        val epsilon = 216.0 / 24389  // (6/29)^3 — the CIE "actual intent" threshold
        val kappa = 24389.0 / 27     // (29/3)^3 — the CIE "actual intent" scale (903.3)

        // Undo the cube-root companding piecewise (CIE 15) → X/Xn, Y/Yn, Z/Zn.
        val xr = if (fx.pow(3) > epsilon) fx.pow(3) else (116 * fx - 16) / kappa
        val yr = if (l > kappa * epsilon) ((l + 16) / 116).pow(3) else l / kappa
        val zr = if (fz.pow(3) > epsilon) fz.pow(3) else (116 * fz - 16) / kappa

        // Scale by the D50 reference white (CSS Color 4 uses the D50 chromaticity
        // white [0.3457/0.3585, 1, (1-0.3457-0.3585)/0.3585]).
        val xD50 = xr * D50_X
        val yD50 = yr * D50_Y
        val zD50 = zr * D50_Z

        // Chromatically adapt XYZ from D50 to D65 (Bradford) since sRGB is D65.
        val (xD65, yD65, zD65) = mul3(BRADFORD_D50_TO_D65, xD50, yD50, zD50)

        // XYZ(D65) → linear sRGB → gamma-encoded sRGB.
        return xyzToSrgb(xD65, yD65, zD65, alpha)
    }

    // ========== LCH (CIE LCH) ==========

    /**
     * Convert CIE LCH to sRGB via Lab.
     * @param l Lightness (0-100)
     * @param c Chroma (0-150+)
     * @param h Hue in degrees (0-360)
     * @param alpha Alpha (0-1)
     */
    fun lchToSrgb(l: Double, c: Double, h: Double, alpha: Double = 1.0): SRGB {
        val hRad = h * PI / 180
        val a = c * cos(hRad)
        val b = c * sin(hRad)
        return labToSrgb(l, a, b, alpha)
    }

    // ========== OKLab ==========

    /**
     * Convert OKLab to sRGB.
     * @param l Lightness (canonical 0-1; css-color-4 §9.2 — 100% = 1.0)
     * @param a Green-red axis (-0.4 to 0.4 typically)
     * @param b Blue-yellow axis (-0.4 to 0.4 typically)
     * @param alpha Alpha (0-1)
     */
    fun oklabToSrgb(l: Double, a: Double, b: Double, alpha: Double = 1.0): SRGB {
        // css-color-4 §9.2: ok-space lightness is CANONICALLY 0..1 (100% =
        // 1.0; ColorParser scales the % arm by 0.01 before calling here) and
        // out-of-range L is clamped to that reference range — so
        // `oklab(1.5 0 0)` computes as white, matching Chrome. The previous
        // `>1 → /100` percentage heuristic mis-read such canonical L as an
        // unscaled percentage (1.5 → 0.015, near-black) once the parser
        // started emitting canonical numbers (wave-48 S2 note).
        val lNorm = l.coerceIn(0.0, 1.0)

        // OKLab to linear sRGB
        val l_ = lNorm + 0.3963377774 * a + 0.2158037573 * b
        val m_ = lNorm - 0.1055613458 * a - 0.0638541728 * b
        val s_ = lNorm - 0.0894841775 * a - 1.2914855480 * b

        val l3 = l_.pow(3)
        val m3 = m_.pow(3)
        val s3 = s_.pow(3)

        // Linear sRGB
        val rLin = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        val gLin = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        val bLin = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

        // Linear to sRGB gamma
        return SRGB(
            r = linearToSrgbGamma(rLin),
            g = linearToSrgbGamma(gLin),
            b = linearToSrgbGamma(bLin),
            a = alpha
        )
    }

    // ========== OKLCH ==========

    /**
     * Convert OKLCH to sRGB via OKLab.
     * @param l Lightness (canonical 0-1; css-color-4 §9.2 — 100% = 1.0)
     * @param c Chroma (0-0.4 typically)
     * @param h Hue in degrees (0-360)
     * @param alpha Alpha (0-1)
     */
    fun oklchToSrgb(l: Double, c: Double, h: Double, alpha: Double = 1.0): SRGB {
        // Same css-color-4 §9.2 reference-range clamp as oklabToSrgb (ok-space
        // L is canonically 0..1): applied here TOO so a direct caller of this
        // converter gets the clamp even if the oklab delegation below is ever
        // restructured. coerceIn is idempotent, so double-clamping is safe.
        val lNorm = l.coerceIn(0.0, 1.0)
        // Polar → rectangular (css-color-4 §9.3): a = C·cos(H), b = C·sin(H).
        val hRad = h * PI / 180
        val a = c * cos(hRad)
        val b = c * sin(hRad)
        return oklabToSrgb(lNorm, a, b, alpha)
    }

    // ========== XYZ helpers ==========

    private fun xyzToSrgb(x: Double, y: Double, z: Double, alpha: Double): SRGB {
        // XYZ to linear sRGB (D65)
        val rLin = +3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val gLin = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bLin = +0.0556434 * x - 0.2040259 * y + 1.0572252 * z

        return SRGB(
            r = linearToSrgbGamma(rLin),
            g = linearToSrgbGamma(gLin),
            b = linearToSrgbGamma(bLin),
            a = alpha
        )
    }

    private fun linearToSrgbGamma(c: Double): Double {
        return if (c <= 0.0031308) {
            12.92 * c
        } else {
            1.055 * c.pow(1.0 / 2.4) - 0.055
        }
    }

    // ========== Named Colors ==========

    /**
     * CSS named colors to sRGB lookup table.
     */
    private val namedColors = mapOf(
        "aliceblue" to SRGB(240/255.0, 248/255.0, 255/255.0),
        "antiquewhite" to SRGB(250/255.0, 235/255.0, 215/255.0),
        "aqua" to SRGB(0.0, 1.0, 1.0),
        "aquamarine" to SRGB(127/255.0, 255/255.0, 212/255.0),
        "azure" to SRGB(240/255.0, 255/255.0, 255/255.0),
        "beige" to SRGB(245/255.0, 245/255.0, 220/255.0),
        "bisque" to SRGB(255/255.0, 228/255.0, 196/255.0),
        "black" to SRGB(0.0, 0.0, 0.0),
        "blanchedalmond" to SRGB(255/255.0, 235/255.0, 205/255.0),
        "blue" to SRGB(0.0, 0.0, 1.0),
        "blueviolet" to SRGB(138/255.0, 43/255.0, 226/255.0),
        "brown" to SRGB(165/255.0, 42/255.0, 42/255.0),
        "burlywood" to SRGB(222/255.0, 184/255.0, 135/255.0),
        "cadetblue" to SRGB(95/255.0, 158/255.0, 160/255.0),
        "chartreuse" to SRGB(127/255.0, 255/255.0, 0.0),
        "chocolate" to SRGB(210/255.0, 105/255.0, 30/255.0),
        "coral" to SRGB(255/255.0, 127/255.0, 80/255.0),
        "cornflowerblue" to SRGB(100/255.0, 149/255.0, 237/255.0),
        "cornsilk" to SRGB(255/255.0, 248/255.0, 220/255.0),
        "crimson" to SRGB(220/255.0, 20/255.0, 60/255.0),
        "cyan" to SRGB(0.0, 1.0, 1.0),
        "darkblue" to SRGB(0.0, 0.0, 139/255.0),
        "darkcyan" to SRGB(0.0, 139/255.0, 139/255.0),
        "darkgoldenrod" to SRGB(184/255.0, 134/255.0, 11/255.0),
        "darkgray" to SRGB(169/255.0, 169/255.0, 169/255.0),
        "darkgreen" to SRGB(0.0, 100/255.0, 0.0),
        "darkgrey" to SRGB(169/255.0, 169/255.0, 169/255.0),
        "darkkhaki" to SRGB(189/255.0, 183/255.0, 107/255.0),
        "darkmagenta" to SRGB(139/255.0, 0.0, 139/255.0),
        "darkolivegreen" to SRGB(85/255.0, 107/255.0, 47/255.0),
        "darkorange" to SRGB(255/255.0, 140/255.0, 0.0),
        "darkorchid" to SRGB(153/255.0, 50/255.0, 204/255.0),
        "darkred" to SRGB(139/255.0, 0.0, 0.0),
        "darksalmon" to SRGB(233/255.0, 150/255.0, 122/255.0),
        "darkseagreen" to SRGB(143/255.0, 188/255.0, 143/255.0),
        "darkslateblue" to SRGB(72/255.0, 61/255.0, 139/255.0),
        "darkslategray" to SRGB(47/255.0, 79/255.0, 79/255.0),
        "darkslategrey" to SRGB(47/255.0, 79/255.0, 79/255.0),
        "darkturquoise" to SRGB(0.0, 206/255.0, 209/255.0),
        "darkviolet" to SRGB(148/255.0, 0.0, 211/255.0),
        "deeppink" to SRGB(255/255.0, 20/255.0, 147/255.0),
        "deepskyblue" to SRGB(0.0, 191/255.0, 255/255.0),
        "dimgray" to SRGB(105/255.0, 105/255.0, 105/255.0),
        "dimgrey" to SRGB(105/255.0, 105/255.0, 105/255.0),
        "dodgerblue" to SRGB(30/255.0, 144/255.0, 255/255.0),
        "firebrick" to SRGB(178/255.0, 34/255.0, 34/255.0),
        "floralwhite" to SRGB(255/255.0, 250/255.0, 240/255.0),
        "forestgreen" to SRGB(34/255.0, 139/255.0, 34/255.0),
        "fuchsia" to SRGB(1.0, 0.0, 1.0),
        "gainsboro" to SRGB(220/255.0, 220/255.0, 220/255.0),
        "ghostwhite" to SRGB(248/255.0, 248/255.0, 255/255.0),
        "gold" to SRGB(255/255.0, 215/255.0, 0.0),
        "goldenrod" to SRGB(218/255.0, 165/255.0, 32/255.0),
        "gray" to SRGB(128/255.0, 128/255.0, 128/255.0),
        "green" to SRGB(0.0, 128/255.0, 0.0),
        "greenyellow" to SRGB(173/255.0, 255/255.0, 47/255.0),
        "grey" to SRGB(128/255.0, 128/255.0, 128/255.0),
        "honeydew" to SRGB(240/255.0, 255/255.0, 240/255.0),
        "hotpink" to SRGB(255/255.0, 105/255.0, 180/255.0),
        "indianred" to SRGB(205/255.0, 92/255.0, 92/255.0),
        "indigo" to SRGB(75/255.0, 0.0, 130/255.0),
        "ivory" to SRGB(255/255.0, 255/255.0, 240/255.0),
        "khaki" to SRGB(240/255.0, 230/255.0, 140/255.0),
        "lavender" to SRGB(230/255.0, 230/255.0, 250/255.0),
        "lavenderblush" to SRGB(255/255.0, 240/255.0, 245/255.0),
        "lawngreen" to SRGB(124/255.0, 252/255.0, 0.0),
        "lemonchiffon" to SRGB(255/255.0, 250/255.0, 205/255.0),
        "lightblue" to SRGB(173/255.0, 216/255.0, 230/255.0),
        "lightcoral" to SRGB(240/255.0, 128/255.0, 128/255.0),
        "lightcyan" to SRGB(224/255.0, 255/255.0, 255/255.0),
        "lightgoldenrodyellow" to SRGB(250/255.0, 250/255.0, 210/255.0),
        "lightgray" to SRGB(211/255.0, 211/255.0, 211/255.0),
        "lightgreen" to SRGB(144/255.0, 238/255.0, 144/255.0),
        "lightgrey" to SRGB(211/255.0, 211/255.0, 211/255.0),
        "lightpink" to SRGB(255/255.0, 182/255.0, 193/255.0),
        "lightsalmon" to SRGB(255/255.0, 160/255.0, 122/255.0),
        "lightseagreen" to SRGB(32/255.0, 178/255.0, 170/255.0),
        "lightskyblue" to SRGB(135/255.0, 206/255.0, 250/255.0),
        "lightslategray" to SRGB(119/255.0, 136/255.0, 153/255.0),
        "lightslategrey" to SRGB(119/255.0, 136/255.0, 153/255.0),
        "lightsteelblue" to SRGB(176/255.0, 196/255.0, 222/255.0),
        "lightyellow" to SRGB(255/255.0, 255/255.0, 224/255.0),
        "lime" to SRGB(0.0, 1.0, 0.0),
        "limegreen" to SRGB(50/255.0, 205/255.0, 50/255.0),
        "linen" to SRGB(250/255.0, 240/255.0, 230/255.0),
        "magenta" to SRGB(1.0, 0.0, 1.0),
        "maroon" to SRGB(128/255.0, 0.0, 0.0),
        "mediumaquamarine" to SRGB(102/255.0, 205/255.0, 170/255.0),
        "mediumblue" to SRGB(0.0, 0.0, 205/255.0),
        "mediumorchid" to SRGB(186/255.0, 85/255.0, 211/255.0),
        "mediumpurple" to SRGB(147/255.0, 112/255.0, 219/255.0),
        "mediumseagreen" to SRGB(60/255.0, 179/255.0, 113/255.0),
        "mediumslateblue" to SRGB(123/255.0, 104/255.0, 238/255.0),
        "mediumspringgreen" to SRGB(0.0, 250/255.0, 154/255.0),
        "mediumturquoise" to SRGB(72/255.0, 209/255.0, 204/255.0),
        "mediumvioletred" to SRGB(199/255.0, 21/255.0, 133/255.0),
        "midnightblue" to SRGB(25/255.0, 25/255.0, 112/255.0),
        "mintcream" to SRGB(245/255.0, 255/255.0, 250/255.0),
        "mistyrose" to SRGB(255/255.0, 228/255.0, 225/255.0),
        "moccasin" to SRGB(255/255.0, 228/255.0, 181/255.0),
        "navajowhite" to SRGB(255/255.0, 222/255.0, 173/255.0),
        "navy" to SRGB(0.0, 0.0, 128/255.0),
        "oldlace" to SRGB(253/255.0, 245/255.0, 230/255.0),
        "olive" to SRGB(128/255.0, 128/255.0, 0.0),
        "olivedrab" to SRGB(107/255.0, 142/255.0, 35/255.0),
        "orange" to SRGB(255/255.0, 165/255.0, 0.0),
        "orangered" to SRGB(255/255.0, 69/255.0, 0.0),
        "orchid" to SRGB(218/255.0, 112/255.0, 214/255.0),
        "palegoldenrod" to SRGB(238/255.0, 232/255.0, 170/255.0),
        "palegreen" to SRGB(152/255.0, 251/255.0, 152/255.0),
        "paleturquoise" to SRGB(175/255.0, 238/255.0, 238/255.0),
        "palevioletred" to SRGB(219/255.0, 112/255.0, 147/255.0),
        "papayawhip" to SRGB(255/255.0, 239/255.0, 213/255.0),
        "peachpuff" to SRGB(255/255.0, 218/255.0, 185/255.0),
        "peru" to SRGB(205/255.0, 133/255.0, 63/255.0),
        "pink" to SRGB(255/255.0, 192/255.0, 203/255.0),
        "plum" to SRGB(221/255.0, 160/255.0, 221/255.0),
        "powderblue" to SRGB(176/255.0, 224/255.0, 230/255.0),
        "purple" to SRGB(128/255.0, 0.0, 128/255.0),
        "rebeccapurple" to SRGB(102/255.0, 51/255.0, 153/255.0),
        "red" to SRGB(1.0, 0.0, 0.0),
        "rosybrown" to SRGB(188/255.0, 143/255.0, 143/255.0),
        "royalblue" to SRGB(65/255.0, 105/255.0, 225/255.0),
        "saddlebrown" to SRGB(139/255.0, 69/255.0, 19/255.0),
        "salmon" to SRGB(250/255.0, 128/255.0, 114/255.0),
        "sandybrown" to SRGB(244/255.0, 164/255.0, 96/255.0),
        "seagreen" to SRGB(46/255.0, 139/255.0, 87/255.0),
        "seashell" to SRGB(255/255.0, 245/255.0, 238/255.0),
        "sienna" to SRGB(160/255.0, 82/255.0, 45/255.0),
        "silver" to SRGB(192/255.0, 192/255.0, 192/255.0),
        "skyblue" to SRGB(135/255.0, 206/255.0, 235/255.0),
        "slateblue" to SRGB(106/255.0, 90/255.0, 205/255.0),
        "slategray" to SRGB(112/255.0, 128/255.0, 144/255.0),
        "slategrey" to SRGB(112/255.0, 128/255.0, 144/255.0),
        "snow" to SRGB(255/255.0, 250/255.0, 250/255.0),
        "springgreen" to SRGB(0.0, 255/255.0, 127/255.0),
        "steelblue" to SRGB(70/255.0, 130/255.0, 180/255.0),
        "tan" to SRGB(210/255.0, 180/255.0, 140/255.0),
        "teal" to SRGB(0.0, 128/255.0, 128/255.0),
        "thistle" to SRGB(216/255.0, 191/255.0, 216/255.0),
        "tomato" to SRGB(255/255.0, 99/255.0, 71/255.0),
        "turquoise" to SRGB(64/255.0, 224/255.0, 208/255.0),
        "violet" to SRGB(238/255.0, 130/255.0, 238/255.0),
        "wheat" to SRGB(245/255.0, 222/255.0, 179/255.0),
        "white" to SRGB(1.0, 1.0, 1.0),
        "whitesmoke" to SRGB(245/255.0, 245/255.0, 245/255.0),
        "yellow" to SRGB(1.0, 1.0, 0.0),
        "yellowgreen" to SRGB(154/255.0, 205/255.0, 50/255.0),
        // Special
        "transparent" to SRGB(0.0, 0.0, 0.0, 0.0)
    )

    fun namedColorToSrgb(name: String): SRGB? {
        return namedColors[name.lowercase()]
    }

    private fun srgbGammaToLinear(c: Double): Double {
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    // ========== Predefined color() color spaces ==========
    //
    // Every predefined RGB color space converts the same way: decode its own
    // transfer function to linear light, apply its primaries→XYZ(D65) matrix, then
    // reuse xyzToSrgb (XYZ(D65)→linear sRGB→gamma). D50-based spaces (lab, xyz-d50)
    // are Bradford-adapted to D65 first. All matrices below are the exact
    // first-principles values from the CSS Color 4 sample code (§17,
    // https://www.w3.org/TR/css-color-4/#color-conversion-code), which agree with
    // Bruce Lindbloom's tables. Callers clamp the result into gamut (simple clip).

    /** D50 reference white (CSS Color 4 D50 chromaticity: [0.3457/0.3585, 1, …]). */
    private const val D50_X = 0.3457 / 0.3585
    private const val D50_Y = 1.0
    private const val D50_Z = (1.0 - 0.3457 - 0.3585) / 0.3585

    /** Bradford chromatic-adaptation matrix D50→D65 (CSS Color 4 sample code). */
    private val BRADFORD_D50_TO_D65 = arrayOf(
        doubleArrayOf( 0.955473421488075,    -0.02309845494876471,  0.06325924320057072),
        doubleArrayOf(-0.0283697093338637,    1.0099953980813041,   0.021041441191917323),
        doubleArrayOf( 0.012314014864481998, -0.020507649298898964, 1.330365926242124)
    )

    /** Linear Display-P3 → XYZ(D65) (CSS Color 4 lin_P3_to_XYZ). */
    private val P3_TO_XYZ = arrayOf(
        doubleArrayOf(0.4865709486482162,  0.26566769316909306,  0.19821728523436247),
        doubleArrayOf(0.2289745640697488,  0.6917385218365064,   0.079286914093745),
        doubleArrayOf(0.0000000000000000,  0.04511338185890264,  1.043944368900976)
    )

    /** Linear a98-rgb (Adobe RGB 1998) → XYZ(D65) (CSS Color 4 lin_a98rgb_to_XYZ). */
    private val A98_TO_XYZ = arrayOf(
        doubleArrayOf(0.5766690429101305,  0.1855582379065463,   0.1882286462349947),
        doubleArrayOf(0.29734497525053605, 0.6273635662554661,   0.07529145849399788),
        doubleArrayOf(0.02703136138641234, 0.07068885253582723,  0.9913375368376388)
    )

    /** Linear rec2020 → XYZ(D65) (CSS Color 4 lin_2020_to_XYZ). */
    private val REC2020_TO_XYZ = arrayOf(
        doubleArrayOf(0.6369580483012914,  0.14461690358620832,  0.16888097516417210),
        doubleArrayOf(0.2627002120112671,  0.6779980715188708,   0.05930171646986196),
        doubleArrayOf(0.0000000000000000,  0.028072693049087428, 1.060985057710791)
    )

    /** Multiply a 3×3 matrix by the column vector (x,y,z). */
    private fun mul3(m: Array<DoubleArray>, x: Double, y: Double, z: Double): Triple<Double, Double, Double> =
        Triple(
            m[0][0] * x + m[0][1] * y + m[0][2] * z,
            m[1][0] * x + m[1][1] * y + m[1][2] * z,
            m[2][0] * x + m[2][1] * y + m[2][2] * z
        )

    /**
     * color(srgb-linear r g b) — linear-light sRGB. Only the transfer function
     * differs from plain sRGB, so we just gamma-encode each channel (no matrix).
     */
    fun srgbLinearToSrgb(r: Double, g: Double, b: Double, alpha: Double = 1.0): SRGB =
        SRGB(linearToSrgbGamma(r), linearToSrgbGamma(g), linearToSrgbGamma(b), alpha)

    /**
     * color(display-p3 r g b). Display-P3 shares the sRGB transfer function, so we
     * decode to linear light, go P3→XYZ(D65), then XYZ→sRGB (both D65 — no adapt).
     */
    fun displayP3ToSrgb(r: Double, g: Double, b: Double, alpha: Double = 1.0): SRGB {
        val (x, y, z) = mul3(P3_TO_XYZ, srgbGammaToLinear(r), srgbGammaToLinear(g), srgbGammaToLinear(b))
        return xyzToSrgb(x, y, z, alpha)
    }

    /**
     * color(a98-rgb r g b). Adobe RGB (1998): decode the 2.19921875 gamma to linear
     * light, apply the Adobe primaries→XYZ(D65) matrix, then XYZ→sRGB (both D65).
     */
    fun a98RgbToSrgb(r: Double, g: Double, b: Double, alpha: Double = 1.0): SRGB {
        val (x, y, z) = mul3(A98_TO_XYZ, a98Linear(r), a98Linear(g), a98Linear(b))
        return xyzToSrgb(x, y, z, alpha)
    }

    /** a98-rgb transfer decode: linear = sign(v)·|v|^(563/256) (CSS Color 4 lin_a98rgb). */
    private fun a98Linear(v: Double): Double {
        val sign = if (v < 0) -1.0 else 1.0
        return sign * abs(v).pow(563.0 / 256.0)  // 563/256 = 2.19921875 (Adobe RGB gamma)
    }

    /**
     * color(rec2020 r g b). ITU-R BT.2020: decode the BT.2020 transfer function to
     * linear light, apply rec2020→XYZ(D65), then XYZ→sRGB (both D65).
     */
    fun rec2020ToSrgb(r: Double, g: Double, b: Double, alpha: Double = 1.0): SRGB {
        val (x, y, z) = mul3(REC2020_TO_XYZ, rec2020Linear(r), rec2020Linear(g), rec2020Linear(b))
        return xyzToSrgb(x, y, z, alpha)
    }

    /** BT.2020 transfer decode (CSS Color 4 lin_2020): α=1.09929682680944, β=0.018053968510807. */
    private fun rec2020Linear(v: Double): Double {
        val a = 1.09929682680944   // BT.2020 α
        val b = 0.018053968510807  // BT.2020 β
        val sign = if (v < 0) -1.0 else 1.0
        val mag = abs(v)
        // Linear segment below β·4.5, power segment above (encode used 4.5 slope / 0.45 exponent).
        return if (mag < b * 4.5) sign * (mag / 4.5) else sign * ((mag + a - 1) / a).pow(1.0 / 0.45)
    }

    /** color(xyz r g b) / color(xyz-d65 …) — components already XYZ(D65). */
    fun xyzD65ToSrgb(x: Double, y: Double, z: Double, alpha: Double = 1.0): SRGB =
        xyzToSrgb(x, y, z, alpha)

    /** color(xyz-d50 x y z) — Bradford-adapt D50→D65, then XYZ→sRGB. */
    fun xyzD50ToSrgb(x: Double, y: Double, z: Double, alpha: Double = 1.0): SRGB {
        val (xd, yd, zd) = mul3(BRADFORD_D50_TO_D65, x, y, z)
        return xyzToSrgb(xd, yd, zd, alpha)
    }
}
