package app.parsing.css.properties.longhands.background

// Shared gradient value micro-grammars — split from
// BackgroundImagePropertyParser per the ≤200-line file rule when A-RC4
// (double-position stops) and A-RC8 (length centers) landed. One owner
// for the pieces every gradient flavour shares: color stops, `at
// <position>` clauses, and `to <side>` direction keywords. MaskImage
// inherits all of this through the parseImage delegation.
import app.irmodels.IRAngle
import app.irmodels.IRLengthPercentage
import app.irmodels.IRPercentage
import app.irmodels.properties.background.BackgroundImageProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

internal object GradientValueParsers {

    // One comma-segment → 0..2 color stops (css-images-4 §3.4.3):
    //   <color>                → 1 stop, auto position
    //   <color> <pos>          → 1 stop
    //   <color> <pos1> <pos2>  → TWO stops sharing the color ("double-
    //                            position" shorthand for a hard stop).
    // The old parseColorStop read only parts[1], silently DISCARDING pos2 —
    // `blue 0 25%` rendered as an auto-spread stop (A-RC4, the wave-21
    // conic-gradient-angle-negative 0.944 failure on all three platforms).
    // Tokenization is paren-aware so colors with spaces inside functions
    // (`color(srgb 1 0 0 / 0.5)`, `rgb(0, 0, 0)`) stay ONE token.
    internal fun parseColorStops(value: String): List<BackgroundImageProperty.ColorStop> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value)
        if (parts.isEmpty()) return emptyList()

        // First token must be the color (angular hints for conic gradients
        // put positions AFTER the color in every supported form).
        val color = ColorParser.parse(parts[0]) ?: return emptyList()
        // Position tokens: <percentage>, or the bare `0` length which
        // PercentageParser rejects — css-values-4 §5.1 allows a unitless
        // zero <length>; 0px from the gradient line start ≡ 0%.
        fun pos(tok: String): IRPercentage? =
            PercentageParser.parse(tok) ?: if (tok == "0") IRPercentage(0.0) else null

        return when (parts.size) {
            // Bare color — auto-spaced stop.
            1 -> listOf(BackgroundImageProperty.ColorStop(color, null))
            // Single explicit position (may still be null if the token is
            // an unsupported form, e.g. a px length — keep prior behavior).
            2 -> listOf(BackgroundImageProperty.ColorStop(color, pos(parts[1])))
            // Double position → two stops with the same color (§3.4.3:
            // "identical to specifying the color twice").
            3 -> {
                val p1 = pos(parts[1])
                val p2 = pos(parts[2])
                // Both positions must parse — otherwise this isn't the
                // double-position production; fall back to the first.
                if (p1 != null && p2 != null) listOf(
                    BackgroundImageProperty.ColorStop(color, p1),
                    BackgroundImageProperty.ColorStop(color, p2)
                ) else listOf(BackgroundImageProperty.ColorStop(color, p1))
            }
            // >3 tokens is not a valid <color-stop> — drop the segment.
            else -> emptyList()
        }
    }

    // `at <position>` for radial + conic gradients. Each axis is a full
    // <length-percentage> per css-images-3 §3.5 / css-values-4 §5.4:
    //   keywords  → their percentage equivalents (left=0%, center=50%, …)
    //   <percent> → Percentage (legacy raw-number wire form)
    //   <length>  → Length: absolute units normalize to px; runtime-
    //               dependent units (lh/em/rem/…) ride typed (pixels=null)
    //               per the "null means runtime-dependent" IR convention —
    //               the engines resolve them where font metrics live.
    // The old version resolved ONLY keywords/percentages, so `at 100px
    // 50px` silently dropped the whole clause (A-RC8) — a lossy:false loss.
    internal fun parsePosition(value: String): BackgroundImageProperty.Position? {
        val tokens = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        // One axis token → length-or-percentage (pct/bare-0/length — the
        // keyword forms are handled by the axis logic below because side
        // keywords are axis-LOCKED, not positional).
        fun lp(tok: String): IRLengthPercentage? {
            // Percentage form keeps the legacy raw-number wire shape.
            PercentageParser.parse(tok)?.let { return IRLengthPercentage.Percentage(it) }
            // Bare `0` is a valid <length> (css-values-4 §5.1) = 0px = 0%.
            if (tok == "0") return IRLengthPercentage.Percentage(IRPercentage(0.0))
            // <length> — LengthParser normalizes absolute units to px and
            // carries relative units typed with pixels=null.
            LengthParser.parse(tok)?.let { return IRLengthPercentage.Length(it) }
            return null
        }
        // Percentage helper for keyword equivalents (§5.4 mapping).
        fun pct(v: Double) = IRLengthPercentage.Percentage(IRPercentage(v))
        val center = pct(50.0)
        // css-values-4 §5.4: left/right ALWAYS name the x axis and
        // top/bottom the y axis — side keywords are never positional. The
        // previous version resolved keywords by token order, so `at top`
        // yielded x=0/y=center (left-center!) and `at bottom left` x=100/y=0.
        fun kwX(tok: String): Double? = when (tok) { "left" -> 0.0; "right" -> 100.0; else -> null }
        fun kwY(tok: String): Double? = when (tok) { "top" -> 0.0; "bottom" -> 100.0; else -> null }
        val keywords = setOf("left", "right", "top", "bottom", "center")
        return when (tokens.size) {
            1 -> {
                val t = tokens[0]
                // Horizontal keyword → x axis, y defaults to center (§5.4).
                kwX(t)?.let { return BackgroundImageProperty.Position(pct(it), center) }
                // Vertical keyword → y axis, x defaults to center.
                kwY(t)?.let { return BackgroundImageProperty.Position(center, pct(it)) }
                if (t == "center") return BackgroundImageProperty.Position(center, center)
                // Plain <length-percentage>: x explicit, y center (§5.4).
                val v = lp(t) ?: return null
                BackgroundImageProperty.Position(v, center)
            }
            2 -> {
                val a = tokens[0]
                val b = tokens[1]
                if (a in keywords && b in keywords) {
                    // Keyword PAIR: order-free per §5.4 (`at bottom left` ≡
                    // `at left bottom`) — assign each to its locked axis;
                    // center takes whichever axis remains. Two keywords on
                    // the SAME axis (`left right`) are invalid → null.
                    var x: Double? = null
                    var y: Double? = null
                    for (tok in tokens) {
                        kwX(tok)?.let { if (x != null) return null else x = it }
                        kwY(tok)?.let { if (y != null) return null else y = it }
                        // center is flexible — resolved after the loop.
                    }
                    BackgroundImageProperty.Position(pct(x ?: 50.0), pct(y ?: 50.0))
                } else {
                    // Mixed/value form is positional per the two-value
                    // grammar: slot 1 accepts left|center|right|<lp>, slot 2
                    // top|center|bottom|<lp>. A vertical keyword in slot 1
                    // (`top 20px`) is INVALID CSS → drop the clause (the old
                    // code silently mis-axised it instead).
                    val x = kwX(a)?.let { pct(it) } ?: (if (a == "center") center else lp(a)) ?: return null
                    val y = kwY(b)?.let { pct(it) } ?: (if (b == "center") center else lp(b)) ?: return null
                    BackgroundImageProperty.Position(x, y)
                }
            }
            else -> null
        }
    }

    // The <rectangular-color-space> / <polar-color-space> idents a
    // <color-interpolation-method> may name (css-color-4 §12.1) — the
    // closed keyword set guards stripInterpolationMethod against eating
    // an `in` that is NOT an interpolation method.
    private val INTERPOLATION_COLORSPACES = setOf(
        "srgb", "srgb-linear", "display-p3", "a98-rgb", "prophoto-rgb",
        "rec2020", "lab", "oklab", "xyz", "xyz-d50", "xyz-d65",
        "hsl", "hwb", "lch", "oklch"
    )
    // The four <hue-interpolation-method> heads (css-color-4 §12.4);
    // each must be followed by the literal `hue` to form the production.
    private val HUE_METHODS = setOf("shorter", "longer", "increasing", "decreasing")

    // css-images-4 §3.1 / §3.4.4: every gradient's syntax prefix may carry
    // a <color-interpolation-method> — `in <colorspace> [<hue-method> hue]?`
    // — in ANY order relative to the angle/direction (`||` combinator).
    // The IR has no interpolation-method field (all engines interpolate in
    // sRGB — documented platform limitation), but the method must be PEELED
    // OFF so the angle/direction beside it still parses: without this,
    // `linear-gradient(to right in srgb, …)` silently lost `to right` and
    // rendered top-to-bottom on all three platforms (WPT srgb-gradient,
    // oklab-gradient, gradient-hue-direction, …).
    // Returns the segment minus the method (possibly empty — a method may
    // be the WHOLE prefix, `in oklab, red, blue`), or null when no method
    // is present so callers keep their existing path byte-identically.
    internal fun stripInterpolationMethod(segment: String): String? {
        // Paren-aware tokenization: an `in` inside a function argument list
        // (e.g. the color stop `color-mix(in srgb, red, blue)`) stays inside
        // ONE token and can never match the standalone `in` we look for.
        val tokens = TokenizationUtils.tokenizeByWhitespace(segment)
        // Find the top-level `in <known-colorspace>` pair (case-insensitive
        // per CSS keywords; the segment reaching us is already lowered).
        val i = tokens.indices.firstOrNull { idx ->
            tokens[idx] == "in" && idx + 1 < tokens.size &&
                tokens[idx + 1] in INTERPOLATION_COLORSPACES
        } ?: return null
        // The method spans `in <colorspace>` plus an optional trailing
        // `<hue-method> hue` pair (only meaningful for polar spaces, but we
        // strip it wherever it appears — the value is unrepresentable
        // either way and leaving `longer hue` behind would poison the
        // angle/direction parse we are trying to save).
        val end = if (i + 3 < tokens.size &&
            tokens[i + 2] in HUE_METHODS && tokens[i + 3] == "hue") i + 3 else i + 1
        // Rebuild the remainder in original token order — whatever angle or
        // direction rode beside the method comes out clean for the caller.
        return (tokens.subList(0, i) + tokens.subList(end + 1, tokens.size))
            .joinToString(" ")
    }

    /**
     * Convert "to right", "to bottom", etc. to the equivalent angle
     * (css-images-3 §3.1 corner/side keywords; corners use the 45°
     * simplification this converter has always applied).
     */
    internal fun directionToAngle(direction: String): IRAngle? {
        return when (direction.lowercase()) {
            "to top" -> IRAngle.fromDegrees(0.0)
            "to top right", "to right top" -> IRAngle.fromDegrees(45.0)
            "to right" -> IRAngle.fromDegrees(90.0)
            "to bottom right", "to right bottom" -> IRAngle.fromDegrees(135.0)
            "to bottom" -> IRAngle.fromDegrees(180.0)
            "to bottom left", "to left bottom" -> IRAngle.fromDegrees(225.0)
            "to left" -> IRAngle.fromDegrees(270.0)
            "to top left", "to left top" -> IRAngle.fromDegrees(315.0)
            else -> null
        }
    }
}
