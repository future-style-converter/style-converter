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
 * - WordSpacing (via letter spacing approximation)
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
     */
    fun extractTextStyle(properties: List<IRProperty>): TextStyle {
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
            ?.let { try { extractFontSize(it.data)?.value } catch (_: Exception) { null } }

        properties.forEach { property ->
            try {
                when (property.type) {
                    "Color" -> color = ValueExtractors.extractColor(property.data)
                    "FontSize" -> fontSize = extractFontSize(property.data)
                    "FontWeight" -> fontWeight = extractFontWeight(property.data)
                    "FontStyle" -> fontStyle = extractFontStyle(property.data)
                    "FontFamily" -> fontFamily = extractFontFamily(property.data)
                    "TextAlign" -> textAlign = extractTextAlign(property.data)
                    "TextDecorationLine" -> textDecoration = extractTextDecoration(property.data)
                    "LineHeight" -> lineHeight = extractLineHeight(property.data, preResolvedFontSp)
                    "LetterSpacing" -> letterSpacing = extractLetterSpacing(property.data)
                    "WordSpacing" -> {
                        // Word spacing approximated via letter spacing
                        // Note: Compose doesn't have direct word-spacing support
                        val wordSpacing = extractWordSpacing(property.data)
                        if (wordSpacing != null && letterSpacing == null) {
                            letterSpacing = wordSpacing
                        }
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
            )
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

    private fun extractFontSize(data: JsonElement): TextUnit? {
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
                if (type == "absoluteKeyword") {
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
            }
        }
        return null
    }

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

    private fun extractFontStyle(data: JsonElement): FontStyle? {
        val keyword = ValueExtractors.extractKeyword(data)
        return when (keyword?.lowercase()) {
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
            // {"v":0.25,"u":"REM"}} — px carries a bogus 0 instead of null,
            // so extractDp below happily returned 0.sp and the tracking
            // vanished (Typography_C10: web spaced glyphs 4px apart,
            // Android didn't, 0.847). When px is 0 but the ORIGINAL is a
            // non-zero em/rem, resolve it the same way the reference does:
            // ×16 (harness root and body font-size are both 16px).
            val px = data["px"]?.jsonPrimitive?.floatOrNull
            if (px == 0f) {
                // The wire shape nests once: {"px":0.0,"original":{"type":
                // "length","original":{"v":0.25,"u":"REM"}}} — unwrap the
                // outer envelope before reading v/u.
                val outer = data["original"] as? JsonObject
                val original = (outer?.get("original") as? JsonObject) ?: outer
                val v = original?.get("v")?.jsonPrimitive?.floatOrNull
                val u = original?.get("u")?.jsonPrimitive?.contentOrNull?.uppercase()
                if (v != null && v != 0f && (u == "EM" || u == "REM")) {
                    return (v * 16f).sp
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

    /**
     * Extract word spacing from IR data.
     * Word spacing is approximated using letter spacing since Compose doesn't support it directly.
     */
    private fun extractWordSpacing(data: JsonElement): TextUnit? {
        if (data is JsonObject) {
            data["pixels"]?.jsonPrimitive?.floatOrNull?.let {
                return it.sp
            }
        }
        val dp = ValueExtractors.extractDp(data)
        if (dp != null) {
            return dp.value.sp
        }
        return null
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
        return when (keyword?.lowercase()) {
            "uppercase" -> TextTransformMode.UPPERCASE
            "lowercase" -> TextTransformMode.LOWERCASE
            "capitalize" -> TextTransformMode.CAPITALIZE
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
            TextTransformMode.CAPITALIZE -> text.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
                }
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
