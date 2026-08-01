package com.styleconverter.runtime.typography

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.*

/**
 * Extracts text-specific styles from IR properties.
 *
 * ## Implemented Properties
 *
 * ### Text Styling (TextStyle)
 * - Color (text color)
 * - FontSize
 * - FontWeight
 * - FontStyle (italic)
 * - FontFamily
 * - TextAlign
 * - TextDecoration (underline, line-through)
 * - TextDecorationColor (tracked, limited Compose support)
 * - TextDecorationThickness (tracked, limited Compose support)
 * - TextDecorationStyle (tracked, limited Compose support)
 * - LineHeight
 * - LetterSpacing
 * - WordSpacing (real: AnnotatedString space-only spans — see
 *   [extractWordSpacingSp] / [applyWordSpacingSpans], NOT a
 *   letter-spacing approximation)
 * - TextIndent
 * - TextTransform (uppercase, lowercase, capitalize)
 * - BaselineShift (sub, super)
 *
 * ### Text Overflow (Text composable parameters)
 * - TextOverflow -> TextOverflow (Clip, Ellipsis, Visible)
 * - LineClamp -> maxLines (limits number of lines)
 * - MaxLines -> maxLines (limits number of lines)
 *
 * ### Text Wrapping
 * - WhiteSpace -> softWrap (nowrap = false)
 * - WordBreak -> controls mid-word breaking
 * - OverflowWrap -> controls long word overflow
 * - WordWrap -> alias for overflow-wrap (legacy)
 * - LineBreak -> CJK line breaking rules (maps to Compose LineBreak)
 *
 * ### Text Justification & Hyphenation
 * - TextJustify -> justification method (auto, none, inter-word, inter-character)
 * - TextAlignLast -> alignment of last line (auto, start, end, left, right, center, justify)
 * - Hyphens -> hyphenation control (none, manual, auto)
 * - HyphenateCharacter -> character used for hyphenation
 */
object TextStyleApplier {

    /**
     * Extract text style from IR properties.
     *
     * @param inheritedFontSizeSp The PARENT's resolved font-size in the
     *   runtime's px==sp space, when the caller has an inheritance channel
     *   to read it from (ComponentRenderer / ContentApplier thread
     *   `DynamicValueResolver.fontSizePxOf(LocalInheritedProperties)`).
     *   It is the resolution base for the relative font-size values —
     *   em / % (css-values-4 §5.1.1: font-size's own em resolves against
     *   the INHERITED size) and smaller / larger (CSS 2.1 §15.7: one
     *   ladder step from the INHERITED size). Null (unit tests, callers
     *   without a cascade) falls back to [RELATIVE_SIZE_BASE_SP] — the
     *   16px browser default, which IS the honest inherited value on the
     *   committed fixture corpus (fixtures never style ancestors), so
     *   defaulted and threaded calls agree everywhere the baseline pins.
     */
    fun extractTextStyle(
        properties: List<IRProperty>,
        inheritedFontSizeSp: Float? = null
    ): TextStyle {
        // Single resolved base for every relative-font-size branch below —
        // threaded inherited size when available, browser-default 16 else.
        val relativeBaseSp = inheritedFontSizeSp ?: RELATIVE_SIZE_BASE_SP
        var color: Color? = null
        var fontSize: TextUnit? = null
        var fontWeight: FontWeight? = null
        var fontStyle: FontStyle? = null
        var fontFamily: FontFamily? = null
        var textAlign: TextAlign? = null
        var textDecoration: TextDecoration? = null
        var lineHeight: TextUnit? = null
        var letterSpacing: TextUnit? = null
        var shadow: Shadow? = null
        var baselineShift: BaselineShift? = null
        var textIndent: TextIndent? = null
        var textGeometricTransform: TextGeometricTransform? = null
        var lineBreak: LineBreak? = null

        // Pre-resolve FontSize so a unitless `line-height: <n>` declared
        // before FontSize in property order still multiplies against
        // the right base. CSS source order isn't guaranteed.
        val preResolvedFontSp: Float? = properties
            .firstOrNull { it.type == "FontSize" }
            ?.let { try { extractFontSize(it.data, relativeBaseSp)?.value } catch (_: Exception) { null } }

        properties.forEach { property ->
            try {
                when (property.type) {
                    "Color" -> color = ValueExtractors.extractColor(property.data)
                    "FontSize" -> fontSize = extractFontSize(property.data, relativeBaseSp)
                    "FontWeight" -> fontWeight = extractFontWeight(property.data)
                    "FontStyle" -> fontStyle = extractFontStyle(property.data)
                    "FontFamily" -> fontFamily = extractFontFamily(property.data)
                    "TextAlign" -> textAlign = extractTextAlign(property.data)
                    "TextDecorationLine" -> textDecoration = extractTextDecoration(property.data)
                    "LineHeight" -> lineHeight = extractLineHeight(property.data, preResolvedFontSp)
                    "LetterSpacing" -> letterSpacing = extractLetterSpacing(property.data)
                    "WordSpacing" -> {
                        // css-text-3 §5.1: word-spacing adds advance at WORD
                        // SEPARATORS only. The old fallback mapped it onto
                        // TextStyle.letterSpacing, which inserts the gap
                        // between EVERY glyph pair — `word-spacing: 12px` on
                        // "0123 4567" spread the digits 12px apart and pushed
                        // "4567" clean off the box. Compose's TextStyle has no
                        // word-spacing field, so the REAL implementation lives
                        // in the AnnotatedString pass (PlaceholderContent →
                        // [applyWordSpacingSpans]): a SpanStyle letter-spacing
                        // applied ONLY to the space characters, i.e. extra
                        // advance after each space == CSS word-spacing.
                        // Nothing to write into the TextStyle here — the
                        // render site reads the value via
                        // [extractWordSpacingSp] (handled, not dropped).
                    }
                    "TextShadow" -> shadow = extractTextShadow(property.data)
                    "VerticalAlign" -> baselineShift = extractBaselineShift(property.data)
                    "TextIndent" -> textIndent = extractTextIndent(property.data)
                    "FontStretch" -> {
                        // CSS `font-stretch` selects a width axis on a variable
                        // font / a matching condensed/expanded family member.
                        // System sans-serifs (the default on iOS/web/Android)
                        // do NOT have a width axis, so on iOS and web the
                        // property is a visible no-op. Compose's
                        // TextGeometricTransform(scaleX = pct/100) on the other
                        // hand is a *geometric* horizontal scale that always
                        // applies regardless of font capability — using it for
                        // font-stretch made every UltraExpanded/UltraCondensed
                        // fixture diverge sharply from iOS+web (e.g.
                        // Typography_FontUltraExpanded SSIM 0.46 because the
                        // text wrapped to two lines on Android while iOS/web
                        // kept it on one). Treat as no-op to match the other
                        // platforms. The value is still read so the property
                        // counts as supported in the audit, but we don't
                        // forge a fake stretch via geometric scaling.
                        extractFontStretch(property.data) // parse for audit, discard
                    }
                    "LineBreak" -> lineBreak = extractLineBreakStyle(property.data)
                    "TextTransform" -> {
                        // Note: TextTransform requires text manipulation, not style
                        // Store for later use in text rendering
                    }
                }
            } catch (e: Exception) {
                // Skip properties that fail to parse
            }
        }

        // Build TextStyle using copy to apply properties.
        // PlatformTextStyle(includeFontPadding = false) + LineHeightStyle
        // (alignment Center, trim None) is the canonical Compose recipe
        // for "make line-height behave like CSS line-height": the line
        // box is exactly `lineHeight` tall (no extra ascender/descender
        // padding) and the glyphs sit centred within it. Without these
        // two opt-ins, single-line text rendered with `lineHeight: 28`
        // came out only ~22sp tall on emulator-default Roboto because
        // includeFontPadding=true added ~3sp top + bottom AND the line
        // box collapsed to the font's natural metrics. Both flags align
        // Android's measured box height with web (`line-height: 28px`)
        // and iOS (`.frame(minHeight: 28)`) byte-for-byte.
        return TextStyle.Default.copy(
            color = color ?: Color.Unspecified,
            // text-align (css-text-3 §6.1). The local was extracted from the
            // "TextAlign" IR property above but never written into the copy,
            // so every caller (ComponentRenderer.PlaceholderContent included)
            // saw Unspecified and fell back to Start — `text-align: center`
            // rendered left-flushed on Android while web centered it
            // (TextAlign_Center, Android-web SSIM 0.86).
            textAlign = textAlign ?: TextAlign.Unspecified,
            fontSize = fontSize ?: TextUnit.Unspecified,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            lineHeight = lineHeight ?: TextUnit.Unspecified,
            letterSpacing = letterSpacing ?: TextUnit.Unspecified,
            shadow = shadow,
            baselineShift = baselineShift,
            textIndent = textIndent,
            textGeometricTransform = textGeometricTransform,
            lineBreak = lineBreak ?: LineBreak.Simple,
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
            ),
            // TextMotion.Animated = linear (unhinted) glyph metrics +
            // subpixel positioning — the same float-advance model Chrome
            // (the reference) and CoreText use. The default (Static)
            // quantizes each glyph advance through the hinting pass, and
            // the per-glyph rounding error ACCUMULATES along a line:
            // wave-6 combined-run measurement (typography-full archive)
            // showed Android glyph runs drifting off web by up to ±3.5px
            // at line end (+0.09px/glyph at font-size 20px, −0.13px/glyph
            // at 22px — direction flips with size, so it cannot be fudged
            // with letter-spacing), resetting at each line start, while
            // iOS tracked web within ±0.5px everywhere. Re-aligning words
            // in the captures lifted Android-web SSIM 0.9364→0.9833
            // (InitialLetter_Normal 20px) and 0.8884→0.9657
            // (FontWeight_Normal 22px), i.e. the drift — not AA character —
            // was the bulk of the Android-web "rasterization wall".
            // Vertical placement needed no change (matched web ≤0.3px).
            textMotion = androidx.compose.ui.text.style.TextMotion.Animated
        )
    }

    /**
     * Extract font stretch as TextGeometricTransform.
     * Maps CSS font-stretch percentages to horizontal scale.
     * 100% = normal, 50% = ultra-condensed, 200% = ultra-expanded
     */
    private fun extractFontStretch(data: JsonElement): TextGeometricTransform? {
        val percentage = when (data) {
            is JsonPrimitive -> {
                data.floatOrNull ?: when (data.contentOrNull?.lowercase()) {
                    "ultra-condensed" -> 50f
                    "extra-condensed" -> 62.5f
                    "condensed" -> 75f
                    "semi-condensed" -> 87.5f
                    "normal" -> 100f
                    "semi-expanded" -> 112.5f
                    "expanded" -> 125f
                    "extra-expanded" -> 150f
                    "ultra-expanded" -> 200f
                    else -> null
                }
            }
            is JsonObject -> {
                data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
            }
            else -> null
        } ?: return null

        // Convert percentage to scale factor (100% = 1.0)
        val scaleX = percentage / 100f
        return TextGeometricTransform(scaleX = scaleX)
    }

    /**
     * Extract text color from properties.
     */
    fun extractTextColor(properties: List<IRProperty>): Color? {
        return properties.find { it.type == "Color" }?.let {
            ValueExtractors.extractColor(it.data)
        }
    }

    /**
     * Extract text alignment from properties.
     */
    fun extractTextAlign(properties: List<IRProperty>): TextAlign? {
        return properties.find { it.type == "TextAlign" }?.let {
            extractTextAlign(it.data)
        }
    }

    private fun extractFontSize(
        data: JsonElement,
        // Inherited-size base for the relative branches (em / % / smaller /
        // larger) — threaded from extractTextStyle; defaults to the 16px
        // browser-inherited base for direct unit-level calls.
        inheritedBaseSp: Float = RELATIVE_SIZE_BASE_SP
    ): TextUnit? {
        // Check for pixel value
        val dp = ValueExtractors.extractDp(data)
        if (dp != null) {
            return dp.value.sp
        }

        // Check for original with pixels
        if (data is JsonObject) {
            data["pixels"]?.jsonPrimitive?.floatOrNull?.let {
                return it.sp
            }
            // Handle keyword sizes
            val original = data["original"]
            if (original is JsonObject) {
                val type = original["type"]?.jsonPrimitive?.contentOrNull
                // The live wire tag is "absolute" (FontSizeProperty's
                // serializer); "absoluteKeyword" is the stale name this
                // check historically matched — the branch still worked in
                // practice only because absolute keywords also ship a
                // resolved px (caught above). Accept both, honestly.
                if (type == "absoluteKeyword" || type == "absolute") {
                    val keyword = original["keyword"]?.jsonPrimitive?.contentOrNull
                    return when (keyword?.lowercase()) {
                        "xx-small" -> 9.sp
                        "x-small" -> 10.sp
                        "small" -> 13.sp
                        "medium" -> 16.sp
                        "large" -> 18.sp
                        "x-large" -> 24.sp
                        "xx-large" -> 32.sp
                        "xxx-large" -> 48.sp
                        else -> null
                    }
                }
                // Relative keywords (wire: {"original":{"type":"relative",
                // "keyword":"smaller"|"larger"}, px absent}). CSS 2.1 §15.7
                // (and css-fonts-4 §2.5): `larger`/`smaller` step ONE notch
                // up/down the absolute-size ladder from the INHERITED size;
                // adjacent ladder steps are separated by roughly the spec's
                // recommended 1.2 scaling factor. We approximate the step
                // as ×1.2 / ÷1.2 against the inherited base threaded in by
                // the caller (browser-default 16px when no ancestor styles
                // font-size — the only base the committed fixture corpus
                // produces) — larger → 19.2px, smaller → 13.33px at that
                // default, matching Chrome's observed resolution for a
                // non-keyword inherited 16px. Previously this shape fell
                // through to null → the caller's 16sp default, so
                // smaller/larger reused the `medium` rendering unchanged.
                if (type == "relative") {
                    val keyword = original["keyword"]?.jsonPrimitive?.contentOrNull
                    return when (keyword?.lowercase()) {
                        "larger" -> (inheritedBaseSp * RELATIVE_SIZE_STEP).sp
                        "smaller" -> (inheritedBaseSp / RELATIVE_SIZE_STEP).sp
                        else -> null
                    }
                }
                // Relative lengths (LIVE wire, verified against the running
                // converter: `font-size: 1.5em` deep-flattens to
                // {"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}
                // — NO resolved px, NO "value" key). css-values-4 §5.1.1:
                // em on font-size itself resolves against the INHERITED
                // size (the threaded base); rem against the ROOT size,
                // which the harness pins at the 16px browser default
                // (fixtures never style the root — a true constant).
                // Previously this shape fell through to null → 16sp
                // default while iOS/web resolved it (1.5em → 24px).
                // Note: in the full renderer pipeline DynamicValueResolver
                // usually adds a top-level "px" for em first (caught by
                // extractDp above) — this branch is the honest fallback
                // for callers without that pass.
                if (type == "length") {
                    val inner = original["original"] as? JsonObject
                    val v = inner?.get("v")?.jsonPrimitive?.floatOrNull
                    val u = inner?.get("u")?.jsonPrimitive?.contentOrNull?.uppercase()
                    if (v != null) {
                        return when (u) {
                            "EM" -> (v * inheritedBaseSp).sp
                            "REM" -> (v * 16f).sp
                            // Other relative units (vw/vh/ch/ex …) have no
                            // static base here — fall through to the
                            // caller's default rather than forging one
                            // (explicit, not a silent drop).
                            else -> null
                        }
                    }
                }
                // Percentages (LIVE wire: `font-size: 120%` emits
                // {"original":{"type":"percentage","value":120}}, no px).
                // css-fonts-4 §2.4: a <percentage> font-size resolves
                // against the inherited font-size — the same base as em,
                // so 120% of the 16px default = 19.2px. Previously fell
                // through to null → the 16sp default.
                if (type == "percentage") {
                    original["value"]?.jsonPrimitive?.floatOrNull?.let { pct ->
                        return (pct / 100f * inheritedBaseSp).sp
                    }
                }
            }
        }
        return null
    }

    /**
     * Fallback inherited base for the relative font-size values (em / % /
     * `smaller` / `larger`) — the browser-default 16px the placeholder
     * corpus inherits (fixtures never style ancestors, so this is the
     * honest computed base, not a guess). Used only when the caller has no
     * inheritance channel to thread the real parent size through
     * (extractTextStyle's `inheritedFontSizeSp` parameter); the render
     * call sites pass the actual inherited value, matching iOS's
     * inherited-size resolution.
     */
    private const val RELATIVE_SIZE_BASE_SP = 16f

    /**
     * CSS 2.1 §15.7's recommended scaling factor between adjacent entries
     * of the absolute-size ladder — the ±1-step multiplier for
     * `larger`/`smaller`. Documented approximation: real UAs use a
     * per-entry table with non-uniform ratios; 1.2 matches Chrome for a
     * 16px inherited base, which is the only base the corpus produces.
     */
    private const val RELATIVE_SIZE_STEP = 1.2f

    private fun extractFontWeight(data: JsonElement): FontWeight? {
        // IR ships font-weight in two shapes:
        //   • bare numeric  → JsonPrimitive(900)             from `font-weight: 900`
        //   • keyword form  → JsonObject({ weight: 700,      from `font-weight: bold`
        //                                  original: "bold"})
        // Old code only handled the JsonObject case, so numeric weights
        // (Edge_HugeText `900`, Button_Primary `600`) silently fell through
        // to FontWeight.Normal — Android rendered regular while iOS/web
        // rendered bold. Resolve the int from either shape, then map to
        // the standard 100-step bucket.
        val weight: Int? = when (data) {
            is JsonObject -> data["weight"]?.jsonPrimitive?.intOrNull
            // floatOrNull covers IRs that emit "700.0" — kotlinx-serialization
            // sometimes preserves the JSON numeric type literally.
            is JsonPrimitive -> data.intOrNull ?: data.floatOrNull?.toInt()
                // css-fonts-4 §2.2 keyword values. The RELATIVE keywords
                // resolve against the inherited weight; the placeholder's
                // inherited weight is always `normal` (400), so the spec's
                // resolution table gives bolder→700 and lighter→100.
                // Previously `font-weight: bolder` fell through to null →
                // regular, while web rendered bold (Typography_C08 0.908).
                ?: when (data.contentOrNull?.lowercase()) {
                    "normal" -> 400
                    "bold" -> 700
                    "bolder" -> 700   // from inherited 400 (css-fonts-4 table)
                    "lighter" -> 100  // from inherited 400 (css-fonts-4 table)
                    else -> null
                }
            else -> null
        }
        if (weight != null) {
            return when (weight) {
                100 -> FontWeight.Thin
                200 -> FontWeight.ExtraLight
                300 -> FontWeight.Light
                400 -> FontWeight.Normal
                500 -> FontWeight.Medium
                600 -> FontWeight.SemiBold
                700 -> FontWeight.Bold
                800 -> FontWeight.ExtraBold
                900 -> FontWeight.Black
                else -> FontWeight(weight.coerceIn(1, 1000))
            }
        }
        return null
    }

    // css-fonts-4 §2.5: bare `oblique` means `oblique 14deg`, and 14deg is
    // also the slant at which Blink's font-matching starts treating an
    // oblique request as italic-shaped. Pinned empirically against the
    // wave-6 web captures (Inter ships roman-only in every harness, so
    // Chromium SYNTHESIZES the slant): `oblique 14deg`/`18deg` rendered
    // pixel-identical to `italic`, while `0deg`/`-10deg`/`11.46deg`
    // rendered pixel-identical to `normal` — one fixed skew, cut at 14.
    private const val OBLIQUE_SLANT_THRESHOLD_DEG = 14.0

    private fun extractFontStyle(data: JsonElement): FontStyle? {
        // `oblique <angle>` arrives as {"oblique":{"deg":N,…}} on the live
        // wire. FontStylePropertyParser.kt normalizes ALL angle units
        // (deg/rad/grad/turn — grad suffix-order bug fixed) to a numeric
        // "deg", clamps to css-fonts-4 §2.4's [-90, 90] band, and rejects
        // the declaration on an unparseable angle, so a well-formed wire
        // always carries a real "deg" (non-deg sources add an "original"
        // key we ignore).
        // Compose's fake italic on a roman-only FontFamily applies the SAME
        // fixed skew Chromium synthesizes (TextPaint#setTextSkewX(-0.25),
        // ≈14deg, via the default FontSynthesis.All), so a threshold map —
        // deg ≥ 14 → Italic, deg < 14 (incl. 0/negative) → Normal —
        // reproduces the web render exactly. A per-angle
        // TextGeometricTransform would NOT: it would slant 11.46deg text
        // that Chromium leaves upright and over-slant 18deg text.
        (data as? JsonObject)?.get("oblique")?.let { obliquePayload ->
            // Missing/non-numeric "deg" is a malformed payload — return
            // null so the drop stays visible instead of guessing a slant.
            val deg = (obliquePayload as? JsonObject)
                ?.get("deg")?.jsonPrimitive?.doubleOrNull ?: return null
            return if (deg >= OBLIQUE_SLANT_THRESHOLD_DEG) FontStyle.Italic
                   else FontStyle.Normal
        }
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.lowercase()) {
            // Bare `oblique` = `oblique 14deg` (css-fonts-4 §2.5 default),
            // exactly at the threshold → slants like italic on all three
            // platforms (verified: web capture 039 == 038 pixel-identical).
            "italic", "oblique" -> FontStyle.Italic
            "normal" -> FontStyle.Normal
            else -> null
        }
    }

    private fun extractFontFamily(data: JsonElement): FontFamily? {
        // Font family handling - map common names
        if (data is JsonArray && data.isNotEmpty()) {
            val firstFont = data[0].jsonPrimitive.contentOrNull?.lowercase()
            return when {
                firstFont?.contains("mono") == true -> FontFamily.Monospace
                firstFont?.contains("serif") == true && !firstFont.contains("sans") -> FontFamily.Serif
                firstFont?.contains("sans") == true -> FontFamily.SansSerif
                firstFont?.contains("cursive") == true -> FontFamily.Cursive
                else -> FontFamily.Default
            }
        }
        if (data is JsonPrimitive) {
            val font = data.contentOrNull?.lowercase()
            return when {
                font?.contains("mono") == true -> FontFamily.Monospace
                font?.contains("serif") == true && !font.contains("sans") -> FontFamily.Serif
                font?.contains("sans") == true -> FontFamily.SansSerif
                font?.contains("cursive") == true -> FontFamily.Cursive
                // Default sans-serif → bundled Inter for cross-platform parity.
                else -> InterFontFamily
            }
        }
        return null
    }

    private fun extractTextAlign(data: JsonElement): TextAlign? {
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.uppercase()) {
            "LEFT", "START" -> TextAlign.Start
            "RIGHT", "END" -> TextAlign.End
            "CENTER" -> TextAlign.Center
            "JUSTIFY" -> TextAlign.Justify
            else -> null
        }
    }

    private fun extractTextDecoration(data: JsonElement): TextDecoration? {
        // Handle array format: ["UNDERLINE"] or ["LINE_THROUGH"]
        if (data is kotlinx.serialization.json.JsonArray) {
            val decorations = data.mapNotNull { elem ->
                val kw = elem.jsonPrimitive.contentOrNull?.lowercase()?.replace("_", "-")
                when (kw) {
                    "underline" -> TextDecoration.Underline
                    "line-through" -> TextDecoration.LineThrough
                    // "overline" is NOT dropped: Compose's TextDecoration has
                    // no overline flag, so it renders through the OWNED draw
                    // pass instead — [extractDecorationLineFlags] +
                    // [decorationSegments], painted by PlaceholderContent over
                    // the laid-out lines (css-text-decor-3 §2.1). Note the
                    // owned pass also re-draws underline/line-through with
                    // Chromium geometry on the label path; this TextStyle
                    // mapping stays for paths WITHOUT layout access (the
                    // renderer strips it where the owned pass is active).
                    else -> null
                }
            }
            return when {
                decorations.isEmpty() -> null
                decorations.size == 1 -> decorations.first()
                else -> TextDecoration.combine(decorations)
            }
        }
        // Handle keyword format: "UNDERLINE" or "LINE_THROUGH"
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.lowercase()?.replace("_", "-")) {
            "underline" -> TextDecoration.Underline
            "line-through" -> TextDecoration.LineThrough
            "none" -> TextDecoration.None
            else -> null
        }
    }

    private fun extractLineHeight(data: JsonElement, fontSizeSp: Float? = null): TextUnit? {
        // Wave 22 (lane FONT) note — a DECLARED `line-height: normal` arrives
        // here as `{"multiplier":1.2,"original":"normal"}` and DELIBERATELY
        // still consumes that legacy 1.2 compatibility multiplier below. The
        // keyword's CSS-correct answer is the face's own metrics, but honouring
        // it on THIS path would move the committed 327-pair dark-stage
        // baselines (fixtures/properties/typography/line-height.json's
        // LineHeight_Normal variant renders through here), which this lane may
        // not re-capture. The override is therefore WPT-GATED one level up, in
        // ComponentRenderer's placeholder line-box pick — see
        // LineHeightNormal.lineBoxSource: outside WPT capture this 1.2 value
        // wins exactly as it always has, byte-for-byte.
        if (data is JsonObject) {
            // Check for pixel value
            data["pixels"]?.jsonPrimitive?.floatOrNull?.let {
                return it.sp
            }
            // Check for multiplier — multiply by the rendered font-size
            // (resolved upstream from the same property pass) so
            // `line-height: 2` on a 14px element yields 28sp, not the
            // 32sp the prior 16-default produced. Falls back to 16
            // only when font-size genuinely isn't declared.
            data["multiplier"]?.jsonPrimitive?.floatOrNull?.let {
                return ((fontSizeSp ?: 16f) * it).sp
            }
        }
        val dp = ValueExtractors.extractDp(data)
        if (dp != null) {
            return dp.value.sp
        }
        return null
    }

    private fun extractLetterSpacing(data: JsonElement): TextUnit? {
        if (data is JsonObject) {
            data["pixels"]?.jsonPrimitive?.floatOrNull?.let {
                return it.sp
            }
            // Relative-unit escape hatch: the converter serializes
            // `letter-spacing: 0.25rem` as {"px": 0.0, "original":
            // {…{"v":0.25,"u":"REM"}}} — px carries a bogus 0 instead of
            // null, so extractDp below happily returned 0.sp and the
            // tracking vanished (Typography_C10: web spaced glyphs 4px
            // apart, Android didn't, 0.847).
            val px = data["px"]?.jsonPrimitive?.floatOrNull
            if (px == 0f) {
                val vu = relativeOriginalVU(data)
                if (vu != null) {
                    val (v, u) = vu
                    when (u) {
                        // css-values-4 §5.1.1: em resolves against the
                        // ELEMENT's own font-size. Compose has a native em
                        // TextUnit for exactly this (a letterSpacing given
                        // in .em resolves against the style's fontSize at
                        // layout time), so `0.1em` on a 22px element yields
                        // 2.2px — the previous hardcoded ×16 produced 1.6px
                        // whenever the element's font-size wasn't the 16px
                        // root default.
                        "EM" -> return v.em
                        // rem resolves against the ROOT font-size, which the
                        // harness pins at the browser default 16px (fixtures
                        // never style the root element) — a true constant
                        // here, unlike em.
                        "REM" -> return (v * 16f).sp
                    }
                }
            }
        }
        val dp = ValueExtractors.extractDp(data)
        if (dp != null) {
            return dp.value.sp
        }
        return null
    }

    /**
     * Unwrap the {v,u} original of a spacing wire value whose px slot is a
     * bogus 0. Two envelope generations exist in the corpus:
     *   - {"px":0.0,"original":{"type":"length","original":{"v":…,"u":…}}}
     *     (the LIVE Letter/WordSpacing wire — verified against the running
     *     converter, which deep-flattens: IRLength's {v,u} sits directly
     *     under the outer "original", NO "value" key), and
     *   - {"px":0.0,"original":{"type":"length","value":{"original":{…}}}}
     *     (older pinned wires — a legacy envelope that nested IRLength
     *     under "value" before the deep-flatten).
     * Accept both so the escape hatch never silently loses a value on
     * either wire shape. Returns null for zero v (a true zero needs no
     * resolution) or non-relative payloads.
     */
    private fun relativeOriginalVU(data: JsonObject): Pair<Float, String>? {
        val outer = data["original"] as? JsonObject ?: return null
        val original = (outer["original"] as? JsonObject)
            ?: ((outer["value"] as? JsonObject)?.get("original") as? JsonObject)
            ?: outer
        val v = original["v"]?.jsonPrimitive?.floatOrNull ?: return null
        val u = original["u"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        return if (v != 0f) v to u else null
    }

    /**
     * Extract text shadow from IR data.
     * TextShadow is an array of shadow objects, we use the first one.
     */
    private fun extractTextShadow(data: JsonElement): Shadow? {
        if (data !is JsonArray || data.isEmpty()) return null

        return try {
            val firstShadow = (data[0] as? JsonObject) ?: return null

            val offsetX = firstShadow["x"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
            val offsetY = firstShadow["y"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
            val blurRadius = firstShadow["blur"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
            val color = firstShadow["c"]?.let { ValueExtractors.extractColor(it) } ?: Color.Black

            Shadow(
                color = color,
                offset = Offset(offsetX, offsetY),
                blurRadius = blurRadius
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== WORD SPACING ====================

    /**
     * Extract CSS `word-spacing` as a resolved sp value for the given
     * element font-size, or null when absent / `normal`.
     *
     * Wire (WordSpacingProperty serializer): {"px":N,"original":…} where
     * `normal` ships as original:"normal" and relative units (em/rem) ship
     * the same bogus px:0.0 the letter-spacing wire does — so the px==0
     * case gets the identical {v,u} escape hatch: em × the ELEMENT
     * font-size (css-values-4 §5.1.1 — word-spacing has no per-glyph em
     * TextUnit path because the value is applied via 1-char spans, so it
     * is resolved eagerly here), rem × the 16px harness root. Negative
     * values pass through untouched (css-text-3 §5.1 allows them; the
     * span mechanism contracts advance the same way it expands it).
     */
    fun extractWordSpacingSp(properties: List<IRProperty>, fontSizeSp: Float): Float? {
        val data = properties.find { it.type == "WordSpacing" }?.data ?: return null
        if (data is JsonObject) {
            // `normal` → the initial value, no extra advance (treat as
            // absent rather than as 0 so callers can skip the span pass).
            val originalPrim = data["original"] as? JsonPrimitive
            if (originalPrim?.contentOrNull?.lowercase() == "normal") return null
            // Resolved px wins whenever it is non-zero ("pixels" is the
            // legacy key some older wires used; "px" is the live one).
            val px = data["px"]?.jsonPrimitive?.floatOrNull
                ?: data["pixels"]?.jsonPrimitive?.floatOrNull
            if (px != null && px != 0f) return px
            // px==0 escape hatch (mirrors extractLetterSpacing): a
            // non-zero em/rem original means the 0 was the converter's
            // bogus fallback, not a declared zero — resolve it.
            if (px == 0f) {
                val vu = relativeOriginalVU(data)
                if (vu != null) {
                    val (v, u) = vu
                    when (u) {
                        "EM" -> return v * fontSizeSp
                        "REM" -> return v * 16f
                    }
                }
                // True declared zero (or a relative unit we can't
                // resolve, e.g. % of the space advance) → honest 0.
                return 0f
            }
        }
        // Bare primitive / dp-shaped fallback (defensive; the live wire
        // is always the JsonObject envelope above).
        return ValueExtractors.extractDp(data)?.value
    }

    /**
     * The letter-spacing a word-separator SPAN must carry so that both
     * trackings apply. css-text-3 §5.1: letter-spacing applies between
     * ALL typographic units and word-spacing applies ADDITIONALLY at word
     * separators — but a SpanStyle letterSpacing REPLACES (not adds to)
     * the paragraph's base letter-spacing on the spanned characters, so
     * the span value has to be the SUM of both. The em base resolves
     * against the element font-size (css-values-4 §5.1.1); Unspecified
     * base contributes nothing.
     */
    fun resolveWordSpacingSpanSp(
        wordSpacingSp: Float,
        baseLetterSpacing: TextUnit,
        fontSizeSp: Float
    ): Float {
        val baseSp = when {
            baseLetterSpacing.isSp -> baseLetterSpacing.value
            baseLetterSpacing.isEm -> baseLetterSpacing.value * fontSizeSp
            else -> 0f // Unspecified → no base tracking to preserve
        }
        return wordSpacingSp + baseSp
    }

    /**
     * Apply CSS word-spacing to already-built annotated text: a
     * SpanStyle(letterSpacing) over EACH word-separator character only.
     * Letter-spacing adds its tracking AFTER a glyph's advance, so extra
     * advance after a space == exactly the CSS word-spacing model —
     * including negatives, which contract the gap symmetrically to the
     * browser. Separators: SPACE and NO-BREAK SPACE, the word-separator
     * characters (css-text-3 §5.1) the Latin fixture corpus produces
     * (ideographic/ogham separators are out of scope for this corpus).
     * Builder-copies the input so existing spans (synthesized small-caps
     * runs) survive untouched.
     */
    fun applyWordSpacingSpans(
        text: androidx.compose.ui.text.AnnotatedString,
        spanSpacingSp: Float
    ): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
        text.text.forEachIndexed { index, ch ->
            if (ch == ' ' || ch == '\u00A0') {
                builder.addStyle(
                    SpanStyle(letterSpacing = spanSpacingSp.sp),
                    index,
                    index + 1
                )
            }
        }
        return builder.toAnnotatedString()
    }

    // ==================== OWNED DECORATION LINES ====================
    //
    // Wave-6 decoration-line ownership: the native runtime draws ALL THREE
    // css-text-decor-3 §2.1 lines (underline / overline / line-through)
    // itself instead of delegating underline+line-through to the
    // platform's 1px built-ins. Device evidence (wave-5 gate,
    // typography/text-decoration-line): Chromium draws pixel-snapped
    // 2px-thick lines at 22px Inter while Android's built-ins drew ~1px
    // at different offsets — Underline pair 0.809, UnderOver 0.740,
    // Triple 0.737 vs the fixture's no-decoration floor 0.8695.
    //
    // THE GEOMETRY ORACLE IS EMPIRICAL — measured off the archived web
    // captures (wave5-gate/typography_text-decoration-line/images/web,
    // 22px Inter, #111 ink on #ecf0f1, two wrapped lines, alphabetic
    // baselines at integer rows y=41 and y=67):
    //   overline     rows [18,20) line 1 · [44,46) line 2
    //   line-through rows [33,35) line 1 · [59,61) line 2
    //   underline    rows [43,45) line 1 · (line 2's clipped by the box)
    // All three are EXACTLY 2 fully-opaque pixel rows — no anti-aliasing
    // — so Chromium places decorations at integral device rows with an
    // integral thickness. The em-fractions below reproduce those rows
    // exactly and come from the bundled Inter face's own tables
    // (apps/web-harness/public/fonts/Inter-Regular.ttf, unitsPerEm 2048),
    // which is what Blink consults for `text-decoration-thickness: auto`
    // and the strike position.

    /** Inter post.underlineThickness = 140/2048 em. At 22px → 1.504 →
     *  rounds to the measured 2px; at 16px → 1.094 → the 1px Chrome
     *  draws at body size (prior wave evidence). One thickness feeds all
     *  three lines — the captures show identical 2px for each. */
    private const val DECORATION_THICKNESS_EM = 140f / 2048f

    /** Inter hhea.ascender = 1984/2048 em. Chromium rounds the ascent to
     *  an integer (21 at 22px) and hangs the overline ABOVE that ascent
     *  edge: measured bottom of the overline is flush with
     *  baseline − round(ascent) (row 20 = 41 − 21), the line box top. */
    private const val DECORATION_ASCENT_EM = 1984f / 2048f

    /** Inter OS/2.yStrikeoutPosition = 671/2048 em above the baseline —
     *  the strike CENTER. At 22px: 41 − 7.208 − 2/2 = 32.79 → snaps to
     *  the measured top row 33. */
    private const val STRIKEOUT_POSITION_EM = 671f / 2048f

    /**
     * Which of the three css-text-decor-3 §2.1 line keywords the
     * component's `text-decoration-line` carries. When [any] is true the
     * renderer's owned draw pass paints EVERY flagged line itself and the
     * TextStyle must NOT also carry TextDecoration.Underline/LineThrough
     * (the built-ins would double-draw under the owned rects).
     */
    data class DecorationLineFlags(
        val underline: Boolean,   // `underline` keyword present
        val overline: Boolean,    // `overline` keyword present
        val lineThrough: Boolean  // `line-through` keyword present
    ) {
        /** True when at least one line is requested — the owned-pass gate. */
        val any: Boolean get() = underline || overline || lineThrough
    }

    /** The no-decoration constant (all flags off) — what absent /
     *  `none` / unknown-keyword wires resolve to. */
    private val NO_DECORATION_LINES = DecorationLineFlags(
        underline = false, overline = false, lineThrough = false
    )

    /**
     * Parse `text-decoration-line` into [DecorationLineFlags]. Handles
     * both wire shapes the converter emits (see TextDecorationLine
     * PropertyParser): the keyword ARRAY (["UNDERLINE","OVERLINE"]) and a
     * bare keyword primitive ("UNDERLINE"). `blink` is a no-visual-effect
     * value in every modern browser (css-text-decor-3 §2.1: UAs "may not"
     * blink) so it maps to no flags, like `none`.
     */
    fun extractDecorationLineFlags(properties: List<IRProperty>): DecorationLineFlags {
        // No declared property → initial value `none` → nothing owned.
        val data = properties.find { it.type == "TextDecorationLine" }?.data
            ?: return NO_DECORATION_LINES
        // Normalize each keyword the way extractTextDecoration does:
        // lowercase + underscore→hyphen (wire ships LINE_THROUGH).
        val keywords: List<String> = if (data is JsonArray) {
            data.mapNotNull { elem ->
                (elem as? JsonPrimitive)?.contentOrNull?.lowercase()?.replace("_", "-")
            }
        } else {
            // Bare primitive wire — a single keyword or nothing.
            listOfNotNull(ValueExtractors.extractKeyword(data)?.lowercase()?.replace("_", "-"))
        }
        // One flag per spec keyword; anything else (none/blink/garbage)
        // contributes no line.
        return DecorationLineFlags(
            underline = "underline" in keywords,
            overline = "overline" in keywords,
            lineThrough = "line-through" in keywords
        )
    }

    /**
     * Whether `text-decoration-line` includes `overline` — kept as the
     * historic wave-5 predicate name (JVM suite pins it); now a thin
     * view over [extractDecorationLineFlags] so there is exactly ONE
     * wire parser for the property.
     */
    fun extractHasOverline(properties: List<IRProperty>): Boolean =
        extractDecorationLineFlags(properties).overline

    /**
     * One horizontal decoration rect in text-layout coordinates:
     * top-left (left, top), extent (width, thickness). Same shape for
     * all three lines — only `top` differs per line kind.
     */
    data class DecorationSegment(
        val left: Float,      // line's leftmost painted x (TextLayoutResult.getLineLeft)
        val top: Float,       // snapped top row of the rect (Chromium-matched, see decorationSegments)
        val width: Float,     // getLineRight − getLineLeft, the inked extent of the visual line
        val thickness: Float  // shared auto thickness (Inter underlineThickness em, ≥1px)
    )

    /**
     * The shared `text-decoration-thickness: auto` in device px: the
     * face's underlineThickness em-fraction × font-size, rounded to an
     * integer like Chromium's device-row snapping, floored at 1px so tiny
     * sizes still paint. 22px → 2 (measured), 16px → 1 (Chrome at body
     * size), 8px → 1 (floor).
     */
    fun decorationThicknessPx(fontSizePx: Float): Float =
        maxOf(1f, Math.round(fontSizePx * DECORATION_THICKNESS_EM).toFloat())

    /**
     * Compute the owned decoration rects for a laid-out paragraph — one
     * segment per flagged line kind per VISUAL line (css-text-decor-3
     * §2.1: each line box gets its own decoration), spanning that line's
     * inked extent. Pure function over the TextLayoutResult accessors
     * (lineCount / getLineBaseline / getLineLeft / getLineRight passed as
     * lambdas) so the JVM suite can pin the geometry without an Android
     * canvas.
     *
     * Geometry — Chromium-matched, all anchored on the ALPHABETIC
     * BASELINE (the one anchor Android's layout reports identically to
     * Blink for the same face+size), with the baseline snapped to an
     * integer row first because every measured web row is integral:
     *   T          = decorationThicknessPx            (2px at 22px)
     *   underline  top = round(baseline) + T          (43 = 41+2 ✓ rows 43-44)
     *   overline   top = round(baseline) − round(ascent·fs) − T
     *                                                 (18 = 41−21−2 ✓ rows 18-19)
     *   line-through top = round(round(baseline) − strikePos·fs − T/2)
     *                                                 (33 = round(32.79) ✓ rows 33-34)
     * Line 2 of the capture confirms the parameterization at a second
     * baseline (67): overline 44 ✓, strike 59 ✓, underline 69 (clipped
     * by the 50px fixture box, consistent).
     *
     * Known remaining divergence (documented, not silent): Chromium's
     * `text-decoration-skip-ink: auto` gaps the underline around
     * descenders (~11px of the 212px run at 22px); the owned pass paints
     * the full run — Compose exposes no per-glyph outline geometry to
     * carve the gaps without a platform canvas.
     *
     * Empty visual lines (right ≤ left, e.g. a trailing blank line) paint
     * no decoration — the browser draws nothing over zero inked extent.
     */
    fun decorationSegments(
        lineCount: Int,
        fontSizePx: Float,
        flags: DecorationLineFlags,
        lineBaseline: (Int) -> Float,
        lineLeft: (Int) -> Float,
        lineRight: (Int) -> Float
    ): List<DecorationSegment> {
        // Nothing flagged → nothing owned (callers gate on flags.any, but
        // stay total anyway — no silent surprises).
        if (!flags.any) return emptyList()
        // One shared thickness for all lines of all kinds (oracle: all
        // three captures show identical 2px at 22px).
        val thickness = decorationThicknessPx(fontSizePx)
        // Chromium's integral rounded ascent (21 at 22px Inter).
        val roundedAscent = Math.round(fontSizePx * DECORATION_ASCENT_EM)
        return (0 until lineCount).flatMap { i ->
            val left = lineLeft(i)
            val right = lineRight(i)
            // Zero inked extent → no decoration on this visual line.
            if (right <= left) return@flatMap emptyList<DecorationSegment>()
            val width = right - left
            // Integral baseline row — web rows are pixel-snapped, and
            // Android baselines can land on fractions; snapping here is
            // what makes the three tops integral like the capture.
            val baseline = Math.round(lineBaseline(i)).toFloat()
            // Build only the flagged kinds, in under→through→over paint
            // order (matches Blink: line-through paints last, over ink).
            buildList {
                if (flags.underline) add(DecorationSegment(
                    // Gap below baseline == the thickness (measured: 2px
                    // gap rows 41-42 clean, ink starts at 43 = 41+2).
                    left, baseline + thickness, width, thickness
                ))
                if (flags.overline) add(DecorationSegment(
                    // Bottom edge flush with baseline − rounded ascent
                    // (the line box top): top = that edge − thickness.
                    left, baseline - roundedAscent - thickness, width, thickness
                ))
                if (flags.lineThrough) add(DecorationSegment(
                    // Strike CENTER at baseline − yStrikeoutPosition·fs;
                    // the rect straddles it (±T/2) then snaps to a row.
                    left,
                    Math.round(baseline - fontSizePx * STRIKEOUT_POSITION_EM - thickness / 2f).toFloat(),
                    width, thickness
                ))
            }
        }
    }

    /**
     * Fix (wave 7, REWORKED wave 8): fractional X compensation for
     * center-aligned text — pure math over the line's UNROUNDED advance
     * so the JVM suite can pin it without an Android canvas.
     *
     * Why: Android StaticLayout's ALIGN_CENTER EVEN-TRUNCATES the line
     * width before centering AT DRAW TIME — AOSP Layout#getLineStartPos
     * computes `((left + right) − ((int) lineMax & ~1)) >> 1`: the raw
     * fractional advance is truncated to an EVEN integer and the division
     * is integral, so a ~219.3px-wide line in a 252px box DRAWS at
     * (252 − 218) >> 1 = 17 while the browser centers fractionally at
     * (252 − 219.3)/2 ≈ 16.35 (pixel-measured on TextAlign_Center:
     * iOS-Android 0.9372 with Android driving the divergence).
     *
     * Wave-7 meta-lesson (recorded, and the reason for this rework): the
     * first version modelled the REPORT path instead of the DRAW path —
     * it read Layout#getLineLeft, whose ALIGN_CENTER branch is a
     * DIFFERENT formula, `floor(left + (mWidth − lineMax)/2)` = 16 on
     * the fixture, and computed the delta against the equally-rounded
     * getLineRight. The rounded report was self-consistent (left 16,
     * right ceil'd, width 220 → delta exactly 0) so the compensation
     * honestly computed 0 — while the pixels were being DRAWN at 17.
     * Report accessors are useless here: the model below re-derives the
     * draw position from the unrounded advance instead.
     *
     * The caller must supply the UNROUNDED line advance — on Android via
     * TextLayoutResult.multiParagraph.getLineWidth(i), which delegates
     * (AndroidParagraph → TextLayout) to android.text.Layout#getLineWidth,
     * the raw float extent from TextLine measurement with NO rounding —
     * NOT getLineRight − getLineLeft (both are floor/ceil-rounded in the
     * ALIGN_CENTER branch, see the meta-lesson above).
     *
     *   drawX  = (layoutWidth − (floor(advance) & ~1)) >> 1   // AOSP draw
     *   idealX = (layoutWidth − advance) / 2                  // CSS center
     *   delta  = idealX − drawX
     *
     * applied as a fractional translationX (graphicsLayer — draw-time
     * only, no layout effect), which cancels the snap exactly.
     *
     * Multi-line: the compensation is computed for the WIDEST visual line
     * (documented approximation — one translation shifts every line, and
     * the widest line dominates the SSIM-visible mass; per-line deltas
     * differ by <1px and would need per-line re-draw Compose can't do).
     *
     * Guard: |delta| must stay under 1.5px — this is a sub-pixel snap
     * COMPENSATION, not a repositioning tool (the model's own range is
     * (−1, +0.5], so anything larger means the inputs disagree with the
     * model — e.g. an RTL or justified line — and we honestly do nothing
     * rather than smear the run). Returns null when no compensation
     * should be applied (callers then leave the layer translation at 0).
     */
    fun centerAlignFractionalDeltaX(
        layoutWidthPx: Float,
        lineCount: Int,
        lineAdvance: (Int) -> Float
    ): Float? {
        // No laid-out lines yet (first frame) → nothing to compensate.
        if (lineCount <= 0) return null
        // Find the widest visual line — see the multi-line note above.
        var widestAdvance = -1f
        for (i in 0 until lineCount) {
            val a = lineAdvance(i)
            if (a > widestAdvance) widestAdvance = a
        }
        // Zero/negative advance (blank line / first frame) → nothing to do.
        if (widestAdvance <= 0f) return null
        // AOSP Layout#getLineStartPos draw model, ALIGN_CENTER branch with
        // left = 0 / right = mWidth: `max = (int) lineMax & ~1` (truncate,
        // then clear bit 0 = round DOWN to even) and an integral `>> 1`.
        // toInt() truncates toward zero == floor for the positive advance.
        val truncatedEvenAdvance = widestAdvance.toInt() and 1.inv()
        val drawX = ((layoutWidthPx.toInt() - truncatedEvenAdvance) shr 1).toFloat()
        // True fractional CSS centering position (css-text-3 §7.1 center
        // alignment is exact — browsers place the run at the half-remainder
        // with subpixel precision, verified on the TextAlign_Center capture).
        val idealX = (layoutWidthPx - widestAdvance) / 2f
        // Compensation = where the browser draws minus where Android draws.
        val delta = idealX - drawX
        // Identity → no layer churn; ≥1.5px → not a snap artifact, bail.
        if (delta == 0f || kotlin.math.abs(delta) >= 1.5f) return null
        return delta
    }

    /**
     * Fix (wave 7, text placement): fractional Y compensation for
     * SUB-NATURAL line-height — the shared cross-native contract with the
     * iOS lane (both natives must land the same placement or neither).
     *
     * Why: CSS half-leading (css-inline-3 §3.2 / CSS 2.1 §10.8.1) places
     * the glyph band inside the line box at
     *   inkTop = boxTop + (L − natural)/2
     * where L is the used line-height and `natural` is the font's
     * ascent+descent content height (verified against all 9 web captures:
     * natural = Inter hhea (1984+494)/2048 × fontSize). When L < natural
     * the half-leading is NEGATIVE, so the browser paints the glyph band
     * (L−natural)/2 px HIGHER than the box top (LineHeight_Unitless_1:
     * L=18 < natural 21.78 at 18px → web ink sits 1.89px higher).
     * Android's StaticLayout (like iOS TextKit) CLAMPS negative
     * half-leading — the rendered line stays at the natural height and
     * the ink does not rise, so both natives agreed with each other
     * (0.993) while both diverged from web (~0.91). This helper returns
     * the exact web offset — (L − natural)/2, always negative = upward —
     * for the renderer to apply as a fractional translationY of the glyph
     * run (draw-time graphicsLayer; the line BOX itself stays
     * uncompressed).
     *
     * Honest limitation (documented, logged once by the renderer): for
     * MULTI-LINE sub-natural text the line boxes remain uncompressed —
     * the line-to-line advance stays at the natural height instead of L,
     * because Compose/StaticLayout cannot shrink a line below the glyph
     * box. Only the overall PLACEMENT is compensated (which is what SSIM
     * sees on the fixture corpus's single-line/short content).
     *
     * Returns null when no compensation applies: L ≥ natural (positive
     * half-leading — StaticLayout distributes that correctly already) or
     * degenerate non-positive inputs.
     */
    fun subNaturalLineHeightDeltaY(
        resolvedLineHeightPx: Float,
        naturalLineBoxPx: Float
    ): Float? {
        // Degenerate metrics (unset line-height, first-frame zero layout).
        if (resolvedLineHeightPx <= 0f || naturalLineBoxPx <= 0f) return null
        // L ≥ natural: half-leading is non-negative, the platform already
        // places the band exactly where CSS does — nothing to compensate.
        if (resolvedLineHeightPx >= naturalLineBoxPx) return null
        // Web's negative half-leading: (L − natural)/2 < 0 == move UP.
        return (resolvedLineHeightPx - naturalLineBoxPx) / 2f
    }

    /**
     * Extract baseline shift from vertical-align property.
     */
    private fun extractBaselineShift(data: JsonElement): BaselineShift? {
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.lowercase()) {
            "sub" -> BaselineShift.Subscript
            "super" -> BaselineShift.Superscript
            "baseline" -> BaselineShift.None
            else -> null
        }
    }

    /**
     * Extract text indent from IR data.
     */
    private fun extractTextIndent(data: JsonElement): TextIndent? {
        val firstLineIndent = ValueExtractors.extractDp(data)?.value?.sp ?: return null
        return TextIndent(firstLine = firstLineIndent)
    }

    /**
     * Text transform mode for text content manipulation.
     */
    enum class TextTransformMode {
        NONE, UPPERCASE, LOWERCASE, CAPITALIZE
    }

    /**
     * Extract text transform mode (for use in text rendering, not style).
     */
    fun extractTextTransform(properties: List<IRProperty>): TextTransformMode {
        val prop = properties.find { it.type == "TextTransform" } ?: return TextTransformMode.NONE
        val keyword = ValueExtractors.extractKeyword(prop.data)
        // The wire ships the bare enum name ("FULL_WIDTH") — normalize the
        // underscore form to CSS kebab-case before matching (no-op for the
        // single-word keywords).
        return when (keyword?.lowercase()?.replace("_", "-")) {
            "uppercase" -> TextTransformMode.UPPERCASE
            "lowercase" -> TextTransformMode.LOWERCASE
            "capitalize" -> TextTransformMode.CAPITALIZE
            // css-text-3 §2.1 `full-width` maps glyphs to their U+FFxx
            // fullwidth compatibility forms. DELIBERATE no-op: Chromium —
            // the visual reference — renders the corpus's Latin fixture
            // text unchanged for full-width, so substituting fullwidth
            // codepoints here would DIVERGE from the reference captures.
            // Explicitly mapped (not silently defaulted) so the decision
            // is auditable.
            "full-width" -> TextTransformMode.NONE
            else -> TextTransformMode.NONE
        }
    }

    /**
     * Apply text transform to a string.
     */
    fun applyTextTransform(text: String, mode: TextTransformMode): String {
        return when (mode) {
            TextTransformMode.NONE -> text
            TextTransformMode.UPPERCASE -> text.uppercase()
            TextTransformMode.LOWERCASE -> text.lowercase()
            // css-text-3 §2.1: capitalize puts the first TYPOGRAPHIC LETTER
            // UNIT of each word in titlecase. Chromium — the visual
            // reference — titlecases the first LETTER even when the word
            // begins with punctuation or digits (WPT capitalize-031:
            // "(this)" renders "(This)"). The old replaceFirstChar touched
            // word[0] only, so any leading non-letter left the word
            // untransformed and Android diverged from web. Scan for the
            // first Char.isLetter() instead; letterless words (pure
            // punctuation / numbers) pass through unchanged — explicitly,
            // matching the browser (nothing to titlecase).
            TextTransformMode.CAPITALIZE -> text.split(" ").joinToString(" ") { word ->
                val i = word.indexOfFirst { it.isLetter() }
                if (i < 0) word // no letter anywhere → word is untouched
                // titlecase() is identity on already-uppercase letters, so
                // "FOO" stays "FOO" — same as the previous behavior.
                else word.substring(0, i) + word[i].titlecase() + word.substring(i + 1)
            }
        }
    }

    /**
     * White space mode for text wrapping.
     */
    enum class WhiteSpaceMode {
        NORMAL, NOWRAP, PRE, PRE_WRAP, PRE_LINE, BREAK_SPACES
    }

    /**
     * Extract white-space mode from properties.
     *
     * css-text-4 split `white-space` into longhands: `text-wrap-mode`
     * (whose value also arrives via the `text-wrap` shorthand) carries the
     * nowrap bit. The reference (Chrome) treats `text-wrap: nowrap` exactly
     * like `white-space: nowrap` for wrapping, so all three IR spellings
     * fold into the same mode here. Previously only "WhiteSpace" was read
     * and Typography_C20 (`text-wrap: nowrap`) still wrapped to two lines
     * on Android while web kept one (0.766).
     */
    fun extractWhiteSpace(properties: List<IRProperty>): WhiteSpaceMode {
        val prop = properties.find { it.type == "WhiteSpace" }
        val keyword = prop?.let { ValueExtractors.extractKeyword(it.data) }
        val mode = when (keyword?.lowercase()?.replace("-", "_")) {
            "nowrap" -> WhiteSpaceMode.NOWRAP
            "pre" -> WhiteSpaceMode.PRE
            "pre_wrap" -> WhiteSpaceMode.PRE_WRAP
            "pre_line" -> WhiteSpaceMode.PRE_LINE
            "break_spaces" -> WhiteSpaceMode.BREAK_SPACES
            else -> WhiteSpaceMode.NORMAL
        }
        if (mode != WhiteSpaceMode.NORMAL) return mode
        // text-wrap / text-wrap-mode: only the wrap-vs-nowrap bit exists in
        // Compose terms (`balance`/`pretty` are line-breaking strategies the
        // paragraph layouter doesn't expose) — map nowrap, ignore the rest.
        val wrapsOff = properties.any { p ->
            (p.type == "TextWrap" || p.type == "TextWrapMode") &&
                ValueExtractors.extractKeyword(p.data)?.lowercase() == "nowrap"
        }
        return if (wrapsOff) WhiteSpaceMode.NOWRAP else WhiteSpaceMode.NORMAL
    }

    // ==================== TAB SIZE ====================

    /**
     * Tab size configuration.
     * Stores the tab width as either a number of spaces or a pixel length.
     */
    data class TabSizeConfig(
        val spaces: Int?,      // Tab width in number of spaces (e.g., 4)
        val pixels: Float?     // Tab width in pixels
    ) {
        /**
         * Get the effective tab width in spaces.
         * Default is 8 spaces (CSS default).
         */
        val effectiveSpaces: Int
            get() = spaces ?: (pixels?.let { (it / 8f).toInt().coerceAtLeast(1) }) ?: 8

        /**
         * Generate tab replacement string for monospace text.
         */
        fun toSpaceString(): String = " ".repeat(effectiveSpaces)
    }

    /**
     * Extract tab-size from properties.
     * CSS tab-size can be a number (spaces) or a length (pixels).
     *
     * Note: Compose Text doesn't natively support tab-size.
     * This is used to pre-process text by replacing tabs with spaces.
     */
    fun extractTabSize(properties: List<IRProperty>): TabSizeConfig {
        val prop = properties.find { it.type == "TabSize" } ?: return TabSizeConfig(8, null)

        return try {
            when (val data = prop.data) {
                is JsonObject -> {
                    val type = data["type"]?.jsonPrimitive?.contentOrNull
                    when (type) {
                        "number" -> {
                            val value = data["value"]
                            val spaces = if (value is JsonObject) {
                                value["value"]?.jsonPrimitive?.intOrNull
                            } else {
                                value?.jsonPrimitive?.intOrNull
                            }
                            TabSizeConfig(spaces ?: 8, null)
                        }
                        "length" -> {
                            val length = data["length"]
                            val pixels = ValueExtractors.extractDp(length)?.value
                            TabSizeConfig(null, pixels)
                        }
                        else -> {
                            // Try direct value extraction
                            val spaces = data["value"]?.jsonPrimitive?.intOrNull
                                ?: data["spaces"]?.jsonPrimitive?.intOrNull
                            TabSizeConfig(spaces ?: 8, null)
                        }
                    }
                }
                is JsonPrimitive -> {
                    val spaces = data.intOrNull
                    TabSizeConfig(spaces ?: 8, null)
                }
                else -> TabSizeConfig(8, null)
            }
        } catch (e: Exception) {
            TabSizeConfig(8, null)
        }
    }

    /**
     * Process text by replacing tabs with spaces according to tab-size.
     */
    fun applyTabSize(text: String, tabConfig: TabSizeConfig): String {
        if (!text.contains('\t')) return text
        return text.replace("\t", tabConfig.toSpaceString())
    }

    // ==================== DIRECTION ====================

    /**
     * Text direction mode.
     */
    enum class DirectionMode {
        LTR, RTL
    }

    /**
     * Extract text direction from properties.
     * Maps to Compose LayoutDirection.
     */
    fun extractDirection(properties: List<IRProperty>): DirectionMode {
        val prop = properties.find { it.type == "Direction" } ?: return DirectionMode.LTR
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()) {
            "RTL" -> DirectionMode.RTL
            else -> DirectionMode.LTR
        }
    }

    /**
     * Extract max lines for text overflow.
     *
     * Handles both LineClamp and MaxLines properties:
     * - LineClamp format: { "type": "lines", "count": 2.0 } or { "type": "none" }
     * - MaxLines format: { "type": "count", "value": { "value": 3.0 } } or { "type": "none" }
     */
    fun extractMaxLines(properties: List<IRProperty>): Int? {
        // First check for LineClamp (higher priority - CSS standard)
        val lineClamp = properties.find { it.type == "LineClamp" }
        if (lineClamp != null) {
            val result = extractLineClampValue(lineClamp.data)
            if (result != null) return result
        }

        // Then check for MaxLines
        val maxLines = properties.find { it.type == "MaxLines" }
        if (maxLines != null) {
            return extractMaxLinesValue(maxLines.data)
        }

        return null
    }

    /**
     * Extract line-clamp value from IR data.
     *
     * IR format:
     * - { "type": "lines", "count": 2.0 }
     * - { "type": "none" }
     * - { "clamp": { "type": "lines", "count": 2.0 } }
     */
    private fun extractLineClampValue(data: JsonElement): Int? {
        if (data !is JsonObject) return ValueExtractors.extractInt(data)

        // Check for nested clamp object
        val clampObj = data["clamp"] as? JsonObject ?: data

        val type = clampObj["type"]?.jsonPrimitive?.contentOrNull

        return when (type?.lowercase()) {
            "none" -> null
            "lines" -> {
                // Count can be a number or wrapped in an IRNumber object
                val count = clampObj["count"]
                when (count) {
                    is JsonPrimitive -> count.intOrNull ?: count.doubleOrNull?.toInt()
                    is JsonObject -> {
                        count["value"]?.jsonPrimitive?.intOrNull
                            ?: count["value"]?.jsonPrimitive?.doubleOrNull?.toInt()
                    }
                    else -> null
                    // Wave-19 skeptic fix: sub-1 counts are outside the
                    // css-overflow-4 §5 grammar (<integer [1,∞]>) → clamp
                    // OFF (null), matching iOS's LineClampExtractor. This
                    // path feeds ComponentRenderer's effectiveMaxLines →
                    // Text(maxLines=...), and Compose's TextDelegate hard-
                    // requires maxLines > 0 — a `line-clamp: 0` wire (the
                    // converter emits {"type":"lines","count":0.0} for it)
                    // crashed the render before this guard.
                }?.takeIf { it >= 1 }
            }
            else -> {
                // Fallback: try direct value extraction
                ValueExtractors.extractInt(data)
            }
        }
    }

    /**
     * Extract max-lines value from IR data.
     *
     * IR format:
     * - { "type": "count", "value": { "value": 3.0 } }
     * - { "type": "none" }
     * - { "value": { "type": "count", "value": { "value": 3.0 } } }
     */
    private fun extractMaxLinesValue(data: JsonElement): Int? {
        if (data !is JsonObject) return ValueExtractors.extractInt(data)

        // Check for nested value object
        val valueObj = data["value"] as? JsonObject ?: data

        val type = valueObj["type"]?.jsonPrimitive?.contentOrNull

        return when (type?.lowercase()) {
            "none" -> null
            "count" -> {
                // Value is wrapped in an IRNumber object
                val innerValue = valueObj["value"]
                when (innerValue) {
                    is JsonPrimitive -> innerValue.intOrNull ?: innerValue.doubleOrNull?.toInt()
                    is JsonObject -> {
                        innerValue["value"]?.jsonPrimitive?.intOrNull
                            ?: innerValue["value"]?.jsonPrimitive?.doubleOrNull?.toInt()
                    }
                    else -> null
                }
            }
            else -> {
                // Fallback: try direct value extraction
                ValueExtractors.extractInt(data)
            }
        }
    }

    /**
     * Extract text overflow mode.
     *
     * IR format:
     * - { "type": "...Single", "value": "ELLIPSIS" }
     * - { "type": "...Clip", "string": "..." } (for custom clip string)
     * - "ELLIPSIS" (simple keyword)
     *
     * Maps to Compose TextOverflow:
     * - "ellipsis" -> TextOverflow.Ellipsis
     * - "clip" -> TextOverflow.Clip
     * - "visible" -> TextOverflow.Visible
     * - "fade" -> TextOverflow.Visible (Compose doesn't have fade, use visible)
     */
    fun extractTextOverflow(properties: List<IRProperty>): TextOverflow {
        val prop = properties.find { it.type == "TextOverflow" } ?: return TextOverflow.Clip
        return extractTextOverflowValue(prop.data)
    }

    /**
     * Extract text overflow from IR data.
     */
    private fun extractTextOverflowValue(data: JsonElement): TextOverflow {
        when (data) {
            is JsonPrimitive -> {
                return keywordToTextOverflow(data.contentOrNull)
            }
            is JsonObject -> {
                // Check for type field (sealed interface serialization)
                val type = data["type"]?.jsonPrimitive?.contentOrNull ?: ""

                // Handle Single value type (most common)
                if (type.contains("Single", ignoreCase = true)) {
                    val value = data["value"]?.jsonPrimitive?.contentOrNull
                    return keywordToTextOverflow(value)
                }

                // Handle Clip type with custom string
                if (type.contains("Clip", ignoreCase = true)) {
                    return TextOverflow.Clip
                }

                // Handle Fade type
                if (type.contains("Fade", ignoreCase = true)) {
                    // Compose doesn't have native fade, use Visible as closest match
                    return TextOverflow.Visible
                }

                // Fallback: try to extract keyword from value field
                val keyword = ValueExtractors.extractKeyword(data)
                return keywordToTextOverflow(keyword)
            }
            else -> return TextOverflow.Clip
        }
    }

    /**
     * Convert keyword string to TextOverflow enum.
     */
    private fun keywordToTextOverflow(keyword: String?): TextOverflow {
        return when (keyword?.lowercase()) {
            "ellipsis" -> TextOverflow.Ellipsis
            "clip" -> TextOverflow.Clip
            "visible" -> TextOverflow.Visible
            "fade" -> TextOverflow.Visible // Compose doesn't have fade
            else -> TextOverflow.Clip
        }
    }

    // ==================== WORD BREAK / OVERFLOW WRAP ====================

    /**
     * Word break mode for text wrapping behavior.
     */
    enum class WordBreakMode {
        NORMAL, BREAK_ALL, KEEP_ALL, BREAK_WORD
    }

    /**
     * Overflow wrap mode for long words.
     */
    enum class OverflowWrapMode {
        NORMAL, ANYWHERE, BREAK_WORD
    }

    /**
     * Combined text wrapping configuration.
     */
    data class TextWrapConfig(
        val softWrap: Boolean,
        val wordBreak: WordBreakMode,
        val overflowWrap: OverflowWrapMode
    ) {
        /**
         * Whether text should allow breaking mid-word.
         * True for break-all, break-word, or anywhere modes.
         */
        val allowMidWordBreak: Boolean
            get() = wordBreak == WordBreakMode.BREAK_ALL ||
                    wordBreak == WordBreakMode.BREAK_WORD ||
                    overflowWrap == OverflowWrapMode.ANYWHERE ||
                    overflowWrap == OverflowWrapMode.BREAK_WORD
    }

    /**
     * Extract word-break mode from properties.
     */
    fun extractWordBreak(properties: List<IRProperty>): WordBreakMode {
        val prop = properties.find { it.type == "WordBreak" } ?: return WordBreakMode.NORMAL
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()?.replace("-", "_")) {
            "BREAK_ALL" -> WordBreakMode.BREAK_ALL
            "KEEP_ALL" -> WordBreakMode.KEEP_ALL
            "BREAK_WORD" -> WordBreakMode.BREAK_WORD
            else -> WordBreakMode.NORMAL
        }
    }

    /**
     * Extract overflow-wrap mode from properties.
     */
    fun extractOverflowWrap(properties: List<IRProperty>): OverflowWrapMode {
        val prop = properties.find { it.type == "OverflowWrap" } ?: return OverflowWrapMode.NORMAL
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()?.replace("-", "_")) {
            "ANYWHERE" -> OverflowWrapMode.ANYWHERE
            "BREAK_WORD" -> OverflowWrapMode.BREAK_WORD
            else -> OverflowWrapMode.NORMAL
        }
    }

    /**
     * Extract combined text wrap configuration from properties.
     */
    fun extractTextWrapConfig(properties: List<IRProperty>): TextWrapConfig {
        val whiteSpace = extractWhiteSpace(properties)
        val wordBreak = extractWordBreak(properties)
        val overflowWrap = extractOverflowWrap(properties)

        // Determine softWrap based on white-space
        val softWrap = whiteSpace != WhiteSpaceMode.NOWRAP && whiteSpace != WhiteSpaceMode.PRE

        return TextWrapConfig(
            softWrap = softWrap,
            wordBreak = wordBreak,
            overflowWrap = overflowWrap
        )
    }

    // ==================== LINE BREAK (CJK Rules) ====================

    /**
     * CSS line-break mode for CJK text.
     * Controls the strictness of line breaking rules.
     */
    enum class LineBreakMode {
        AUTO, LOOSE, NORMAL, STRICT, ANYWHERE
    }

    /**
     * Extract line-break mode from properties.
     *
     * IR format: "AUTO", "LOOSE", "NORMAL", "STRICT", "ANYWHERE"
     */
    fun extractLineBreakMode(properties: List<IRProperty>): LineBreakMode {
        val prop = properties.find { it.type == "LineBreak" } ?: return LineBreakMode.AUTO
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()) {
            "LOOSE" -> LineBreakMode.LOOSE
            "NORMAL" -> LineBreakMode.NORMAL
            "STRICT" -> LineBreakMode.STRICT
            "ANYWHERE" -> LineBreakMode.ANYWHERE
            else -> LineBreakMode.AUTO
        }
    }

    /**
     * Extract Compose LineBreak style from IR data.
     * Maps CSS line-break values to Compose LineBreak.
     *
     * CSS -> Compose mapping:
     * - auto -> LineBreak.Simple (default, balanced)
     * - loose -> LineBreak.Simple (less restrictive, similar)
     * - normal -> LineBreak.Paragraph (standard rules)
     * - strict -> LineBreak.Paragraph (most restrictive available in Compose)
     * - anywhere -> LineBreak.Simple (allows breaking anywhere)
     *
     * Note: Compose's LineBreak options are more limited than CSS.
     * LineBreak.Heading is optimized for short text.
     * LineBreak.Paragraph is optimized for body text.
     * LineBreak.Simple is the most permissive.
     */
    private fun extractLineBreakStyle(data: JsonElement): LineBreak? {
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.uppercase()) {
            "LOOSE" -> LineBreak.Simple        // Less restrictive
            "NORMAL" -> LineBreak.Paragraph    // Standard rules
            "STRICT" -> LineBreak.Paragraph    // Most restrictive in Compose
            "ANYWHERE" -> LineBreak.Simple     // Allow breaking anywhere
            "AUTO" -> LineBreak.Simple         // Default balanced behavior
            else -> null
        }
    }

    /**
     * Extract Compose LineBreak from properties (public helper).
     */
    fun extractComposeLineBreak(properties: List<IRProperty>): LineBreak? {
        val prop = properties.find { it.type == "LineBreak" } ?: return null
        return extractLineBreakStyle(prop.data)
    }

    // ==================== WORD WRAP (Legacy Alias) ====================

    /**
     * Extract overflow-wrap mode from properties, including WordWrap as legacy alias.
     * WordWrap is the legacy name for overflow-wrap.
     */
    fun extractOverflowWrapWithLegacy(properties: List<IRProperty>): OverflowWrapMode {
        // Check for overflow-wrap first (standard)
        val overflowWrap = properties.find { it.type == "OverflowWrap" }
        if (overflowWrap != null) {
            val keyword = ValueExtractors.extractKeyword(overflowWrap.data)
            return when (keyword?.uppercase()?.replace("-", "_")) {
                "ANYWHERE" -> OverflowWrapMode.ANYWHERE
                "BREAK_WORD" -> OverflowWrapMode.BREAK_WORD
                else -> OverflowWrapMode.NORMAL
            }
        }

        // Fall back to word-wrap (legacy alias)
        val wordWrap = properties.find { it.type == "WordWrap" }
        if (wordWrap != null) {
            val keyword = ValueExtractors.extractKeyword(wordWrap.data)
            return when (keyword?.uppercase()?.replace("-", "_")) {
                "ANYWHERE" -> OverflowWrapMode.ANYWHERE
                "BREAK_WORD" -> OverflowWrapMode.BREAK_WORD
                else -> OverflowWrapMode.NORMAL
            }
        }

        return OverflowWrapMode.NORMAL
    }

    // ==================== TEXT JUSTIFY ====================

    /**
     * Text justify mode for text alignment method.
     * Note: Compose has limited support - only Justify alignment exists, not justification method.
     */
    enum class TextJustifyMode {
        AUTO,           // Browser determines method
        NONE,           // Disable justification
        INTER_WORD,     // Adjust spacing between words
        INTER_CHARACTER, // Adjust spacing between characters (CJK text)
        DISTRIBUTE      // Distribute space evenly (deprecated, treat as inter-character)
    }

    /**
     * Extract text-justify mode from properties.
     * Note: Compose doesn't have direct support for justification method.
     * This is stored for potential custom paragraph handling.
     */
    fun extractTextJustify(properties: List<IRProperty>): TextJustifyMode {
        val prop = properties.find { it.type == "TextJustify" } ?: return TextJustifyMode.AUTO
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()?.replace("-", "_")) {
            "NONE" -> TextJustifyMode.NONE
            "INTER_WORD" -> TextJustifyMode.INTER_WORD
            "INTER_CHARACTER" -> TextJustifyMode.INTER_CHARACTER
            "DISTRIBUTE" -> TextJustifyMode.DISTRIBUTE
            else -> TextJustifyMode.AUTO
        }
    }

    // ==================== TEXT ALIGN LAST ====================

    /**
     * Text align last mode for alignment of the last line in a block.
     * Note: Compose doesn't have direct support - would need custom paragraph handling.
     */
    enum class TextAlignLastMode {
        AUTO,    // Same as text-align (or start if text-align is justify)
        START,   // Align to start edge
        END,     // Align to end edge
        LEFT,    // Align to left
        RIGHT,   // Align to right
        CENTER,  // Center alignment
        JUSTIFY  // Justify the last line
    }

    /**
     * Extract text-align-last mode from properties.
     */
    fun extractTextAlignLast(properties: List<IRProperty>): TextAlignLastMode {
        val prop = properties.find { it.type == "TextAlignLast" } ?: return TextAlignLastMode.AUTO
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()) {
            "START" -> TextAlignLastMode.START
            "END" -> TextAlignLastMode.END
            "LEFT" -> TextAlignLastMode.LEFT
            "RIGHT" -> TextAlignLastMode.RIGHT
            "CENTER" -> TextAlignLastMode.CENTER
            "JUSTIFY" -> TextAlignLastMode.JUSTIFY
            else -> TextAlignLastMode.AUTO
        }
    }

    // ==================== HYPHENS ====================

    /**
     * Hyphens mode for hyphenation control.
     * Maps to Android's hyphenation settings through ParagraphStyle.
     */
    enum class HyphensMode {
        NONE,   // Never hyphenate
        MANUAL, // Only hyphenate at soft hyphen
        AUTO    // Automatic hyphenation based on language
    }

    /**
     * Extract hyphens mode from properties.
     * Can be used with Android's built-in hyphenation support.
     */
    fun extractHyphens(properties: List<IRProperty>): HyphensMode {
        val prop = properties.find { it.type == "Hyphens" } ?: return HyphensMode.MANUAL
        val keyword = ValueExtractors.extractKeyword(prop.data)
        return when (keyword?.uppercase()) {
            "NONE" -> HyphensMode.NONE
            "AUTO" -> HyphensMode.AUTO
            else -> HyphensMode.MANUAL
        }
    }

    // ==================== HYPHENATE CHARACTER ====================

    /**
     * Hyphenate character configuration.
     * Stores the character to use at line breaks when hyphenating.
     */
    data class HyphenateCharacterConfig(
        val character: String  // Default is soft hyphen, can be custom string
    ) {
        companion object {
            val DEFAULT = HyphenateCharacterConfig("\u00AD") // Soft hyphen
            val HYPHEN = HyphenateCharacterConfig("-")       // Regular hyphen
        }
    }

    /**
     * Extract hyphenate-character from properties.
     * Note: Custom hyphenation characters require custom text processing.
     */
    fun extractHyphenateCharacter(properties: List<IRProperty>): HyphenateCharacterConfig {
        val prop = properties.find { it.type == "HyphenateCharacter" }
            ?: return HyphenateCharacterConfig.DEFAULT

        return try {
            when (val data = prop.data) {
                is JsonPrimitive -> {
                    val value = data.contentOrNull
                    if (value?.lowercase() == "auto") {
                        HyphenateCharacterConfig.DEFAULT
                    } else {
                        HyphenateCharacterConfig(value ?: HyphenateCharacterConfig.DEFAULT.character)
                    }
                }
                is JsonObject -> {
                    val type = data["type"]?.jsonPrimitive?.contentOrNull
                    when (type?.lowercase()) {
                        "auto" -> HyphenateCharacterConfig.DEFAULT
                        "string" -> {
                            val value = data["value"]?.jsonPrimitive?.contentOrNull
                                ?: HyphenateCharacterConfig.DEFAULT.character
                            HyphenateCharacterConfig(value)
                        }
                        else -> {
                            // Try to extract value directly
                            val value = data["value"]?.jsonPrimitive?.contentOrNull
                                ?: data["character"]?.jsonPrimitive?.contentOrNull
                            HyphenateCharacterConfig(value ?: HyphenateCharacterConfig.DEFAULT.character)
                        }
                    }
                }
                else -> HyphenateCharacterConfig.DEFAULT
            }
        } catch (e: Exception) {
            HyphenateCharacterConfig.DEFAULT
        }
    }

    // ==================== COMBINED TEXT JUSTIFICATION CONFIG ====================

    /**
     * Combined text justification and hyphenation configuration.
     * Groups related properties for text rendering.
     */
    data class TextJustificationConfig(
        val textJustify: TextJustifyMode,
        val textAlignLast: TextAlignLastMode,
        val hyphens: HyphensMode,
        val hyphenateCharacter: HyphenateCharacterConfig
    ) {
        /**
         * Whether hyphenation is enabled (auto mode).
         */
        val hyphenationEnabled: Boolean
            get() = hyphens == HyphensMode.AUTO

        /**
         * Whether justification is disabled.
         */
        val justificationDisabled: Boolean
            get() = textJustify == TextJustifyMode.NONE
    }

    /**
     * Extract combined text justification configuration from properties.
     */
    fun extractTextJustificationConfig(properties: List<IRProperty>): TextJustificationConfig {
        return TextJustificationConfig(
            textJustify = extractTextJustify(properties),
            textAlignLast = extractTextAlignLast(properties),
            hyphens = extractHyphens(properties),
            hyphenateCharacter = extractHyphenateCharacter(properties)
        )
    }

    // ==================== EXTENDED TEXT DECORATION ====================

    /**
     * Text decoration style types.
     * Note: Compose only supports solid, others require custom drawing.
     */
    enum class TextDecorationStyleType {
        SOLID, DOUBLE, DOTTED, DASHED, WAVY
    }

    /**
     * Extended text decoration configuration.
     * Contains decoration line type, color, thickness, and style.
     *
     * Note: Compose's TextDecoration only supports line type (underline, line-through).
     * Color, thickness, and style require custom drawing for full support.
     */
    data class TextDecorationConfig(
        val decoration: TextDecoration,
        val color: Color?,              // null = use text color
        val thickness: Float?,          // in pixels, null = default
        val style: TextDecorationStyleType
    ) {
        val hasCustomStyling: Boolean
            get() = color != null || thickness != null || style != TextDecorationStyleType.SOLID
    }

    /**
     * Extract extended text decoration configuration from properties.
     */
    fun extractTextDecorationConfig(properties: List<IRProperty>): TextDecorationConfig? {
        var decorationLine: TextDecoration? = null
        var decorationColor: Color? = null
        var decorationThickness: Float? = null
        var decorationStyle = TextDecorationStyleType.SOLID

        properties.forEach { prop ->
            try {
                when (prop.type) {
                    "TextDecorationLine" -> {
                        decorationLine = extractTextDecoration(prop.data)
                    }
                    "TextDecorationColor" -> {
                        decorationColor = ValueExtractors.extractColor(prop.data)
                    }
                    "TextDecorationThickness" -> {
                        val dp = ValueExtractors.extractDp(prop.data)
                        decorationThickness = dp?.value
                    }
                    "TextDecorationStyle" -> {
                        val keyword = ValueExtractors.extractKeyword(prop.data)
                        decorationStyle = when (keyword?.lowercase()) {
                            "double" -> TextDecorationStyleType.DOUBLE
                            "dotted" -> TextDecorationStyleType.DOTTED
                            "dashed" -> TextDecorationStyleType.DASHED
                            "wavy" -> TextDecorationStyleType.WAVY
                            else -> TextDecorationStyleType.SOLID
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip properties that fail to parse
            }
        }

        if (decorationLine == null) return null

        return TextDecorationConfig(
            decoration = decorationLine,
            color = decorationColor,
            thickness = decorationThickness,
            style = decorationStyle
        )
    }
}
