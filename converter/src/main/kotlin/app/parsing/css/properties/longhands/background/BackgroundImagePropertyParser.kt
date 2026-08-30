package app.parsing.css.properties.longhands.background

import app.irmodels.*
import app.irmodels.properties.background.BackgroundImageProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.UrlParser
import app.parsing.css.properties.primitiveParsers.DegenerateCalcRewriter
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Parser for the `background-image` property.
 *
 * Supports:
 * - none: No background image
 * - url(): Image from URL or data URI
 * - linear-gradient() / repeating-linear-gradient() (css-images-3 §3.1)
 * - radial-gradient() / repeating-radial-gradient() (css-images-3 §3.5)
 * - conic-gradient() / repeating-conic-gradient() (css-images-4 §3.4.4)
 * - cross-fade(): modern n-ary + legacy two-arg syntax (css-images-4 §2.6.2)
 *
 * Multiple images can be specified as comma-separated values.
 *
 * This object also OWNS the gradient grammar for `mask-image` —
 * MaskImagePropertyParser delegates here (one grammar, two wrappers) so the
 * two parsers can never drift again (the old duplicated mask copy had no
 * from/at prefix handling and mis-parsed `from 45deg` as a color stop).
 */
object BackgroundImagePropertyParser : PropertyParser {

    // The six gradient function heads of css-images-3 §3.1/§3.5 and
    // css-images-4 §3.4.4 (plus their repeating- variants). Only a layer
    // that BEGINS with one of these may be fed to DegenerateCalcRewriter:
    // a gradient body is built purely of case-insensitive CSS tokens
    // (colors, lengths, angles, keywords — the grammar has no <string> or
    // <url> production), so folding a calc() span inside one can never
    // touch case-/byte-sensitive author payload bytes.
    private val GRADIENT_FUNCTION_PREFIXES = listOf(
        "linear-gradient(", "repeating-linear-gradient(",
        "radial-gradient(", "repeating-radial-gradient(",
        "conic-gradient(", "repeating-conic-gradient(",
    )

    override fun parse(value: String): IRProperty? {
        val rawTrimmed = value.trim()
        // wave-48 lane W5 (scope repaired by S6 must-fix 1): fold
        // DEGENERATE-INFINITE calc() lengths (`calc(1px / 0)`,
        // `calc(Infinity * 1px)` — css-values-4 §10.9 says they are VALID
        // and clamp at used-value time) to their clamp value BEFORE the
        // expression gate below, so the gradients of WPT
        // gradient-infinity-001/002 take the typed path all three runtimes
        // render instead of the Raw fallback only the web can resolve.
        //
        // SCOPE (S6 must-fix 1): the first W5 cut ran the rewriter over the
        // WHOLE declaration, which rewrote bytes inside url() payloads,
        // quoted strings and data URIs (executed proof: url('calc(1px /
        // 0).png') → IR ["33554400px.png"]) — violating the CASE-PRESERVATION
        // CONTRACT below, whose whole point is that url()/string payloads are
        // untouchable author bytes. So: split the ORIGINAL bytes into layers
        // FIRST (splitByComma is paren-aware, so commas inside url(...) or
        // gradient bodies never split a layer) and rewrite ONLY layers that
        // begin with a gradient function head, where no byte-sensitive
        // payload can exist (see GRADIENT_FUNCTION_PREFIXES).
        val rawLayers = TokenizationUtils.splitByComma(rawTrimmed)
        // Tracks whether any gradient layer actually folded — when none did,
        // downstream must see the EXACT author bytes (no re-join drift).
        var rewroteAny = false
        // Per-layer fold: each element is the layer's trimmed ORIGINAL bytes,
        // or its folded form when it is a gradient with a degenerate calc().
        val layers = rawLayers.map { layer ->
            // Leading/trailing whitespace around a comma is insignificant
            // (CSS Syntax L3 §5) — trim before both matching and parsing.
            val t = layer.trim()
            // Function names are ASCII case-insensitive (CSS Syntax L3 §4.3):
            // match the head on a lowered COPY, rewrite the ORIGINAL bytes.
            if (GRADIENT_FUNCTION_PREFIXES.any { t.lowercase().startsWith(it) }) {
                // Per-layer all-or-nothing, exactly as the rewriter's banner
                // demands: a gradient with any OTHER calc() (finite math,
                // var(), nesting) comes back null and its author bytes
                // proceed untouched into the expression gate below.
                DegenerateCalcRewriter.rewrite(t)?.also { rewroteAny = true } ?: t
            } else {
                // NON-gradient layer (url()/image()/cross-fade()/keyword/…):
                // NEVER rewritten — its bytes may be case- and byte-sensitive
                // payload (data URIs, quoted strings, server paths).
                t
            }
        }
        // The whole-declaration form the keyword/expression gates below run
        // on. When nothing folded this is EXACTLY rawTrimmed — byte-verbatim
        // author input, preserving the historic Raw-route bytes. When a
        // gradient folded, the layers re-join with the canonical ", ":
        // inter-layer whitespace is insignificant per CSS Syntax L3 §5, and
        // this form only reaches the wire through the Raw route as valid,
        // pixel-identical CSS (non-gradient layer bytes survive verbatim).
        val trimmed = if (rewroteAny) layers.joinToString(", ") else rawTrimmed
        // CASE-PRESERVATION CONTRACT: CSS keywords/function names are ASCII
        // case-insensitive (CSS Syntax L3 §4.3), but url() PAYLOADS are
        // case-sensitive author bytes (base64 data URIs, case-sensitive
        // server paths). We therefore lower only a COPY for matching and
        // extract every payload from the ORIGINAL bytes. This repairs the
        // historic bug where the whole declaration was lowercased before
        // splitting, corrupting data URIs (the iOS runtime documented it
        // as a known wire quirk). Emitting the true bytes is a bug fix,
        // not a wire-shape change: schema/spec/05-versioning.md freezes
        // byte SHAPES, and the v2 schema is permissive at property-data
        // leaves — the leaf string's shape (a JSON string) is unchanged.
        val lowered = trimmed.lowercase()

        // Handle global keywords (inherit/initial/…): matched and stored
        // lowercase — keywords have no case-sensitive payload to preserve.
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return BackgroundImageProperty(listOf(BackgroundImageProperty.BackgroundImage.Keyword(lowered)))
        }

        // Check for var() or other complex expressions - use Raw. Detection
        // runs on the lowered copy of the REWRITTEN form (S6 must-fix 1: a
        // degenerate calc() folded above no longer trips this gate, while a
        // calc() surviving anywhere — including inside a url() payload the
        // rewriter must not touch — still routes the whole value to Raw,
        // which carries those bytes for the web runtime to resolve).
        if (ExpressionDetector.containsExpression(lowered)) {
            return BackgroundImageProperty(listOf(BackgroundImageProperty.BackgroundImage.Raw(trimmed)))
        }

        // Multiple background images: the layer list was already split from
        // the ORIGINAL bytes above (splitByComma is paren-aware and
        // case-agnostic), so each layer still carries the author's casing —
        // and its verbatim payload bytes — into parseImage.
        if (layers.isEmpty()) {
            return BackgroundImageProperty(listOf(BackgroundImageProperty.BackgroundImage.Raw(trimmed)))
        }

        // Unparseable layers fall back to Raw with the ORIGINAL layer bytes
        // (previously the lowered bytes leaked into Raw too). Layers were
        // trimmed when the list was built, so no re-trim is needed here.
        val images = layers.map { parseImage(it) ?: BackgroundImageProperty.BackgroundImage.Raw(it) }

        return BackgroundImageProperty(images)
    }

    // Receives one layer in ORIGINAL author bytes. Dispatch happens on a
    // lowered copy (keywords/function names are case-insensitive); url()
    // payloads are extracted from the original, while gradient bodies are
    // parsed from the lowered copy — every token inside a gradient (color
    // keywords, hex digits, angle units, direction keywords) is itself
    // case-insensitive per CSS Images L3/L4, so no author bytes are lost.
    // INTERNAL (not private): MaskImagePropertyParser delegates here so the
    // mask grammar is this grammar (A-RC9 — one grammar, two wrappers).
    internal fun parseImage(value: String): BackgroundImageProperty.BackgroundImage? {
        // Lowered copy used ONLY for prefix matching and gradient parsing.
        val lower = value.lowercase()
        return when {
            lower == "none" -> BackgroundImageProperty.BackgroundImage.None()
            // url(): parse from the ORIGINAL bytes — UrlParser matches the
            // function name case-insensitively but returns the raw payload.
            lower.startsWith("url(") -> parseUrl(value)
            // image() notation (wave-48 lane W5, css-images-4 §2.1) — from
            // ORIGINAL bytes too, since url/string payloads are case-
            // sensitive. Grammar lives in ImageNotationParser (≤200-line
            // rule); a refusal there falls to the Raw route as before.
            lower.startsWith("image(") -> ImageNotationParser.parse(value)
            lower.startsWith("linear-gradient(") -> parseLinearGradient(lower, repeating = false)
            lower.startsWith("repeating-linear-gradient(") -> parseLinearGradient(lower, repeating = true)
            lower.startsWith("radial-gradient(") -> parseRadialGradient(lower, repeating = false)
            lower.startsWith("repeating-radial-gradient(") -> parseRadialGradient(lower, repeating = true)
            lower.startsWith("conic-gradient(") -> parseConicGradient(lower, repeating = false)
            lower.startsWith("repeating-conic-gradient(") -> parseConicGradient(lower, repeating = true)
            // cross-fade() args may contain url() images, so parse from the
            // ORIGINAL bytes and lower per-token inside (css-images-4 §2.6.2).
            // Grammar lives in CrossFadeParser (same package — split for
            // the ≤200-line rule); it recurses back into parseImage.
            lower.startsWith("cross-fade(") -> CrossFadeParser.parse(value)
            else -> null
        }
    }

    private fun parseUrl(value: String): BackgroundImageProperty.BackgroundImage? {
        val url = UrlParser.parse(value) ?: return null
        return BackgroundImageProperty.BackgroundImage.Url(url)
    }

    private fun parseLinearGradient(value: String, repeating: Boolean): BackgroundImageProperty.BackgroundImage? {
        val funcName = if (repeating) "repeating-linear-gradient" else "linear-gradient"
        val content = TokenizationUtils.extractFunctionContent(value, funcName) ?: return null

        // Parse angle and color stops
        val parts = TokenizationUtils.splitByComma(content)
        if (parts.isEmpty()) return null

        var angle: IRAngle? = null
        var colorStopStart = 0

        // Check if first part is an angle or direction. A
        // <color-interpolation-method> (`in srgb [longer hue]`, §3.1's `||`
        // combinator) may ride in the same segment — peel it off FIRST so
        // `to right in srgb` still yields its direction; its presence also
        // marks the segment as the syntax prefix even when nothing else
        // remains (`in oklab, red, blue` — angle stays null, stops start
        // at the next segment).
        val rawFirst = parts[0].trim()
        val strippedFirst = GradientValueParsers.stripInterpolationMethod(rawFirst)
        // wave-37: the method is CARRIED as well as peeled — the web runtime
        // re-emits it so the browser interpolates in the authored space.
        val interp = GradientValueParsers.interpolationMethodOf(rawFirst)
        if (strippedFirst != null) colorStopStart = 1
        val firstPart = strippedFirst ?: rawFirst

        // Try parsing as angle first
        AngleParser.parse(firstPart)?.let {
            angle = it
            colorStopStart = 1
        } ?: run {
            // Try parsing as direction keyword (to right, to bottom, to top left, etc.)
            if (firstPart.startsWith("to ")) {
                angle = GradientValueParsers.directionToAngle(firstPart)
                if (angle != null) {
                    colorStopStart = 1
                }
            }
        }

        // ── syntax-prefix strictness (wave-40 lane T6) ────────────────────
        // The full rationale + the measured CSS.supports evidence lives in
        // GradientPrefixGuard (same package). Two shapes are rejected here:
        //   1. a leftover beside a RECOGNISED interpolation method that is
        //      neither an angle nor a direction (`0.25turns in srgb`) — §3.1's
        //      `||` combinator allows nothing else in that slot;
        //   2. an UNCLAIMED first segment that cannot be a <color-stop>.
        // Returning null routes the layer to parse()'s Raw fallback, whose
        // author bytes the web runtime re-emits verbatim — so the browser
        // drops exactly what Chromium drops, instead of us inventing pixels.
        if (colorStopStart == 1 && angle == null && firstPart.isNotEmpty()) return null
        if (colorStopStart == 0 && !GradientPrefixGuard.isColorStopSegment(firstPart)) return null

        // Parse color stops — flatMap because a double-position stop
        // (`red 25% 50%`, css-images-4 §3.4.3) expands into TWO entries.
        val colorStops = parts.drop(colorStopStart).flatMap { GradientValueParsers.parseColorStops(it.trim()) }
        if (colorStops.isEmpty()) return null

        return BackgroundImageProperty.BackgroundImage.LinearGradient(angle, colorStops, repeating, interp)
    }

    private fun parseRadialGradient(value: String, repeating: Boolean): BackgroundImageProperty.BackgroundImage? {
        val funcName = if (repeating) "repeating-radial-gradient" else "radial-gradient"
        val content = TokenizationUtils.extractFunctionContent(value, funcName) ?: return null

        // CSS Images Module 3 §3.5: the optional first comma-separated part
        // is a "<radial-gradient-syntax>" prefix carrying any of:
        //   <ending-shape> (circle | ellipse)
        //   <ending-shape-size> (closest-side | closest-corner | farthest-side
        //                       | farthest-corner) | <length-percentage>{1,2}
        //   "at" <position>
        // Any of those can appear in any order, all are optional. If the
        // first part doesn't parse as a stop AND mentions any of these
        // tokens, treat it as the prefix; otherwise fall through and parse
        // every part as a colour stop. Failing to peel this prefix off used
        // to yield a bogus stop with `color: { original: "ellipse" }` and
        // dropped the size keyword entirely (Phase 12 audit regression).
        val parts = TokenizationUtils.splitByComma(content)
        if (parts.isEmpty()) return null

        var shape: BackgroundImageProperty.GradientShape? = null
        var size: BackgroundImageProperty.GradientSize? = null
        var position: BackgroundImageProperty.Position? = null
        var stopStart = 0

        // Peel any <color-interpolation-method> off the candidate prefix
        // (§3.5's `||` combinator, same rationale as the linear case) —
        // its presence alone marks the segment as the prefix.
        val rawRadialFirst = parts[0].trim()
        val strippedRadialFirst = GradientValueParsers.stripInterpolationMethod(rawRadialFirst)
        val interp = GradientValueParsers.interpolationMethodOf(rawRadialFirst)
        val first = strippedRadialFirst ?: rawRadialFirst
        if (strippedRadialFirst != null && !looksLikeRadialPrefix(first)) {
            // Method-only prefix (`in oklab, red, blue`) — nothing else to
            // extract; just skip the segment when parsing stops.
            stopStart = 1
        }
        if (looksLikeRadialPrefix(first)) {
            // Split off "at <pos>" first.
            val atIdx = first.indexOf(" at ")
            // "at ..." may also be the WHOLE prefix (position-only form).
            val startsWithAt = first.startsWith("at ")
            val shapeAndSize = when {
                startsWithAt -> ""
                atIdx >= 0 -> first.substring(0, atIdx).trim()
                else -> first
            }
            val posPart = when {
                startsWithAt -> first.removePrefix("at ").trim()
                atIdx >= 0 -> first.substring(atIdx + 4).trim()
                else -> null
            }
            // Now tokenise shapeAndSize.
            for (tok in shapeAndSize.split(Regex("\\s+")).filter { it.isNotBlank() }) {
                when (tok) {
                    "circle" -> shape = BackgroundImageProperty.GradientShape.CIRCLE
                    "ellipse" -> shape = BackgroundImageProperty.GradientShape.ELLIPSE
                    "closest-side" -> size = BackgroundImageProperty.GradientSize.CLOSEST_SIDE
                    "closest-corner" -> size = BackgroundImageProperty.GradientSize.CLOSEST_CORNER
                    "farthest-side" -> size = BackgroundImageProperty.GradientSize.FARTHEST_SIDE
                    "farthest-corner" -> size = BackgroundImageProperty.GradientSize.FARTHEST_CORNER
                    // <length-percentage> radii unsupported here; leave size null.
                }
            }
            posPart?.let { position = GradientValueParsers.parsePosition(it) }
            stopStart = 1
        }

        // flatMap — double-position stops expand to two entries (§3.4.3).
        val colorStops = parts.drop(stopStart).flatMap { GradientValueParsers.parseColorStops(it.trim()) }
        if (colorStops.isEmpty()) return null

        return BackgroundImageProperty.BackgroundImage.RadialGradient(shape, size, position, colorStops, repeating, interp)
    }

    // The first comma-segment of a radial-gradient is a prefix when it
    // mentions any shape/size keyword or starts with "at " (a position-only
    // prefix). Anything else (e.g. "red", "red 0%") is a colour stop.
    private fun looksLikeRadialPrefix(s: String): Boolean {
        val l = s.lowercase()
        if (l.startsWith("at ")) return true
        for (kw in listOf("circle", "ellipse", "closest-side", "closest-corner",
                          "farthest-side", "farthest-corner", " at ")) {
            if (l.contains(kw)) return true
        }
        return false
    }

    // (`at <position>` and color-stop micro-grammars live in
    // GradientValueParsers — split for the ≤200-line rule.)

    private fun parseConicGradient(value: String, repeating: Boolean): BackgroundImageProperty.BackgroundImage? {
        val funcName = if (repeating) "repeating-conic-gradient" else "conic-gradient"
        val content = TokenizationUtils.extractFunctionContent(value, funcName) ?: return null

        val parts = TokenizationUtils.splitByComma(content)
        if (parts.isEmpty()) return null

        // Handle "from <angle>" and/or "at <position>" prefix before color stops
        var fromAngle: IRAngle? = null
        var position: BackgroundImageProperty.Position? = null
        var colorStopStart = 0
        // Peel any <color-interpolation-method> off the prefix segment
        // (§3.4.4's `||` combinator — `from 45deg in oklch` must not lose
        // its from-angle); method presence alone marks the prefix.
        val rawConicFirst = parts[0].trim()
        val strippedConicFirst = GradientValueParsers.stripInterpolationMethod(rawConicFirst)
        val interp = GradientValueParsers.interpolationMethodOf(rawConicFirst)
        if (strippedConicFirst != null) colorStopStart = 1
        val firstPart = strippedConicFirst ?: rawConicFirst

        // Pull out the "at <pos>" tail (if any) and the "from <angle>"
        // head. Either part may be missing, but they always appear in
        // this order per CSS Images Module 4 §3.4.4.
        if (firstPart.startsWith("from ")) {
            val afterFrom = firstPart.removePrefix("from ").trim()
            val atIndex = afterFrom.indexOf(" at ")
            val anglePart = if (atIndex >= 0) afterFrom.substring(0, atIndex).trim() else afterFrom
            fromAngle = AngleParser.parse(anglePart)
            // Same strictness as the linear case, same reason: css-images-4
            // §3.4.4 types this slot as `from <angle>` and nothing else, so a
            // unit outside the css-values-4 §7.1 table (`from 0.25turns`)
            // makes the whole function — and therefore the declaration —
            // invalid. We used to drop just the angle and paint from 0deg.
            if (fromAngle == null) return null
            if (atIndex >= 0) {
                position = GradientValueParsers.parsePosition(afterFrom.substring(atIndex + 4).trim())
            }
            colorStopStart = 1
        } else if (firstPart.startsWith("at ")) {
            position = GradientValueParsers.parsePosition(firstPart.removePrefix("at ").trim())
            colorStopStart = 1
        }

        // flatMap — double-position stops expand to two entries (§3.4.3).
        val colorStops = parts.drop(colorStopStart).flatMap { GradientValueParsers.parseColorStops(it.trim()) }
        if (colorStops.isEmpty()) return null

        return BackgroundImageProperty.BackgroundImage.ConicGradient(fromAngle, position, colorStops, repeating, interp)
    }

}
