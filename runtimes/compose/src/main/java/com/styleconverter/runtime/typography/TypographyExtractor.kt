package com.styleconverter.runtime.typography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts typography-related configuration from IR properties.
 *
 * ## Supported Properties
 * - FontFamily: Font family name or generic family
 * - FontSize: Font size (IRLength format)
 * - FontWeight: Weight (100-900 or keywords)
 * - FontStyle: Style (normal, italic, oblique)
 * - LetterSpacing: Letter spacing (IRLength format)
 * - LineHeight: Line height (number multiplier or IRLength)
 * - TextAlign: Text alignment (start, end, center, justify)
 * - TextDecorationLine: Text decoration (underline, line-through)
 * - TextOverflow: Overflow behavior (clip, ellipsis, visible)
 * - Color: Text color (IRColor format)
 * - TextTransform: Text transformation (uppercase, lowercase, capitalize)
 * - TextIndent: First line indentation (IRLength format)
 * - WordSpacing: Word spacing (IRLength format)
 * - WhiteSpace: White space handling
 * - LineClamp: Maximum visible lines
 *
 * ## IR Formats
 *
 * ### FontFamily
 * ```json
 * { "families": ["Roboto", "sans-serif"] }
 * // or just: "sans-serif"
 * ```
 *
 * ### FontSize
 * ```json
 * { "px": 16.0 }
 * // or with original: { "px": 16.0, "original": { "v": 1.0, "u": "rem" } }
 * ```
 *
 * ### FontWeight
 * ```json
 * 700
 * // or: { "weight": 700, "original": "bold" }
 * // or: "bold"
 * ```
 *
 * ### LineHeight
 * ```json
 * { "multiplier": 1.5 }
 * // or: { "px": 24.0 }
 * // or: 1.5
 * ```
 */
object TypographyExtractor {

    init {
        // Phase 6: claim the entire CSS Fonts + CSS Text + CSS Text-Decoration
        // surface that this monolithic extractor (plus its TextEmphasis,
        // FontVariant, FontSynthesis companions) already handles, plus the
        // long tail of spec properties we parse but do not yet render on
        // Compose. Registration here is what stops the legacy PropertyApplier
        // switch from re-handling these IDs — rendering happens inside
        // TextStyleApplier / TextFormattingApplier downstream.
        //
        // Sibling extractors own the five sub-families we delegate to:
        //   typography/advanced → AlignmentBaseline, BaselineShift, …
        //   typography/ruby     → Ruby*
        //   typography/text     → WritingMode, TextWrap*, HyphenateLimit*, …
        //   typography          → TextTransform, WhiteSpace, … (TextFormattingExtractor)
        //
        // CSS specs:
        //   Fonts 4          https://drafts.csswg.org/css-fonts-4/
        //   Text 4           https://drafts.csswg.org/css-text-4/
        //   Text-Decoration 4 https://drafts.csswg.org/css-text-decor-4/
        //   Inline 3         https://drafts.csswg.org/css-inline-3/
        PropertyRegistry.migrated(
            // ---- Core font descriptors (rendered via Compose TextStyle) ----
            "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
            // ---- Font variant / feature / synthesis ----
            // extractFontVariantConfig() and extractFontSynthesisConfig() below.
            "FontVariantCaps", "FontVariantLigatures", "FontVariantNumeric",
            "FontVariantEastAsian", "FontVariantEmoji", "FontVariantPosition",
            "FontVariantAlternates", "FontFeatureSettings", "FontKerning",
            "FontOpticalSizing", "FontVariationSettings",
            "FontSynthesisWeight", "FontSynthesisStyle", "FontSynthesisSmallCaps",
            // FontSynthesisPosition is proposed CSS Fonts 5 — parse-only.
            "FontSynthesisPosition",
            // ---- Font identity / metadata (no Compose analogue — parse-only) ----
            "FontDisplay", "FontLanguageOverride", "FontNamedInstance",
            "FontPalette", "FontSizeAdjust", "FontSmooth",
            "FontMinSize", "FontMaxSize",
            // ---- Caret (routed to AccentExtractor for actual rendering but
            //      the IR property lives under typography so we claim it
            //      here to close the category.) ----
            "CaretColor",
            // ---- Core text layout ----
            "LetterSpacing", "LineHeight", "WordSpacing",
            "TextAlign", "TextAlignLast", "TextAlignAll", "TextJustify",
            "TextOverflow",
            // ---- Text decoration family (rendered via TextDecorationConfig) ----
            "TextDecorationLine", "TextDecorationColor", "TextDecorationStyle",
            "TextDecorationThickness", "TextUnderlineOffset", "TextUnderlinePosition",
            // TextDecorationSkip* are parse-only — Compose has no API for them.
            "TextDecorationSkip", "TextDecorationSkipInk",
            // ---- Text emphasis (rendered via TextEmphasisApplier overlay) ----
            "TextEmphasis", "TextEmphasisStyle", "TextEmphasisColor", "TextEmphasisPosition",
            // ---- Shadows, vertical-align, line-clamp ----
            "TextShadow", "VerticalAlign", "VerticalAlignLast",
            "LineClamp", "MaxLines", "LineBreak",
            // ---- CSS-Text-4 long tail (parse-only on Compose today) ----
            "TextAnchor",              // SVG text anchor
            "TextAutospace",           // CJK autospace
            "TextBoxEdge", "TextBoxTrim",
            "TextCombineUpright",      // tate-chu-yoko
            "TextGroupAlign",
            "TextRendering",           // geometricPrecision/optimizeSpeed
            "TextSizeAdjust",          // WebKit text zoom
            "TextSpaceCollapse", "TextSpaceTrim", "TextSpacing", "TextSpacingTrim",
            "WordSpaceTransform",
            "BlockEllipsis",
            "HangingPunctuation",
            // ---- Initial-letter / drop-cap (parse-only) ----
            "InitialLetter", "InitialLetterAlign",
            // ---- Line grid / snap / height-step (CSS Line Grid 3, parse-only) ----
            "LineGrid", "LineSnap", "LineHeightStep",
            // ---- Widows/Orphans — paging-only, parse-only on mobile ----
            "Orphans", "Widows",
            // ---- SVG-only kerning + glyph orientation (parse-only) ----
            "Kerning", "GlyphOrientationHorizontal", "GlyphOrientationVertical",
            owner = "typography"
        )
    }

    /**
     * Extract a complete TypographyConfig from a list of property type/data pairs.
     *
     * @param properties List of (propertyType, data) pairs from IR
     * @return TypographyConfig with all extracted values
     */
    fun extractTypographyConfig(properties: List<Pair<String, JsonElement?>>): TypographyConfig {
        var config = TypographyConfig()

        // For extended text decoration, we need to accumulate values
        var decoLine: TextDecoration? = null
        var decoColor: Color? = null
        var decoThickness: Float? = null
        var decoStyle: TextDecorationStyle = TextDecorationStyle.SOLID
        var decoUnderlineOffset: Float? = null
        var decoUnderlinePosition: TextUnderlinePosition = TextUnderlinePosition.AUTO

        // Pre-resolve FontSize so a unitless `line-height: <n>` that
        // appears BEFORE FontSize in property order still multiplies
        // against the right base. CSS author order is not guaranteed.
        val preResolvedFontSp: Float? = properties
            .firstOrNull { it.first == "FontSize" }
            ?.let { extractFontSize(it.second)?.value }

        for ((type, data) in properties) {
            config = when (type) {
                "FontFamily" -> config.copy(fontFamily = extractFontFamily(data))
                "FontSize" -> config.copy(fontSize = extractFontSize(data))
                "FontWeight" -> config.copy(fontWeight = extractFontWeight(data))
                "FontStyle" -> config.copy(fontStyle = extractFontStyle(data))
                "FontStretch" -> config.copy(fontStretch = extractFontStretch(data))
                "LetterSpacing" -> config.copy(letterSpacing = extractLetterSpacing(data))
                "LineHeight" -> config.copy(lineHeight = extractLineHeight(data, preResolvedFontSp))
                "TextAlign" -> config.copy(textAlign = extractTextAlign(data))
                "TextAlignLast" -> config.copy(textAlignLast = extractTextAlignLast(data))
                "TextDecorationLine" -> {
                    decoLine = extractTextDecoration(data)
                    config.copy(textDecoration = decoLine)
                }
                "TextDecorationColor" -> {
                    decoColor = ValueExtractors.extractColor(data)
                    config
                }
                "TextDecorationThickness" -> {
                    decoThickness = ValueExtractors.extractDp(data)?.value
                    config
                }
                "TextDecorationStyle" -> {
                    decoStyle = extractTextDecorationStyle(data)
                    config
                }
                "TextUnderlineOffset" -> {
                    decoUnderlineOffset = ValueExtractors.extractDp(data)?.value
                    config
                }
                "TextUnderlinePosition" -> {
                    decoUnderlinePosition = extractTextUnderlinePosition(data)
                    config
                }
                "TextOverflow" -> config.copy(textOverflow = extractTextOverflow(data))
                "Color" -> config.copy(color = extractTextColor(data))
                "TextTransform" -> config.copy(textTransform = extractTextTransform(data))
                "TextIndent" -> config.copy(textIndent = extractTextIndent(data))
                "WordSpacing" -> config.copy(wordSpacing = extractWordSpacing(data))
                "WhiteSpace" -> config.copy(whiteSpace = extractWhiteSpace(data))
                "LineClamp" -> config.copy(lineClamp = extractLineClamp(data))
                "MaxLines" -> config.copy(maxLines = extractMaxLines(data))
                "TextShadow" -> config.copy(textShadow = extractTextShadow(data))
                "VerticalAlign" -> config.copy(baselineShift = extractBaselineShift(data))
                "TabSize" -> config.copy(tabSize = extractTabSize(data))
                "Direction" -> config.copy(direction = extractDirection(data))
                "LineBreak" -> config.copy(lineBreak = extractLineBreakMode(data))
                "WordBreak" -> config.copy(wordBreak = extractWordBreak(data))
                "OverflowWrap", "WordWrap" -> config.copy(overflowWrap = extractOverflowWrap(data))
                "Hyphens" -> config.copy(hyphens = extractHyphens(data))
                "HyphenateCharacter" -> config.copy(hyphenateCharacter = ValueExtractors.extractKeyword(data))
                "TextJustify" -> config.copy(textJustify = extractTextJustify(data))
                else -> config
            }
        }

        // Build extended text decoration if any decoration properties were set
        if (decoLine != null || decoColor != null || decoThickness != null ||
            decoUnderlineOffset != null || decoUnderlinePosition != TextUnderlinePosition.AUTO) {
            config = config.copy(
                textDecorationExtended = TextDecorationConfig(
                    line = decoLine,
                    color = decoColor,
                    thickness = decoThickness?.dp,
                    style = decoStyle,
                    underlineOffset = decoUnderlineOffset?.dp,
                    underlinePosition = decoUnderlinePosition
                )
            )
        }

        return config
    }

    /**
     * Extract font family from IR data.
     *
     * Handles formats:
     * - String keyword: "sans-serif", "serif", "monospace", "cursive"
     * - Object with families array: { "families": ["Roboto", "sans-serif"] }
     *
     * @param json JSON element containing font family data
     * @return FontFamily, or null if not extractable
     */
    private fun extractFontFamily(json: JsonElement?): FontFamily? {
        val familyName = ValueExtractors.extractKeyword(json) ?: return null
        return when (familyName.lowercase()) {
            "serif" -> FontFamily.Serif
            "sans-serif" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
    }

    /**
     * Extract font size from IR data.
     *
     * Converts pixel values to sp (scalable pixels) for accessibility.
     *
     * @param json JSON element containing font size data (IRLength format)
     * @return TextUnit in sp, or null if not extractable
     */
    private fun extractFontSize(json: JsonElement?): TextUnit? {
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value.sp
    }

    /**
     * Extract font weight from IR data.
     *
     * Handles formats:
     * - Direct numeric: 400, 700
     * - Object with weight: { "weight": 700 }
     * - Keyword: "normal", "bold"
     *
     * @param json JSON element containing font weight data
     * @return FontWeight, or null if not extractable
     */
    private fun extractFontWeight(json: JsonElement?): FontWeight? {
        val weight = ValueExtractors.extractFontWeight(json) ?: return null
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
            else -> FontWeight(weight.coerceIn(100, 900))
        }
    }

    /**
     * Extract font style from IR data.
     *
     * CSS values: normal, italic, oblique
     * Note: Compose doesn't distinguish oblique from italic.
     *
     * @param json JSON element containing font style data
     * @return FontStyle, or null if not extractable
     */
    private fun extractFontStyle(json: JsonElement?): FontStyle? {
        val style = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (style) {
            "italic", "oblique" -> FontStyle.Italic
            "normal" -> FontStyle.Normal
            else -> null
        }
    }

    /**
     * Extract letter spacing from IR data.
     *
     * @param json JSON element containing letter spacing data (IRLength format)
     * @return TextUnit in sp, or null if not extractable
     */
    private fun extractLetterSpacing(json: JsonElement?): TextUnit? {
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value.sp
    }

    /**
     * Extract line height from IR data.
     *
     * Handles formats:
     * - Number (multiplier): 1.5 -> multiplied by base font size (default 16)
     * - Length (IRLength): { "px": 24.0 } -> direct sp value
     * - Keyword: "normal" -> 1.2 multiplier
     *
     * @param json JSON element containing line height data
     * @return TextUnit in sp, or null if not extractable
     */
    private fun extractLineHeight(json: JsonElement?, fontSizeSp: Float? = null): TextUnit? {
        // The IR emits LineHeight as either `{ "px": N }` (length) or
        // `{ "multiplier": N, "original": ... }` (CSS unitless multiplier).
        // Try the multiplier path first via the JSON-object lookup so
        // the multiplier × font-size resolution lands on the correct
        // base when font-size was set in the same property pass.
        if (json is JsonObject) {
            val mult = json["multiplier"]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.floatOrNull else null
            }
            if (mult != null) {
                // Use the rendered font-size (set earlier in the same
                // extractor pass) rather than the 16sp body default —
                // matches CSS spec: `line-height: 2` on a 14px element
                // computes to 28px line-box, not 32px.
                val base = fontSizeSp ?: 16f
                return (mult * base).sp
            }
        }
        // Fallback: extractFloat handles raw numbers (some legacy IR
        // shapes emit just `2.0`); still uses fontSizeSp when known.
        val floatValue = ValueExtractors.extractFloat(json)
        if (floatValue != null) {
            return (floatValue * (fontSizeSp ?: 16f)).sp
        }
        // Try extracting as length
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value.sp
    }

    /**
     * Extract text alignment from IR data.
     *
     * CSS values: left, right, center, justify, start, end
     *
     * @param json JSON element containing text align data
     * @return TextAlign, or null if not extractable
     */
    private fun extractTextAlign(json: JsonElement?): TextAlign? {
        val align = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (align) {
            "left", "start" -> TextAlign.Start
            "right", "end" -> TextAlign.End
            "center" -> TextAlign.Center
            "justify" -> TextAlign.Justify
            else -> null
        }
    }

    /**
     * Extract text decoration from IR data.
     *
     * CSS values: underline, line-through, overline, none
     * Note: Compose doesn't support overline.
     *
     * @param json JSON element containing text decoration data
     * @return TextDecoration, or null if not extractable
     */
    private fun extractTextDecoration(json: JsonElement?): TextDecoration? {
        val decoration = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (decoration) {
            "underline" -> TextDecoration.Underline
            "line-through" -> TextDecoration.LineThrough
            "none" -> TextDecoration.None
            else -> null
        }
    }

    /**
     * Extract text overflow behavior from IR data.
     *
     * CSS values: clip, ellipsis, visible
     *
     * @param json JSON element containing text overflow data
     * @return TextOverflow, or null if not extractable
     */
    private fun extractTextOverflow(json: JsonElement?): TextOverflow? {
        val overflow = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (overflow) {
            "clip" -> TextOverflow.Clip
            "ellipsis" -> TextOverflow.Ellipsis
            "visible" -> TextOverflow.Visible
            else -> null
        }
    }

    /**
     * Extract text color from IR data.
     *
     * @param json JSON element containing color data (IRColor format)
     * @return Color, or null if not extractable (e.g., var(), currentColor)
     */
    private fun extractTextColor(json: JsonElement?): Color? {
        return ValueExtractors.extractColor(json)
    }

    /**
     * Extract text transform from IR data.
     *
     * CSS values: none, uppercase, lowercase, capitalize
     * Note: Compose doesn't have built-in text-transform; must be applied to string.
     *
     * @param json JSON element containing text transform data
     * @return TextTransform, or null if not extractable
     */
    private fun extractTextTransform(json: JsonElement?): TextTransform? {
        val transform = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (transform) {
            "uppercase" -> TextTransform.UPPERCASE
            "lowercase" -> TextTransform.LOWERCASE
            "capitalize" -> TextTransform.CAPITALIZE
            "none" -> TextTransform.NONE
            else -> null
        }
    }

    /**
     * Extract text indent from IR data.
     *
     * @param json JSON element containing text indent data (IRLength format)
     * @return TextUnit in sp, or null if not extractable
     */
    private fun extractTextIndent(json: JsonElement?): TextUnit? {
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value.sp
    }

    /**
     * Extract word spacing from IR data.
     *
     * @param json JSON element containing word spacing data (IRLength format)
     * @return TextUnit in sp, or null if not extractable
     */
    private fun extractWordSpacing(json: JsonElement?): TextUnit? {
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value.sp
    }

    /**
     * Extract white space handling from IR data.
     *
     * CSS values: normal, nowrap, pre, pre-wrap, pre-line, break-spaces
     *
     * @param json JSON element containing white space data
     * @return WhiteSpace, or null if not extractable
     */
    private fun extractWhiteSpace(json: JsonElement?): WhiteSpace? {
        val ws = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (ws) {
            "normal" -> WhiteSpace.NORMAL
            "nowrap" -> WhiteSpace.NOWRAP
            "pre" -> WhiteSpace.PRE
            "pre-wrap" -> WhiteSpace.PRE_WRAP
            "pre-line" -> WhiteSpace.PRE_LINE
            "break-spaces" -> WhiteSpace.BREAK_SPACES
            else -> null
        }
    }

    /**
     * Extract line clamp value from IR data.
     *
     * CSS: -webkit-line-clamp
     *
     * @param json JSON element containing line clamp data
     * @return Int number of lines, or null if not extractable
     */
    private fun extractLineClamp(json: JsonElement?): Int? {
        if (json == null) return null
        // Handle object format: { "type": "lines", "count": 2.0 }
        if (json is JsonObject) {
            val type = json["type"]?.jsonPrimitive?.toString()?.trim('"')
            if (type == "none") return null
            if (type == "lines") {
                return json["count"]?.jsonPrimitive?.floatOrNull?.toInt()
            }
        }
        return ValueExtractors.extractInt(json)
    }

    /**
     * Extract max-lines value from IR data.
     */
    private fun extractMaxLines(json: JsonElement?): Int? {
        if (json == null) return null
        if (json is JsonObject) {
            val type = json["type"]?.jsonPrimitive?.toString()?.trim('"')
            if (type == "none") return null
            if (type == "count") {
                val value = json["value"]
                return if (value is JsonObject) {
                    value["value"]?.jsonPrimitive?.intOrNull
                } else {
                    value?.jsonPrimitive?.intOrNull
                }
            }
        }
        return ValueExtractors.extractInt(json)
    }

    /**
     * Extract font stretch as percentage (50-200, where 100 is normal).
     */
    private fun extractFontStretch(json: JsonElement?): Float? {
        if (json == null) return null
        return when (json) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                json.floatOrNull ?: when (json.toString().trim('"').lowercase()) {
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
                json["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: json["value"]?.jsonPrimitive?.floatOrNull
            }
            else -> null
        }
    }

    /**
     * Extract text shadow from IR data.
     */
    private fun extractTextShadow(json: JsonElement?): TextShadowConfig? {
        if (json == null) return null
        // TextShadow is typically an array
        val array = json as? JsonArray
        if (array == null || array.isEmpty()) return null

        val firstShadow = array[0] as? JsonObject ?: return null
        val offsetX = firstShadow["x"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
        val offsetY = firstShadow["y"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
        val blur = firstShadow["blur"]?.let { ValueExtractors.extractDp(it)?.value } ?: 0f
        val color = firstShadow["c"]?.let { ValueExtractors.extractColor(it) }
            ?: firstShadow["color"]?.let { ValueExtractors.extractColor(it) }
            ?: Color.Black

        return TextShadowConfig(
            offsetX = offsetX.dp,
            offsetY = offsetY.dp,
            blurRadius = blur.dp,
            color = color
        )
    }

    /**
     * Extract baseline shift from vertical-align property.
     */
    private fun extractBaselineShift(json: JsonElement?): BaselineShift? {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (keyword) {
            "sub" -> BaselineShift.Subscript
            "super" -> BaselineShift.Superscript
            "baseline" -> BaselineShift.None
            else -> null
        }
    }

    /**
     * Extract tab size configuration.
     */
    private fun extractTabSize(json: JsonElement?): TabSizeConfig? {
        if (json == null) return null
        return when (json) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                val spaces = json.intOrNull
                if (spaces != null) TabSizeConfig(spaces = spaces, pixels = null)
                else null
            }
            is JsonObject -> {
                val type = json["type"]?.jsonPrimitive?.toString()?.trim('"')
                when (type) {
                    "number" -> {
                        val value = json["value"]
                        val spaces = if (value is JsonObject) {
                            value["value"]?.jsonPrimitive?.intOrNull
                        } else {
                            value?.jsonPrimitive?.intOrNull
                        }
                        TabSizeConfig(spaces = spaces ?: 8, pixels = null)
                    }
                    "length" -> {
                        val pixels = json["length"]?.let { ValueExtractors.extractDp(it)?.value }
                        TabSizeConfig(spaces = null, pixels = pixels)
                    }
                    else -> {
                        val spaces = json["value"]?.jsonPrimitive?.intOrNull
                            ?: json["spaces"]?.jsonPrimitive?.intOrNull
                        TabSizeConfig(spaces = spaces ?: 8, pixels = null)
                    }
                }
            }
            else -> null
        }
    }

    /**
     * Extract text direction from IR data.
     */
    private fun extractDirection(json: JsonElement?): DirectionMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return null
        return when (keyword) {
            "RTL" -> DirectionMode.RTL
            "LTR" -> DirectionMode.LTR
            else -> null
        }
    }

    /**
     * Extract line break mode from IR data.
     */
    private fun extractLineBreakMode(json: JsonElement?): LineBreakMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return null
        return try {
            LineBreakMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract word break mode from IR data.
     */
    private fun extractWordBreak(json: JsonElement?): WordBreakMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return null
        return try {
            WordBreakMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract overflow wrap mode from IR data.
     */
    private fun extractOverflowWrap(json: JsonElement?): OverflowWrapMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return null
        return try {
            OverflowWrapMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract hyphens mode from IR data.
     */
    private fun extractHyphens(json: JsonElement?): HyphensMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return null
        return try {
            HyphensMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract text justify mode from IR data.
     */
    private fun extractTextJustify(json: JsonElement?): TextJustifyMode? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return null
        return try {
            TextJustifyMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract text align last from IR data.
     */
    private fun extractTextAlignLast(json: JsonElement?): TextAlignLast? {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return null
        return try {
            TextAlignLast.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract text decoration style from IR data.
     */
    private fun extractTextDecorationStyle(json: JsonElement?): TextDecorationStyle {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return TextDecorationStyle.SOLID
        return try {
            TextDecorationStyle.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            TextDecorationStyle.SOLID
        }
    }

    /**
     * Extract text underline position from IR data.
     *
     * CSS values: auto, under, left, right, from-font
     */
    private fun extractTextUnderlinePosition(json: JsonElement?): TextUnderlinePosition {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return TextUnderlinePosition.AUTO
        return try {
            TextUnderlinePosition.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            TextUnderlinePosition.AUTO
        }
    }

    // ==================== TEXT EMPHASIS ====================

    /**
     * Extract text emphasis configuration from properties.
     *
     * CSS properties: text-emphasis-style, text-emphasis-color, text-emphasis-position
     */
    fun extractTextEmphasisConfig(properties: List<Pair<String, JsonElement?>>): TextEmphasisConfig {
        var style = TextEmphasisStyle.NONE
        var color: Color? = null
        var position = TextEmphasisPosition.OVER_RIGHT

        for ((type, data) in properties) {
            when (type) {
                "TextEmphasisStyle" -> style = extractTextEmphasisStyle(data)
                "TextEmphasisColor" -> color = ValueExtractors.extractColor(data)
                "TextEmphasisPosition" -> position = extractTextEmphasisPosition(data)
                "TextEmphasis" -> {
                    // Shorthand combines style and optionally color
                    if (data is JsonObject) {
                        data["style"]?.let { style = extractTextEmphasisStyle(it) }
                        data["color"]?.let { color = ValueExtractors.extractColor(it) }
                    }
                }
            }
        }

        return TextEmphasisConfig(style = style, color = color, position = position)
    }

    private fun extractTextEmphasisStyle(json: JsonElement?): TextEmphasisStyle {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return TextEmphasisStyle.NONE
        return try {
            TextEmphasisStyle.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            // Handle compound values like "filled dot"
            when {
                keyword.contains("DOT") && keyword.contains("FILLED") -> TextEmphasisStyle.FILLED_DOT
                keyword.contains("DOT") && keyword.contains("OPEN") -> TextEmphasisStyle.OPEN_DOT
                keyword.contains("CIRCLE") && keyword.contains("FILLED") -> TextEmphasisStyle.FILLED_CIRCLE
                keyword.contains("CIRCLE") && keyword.contains("OPEN") -> TextEmphasisStyle.OPEN_CIRCLE
                keyword.contains("DOUBLE") && keyword.contains("FILLED") -> TextEmphasisStyle.FILLED_DOUBLE_CIRCLE
                keyword.contains("DOUBLE") && keyword.contains("OPEN") -> TextEmphasisStyle.OPEN_DOUBLE_CIRCLE
                keyword.contains("TRIANGLE") && keyword.contains("FILLED") -> TextEmphasisStyle.FILLED_TRIANGLE
                keyword.contains("TRIANGLE") && keyword.contains("OPEN") -> TextEmphasisStyle.OPEN_TRIANGLE
                keyword.contains("SESAME") && keyword.contains("FILLED") -> TextEmphasisStyle.FILLED_SESAME
                keyword.contains("SESAME") && keyword.contains("OPEN") -> TextEmphasisStyle.OPEN_SESAME
                keyword.contains("DOT") -> TextEmphasisStyle.FILLED_DOT
                keyword.contains("CIRCLE") -> TextEmphasisStyle.FILLED_CIRCLE
                keyword.contains("TRIANGLE") -> TextEmphasisStyle.FILLED_TRIANGLE
                keyword.contains("SESAME") -> TextEmphasisStyle.FILLED_SESAME
                else -> TextEmphasisStyle.NONE
            }
        }
    }

    private fun extractTextEmphasisPosition(json: JsonElement?): TextEmphasisPosition {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return TextEmphasisPosition.OVER_RIGHT
        return try {
            TextEmphasisPosition.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            when {
                keyword.contains("OVER") && keyword.contains("LEFT") -> TextEmphasisPosition.OVER_LEFT
                keyword.contains("UNDER") && keyword.contains("RIGHT") -> TextEmphasisPosition.UNDER_RIGHT
                keyword.contains("UNDER") && keyword.contains("LEFT") -> TextEmphasisPosition.UNDER_LEFT
                keyword.contains("OVER") -> TextEmphasisPosition.OVER_RIGHT
                keyword.contains("UNDER") -> TextEmphasisPosition.UNDER_RIGHT
                else -> TextEmphasisPosition.OVER_RIGHT
            }
        }
    }

    // ==================== FONT VARIANT ====================

    /**
     * Extract font variant configuration from properties.
     *
     * CSS properties: font-variant-caps, font-variant-numeric, font-variant-ligatures,
     *                 font-feature-settings, font-kerning, font-optical-sizing
     */
    fun extractFontVariantConfig(properties: List<Pair<String, JsonElement?>>): FontVariantConfig {
        var caps = FontVariantCaps.NORMAL
        var ligatures = FontVariantLigatures.NORMAL
        var numeric = FontVariantNumeric()
        var eastAsian = FontVariantEastAsian.NORMAL
        var emoji = FontVariantEmoji.NORMAL
        var position = FontVariantPosition.NORMAL
        var alternates = FontVariantAlternates.NORMAL
        var fontFeatureSettings = emptyList<FontFeatureSetting>()
        var fontKerning = FontKerningValue.AUTO
        var fontOpticalSizing = FontOpticalSizingValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "FontVariantCaps" -> caps = extractFontVariantCaps(data)
                "FontVariantLigatures" -> ligatures = extractFontVariantLigatures(data)
                "FontVariantNumeric" -> numeric = extractFontVariantNumeric(data)
                "FontVariantEastAsian" -> eastAsian = extractFontVariantEastAsian(data)
                "FontVariantEmoji" -> emoji = extractFontVariantEmoji(data)
                "FontVariantPosition" -> position = extractFontVariantPosition(data)
                "FontVariantAlternates" -> alternates = extractFontVariantAlternates(data)
                "FontFeatureSettings" -> fontFeatureSettings = extractFontFeatureSettings(data)
                "FontKerning" -> fontKerning = extractFontKerning(data)
                "FontOpticalSizing" -> fontOpticalSizing = extractFontOpticalSizing(data)
            }
        }

        return FontVariantConfig(
            caps = caps,
            ligatures = ligatures,
            numeric = numeric,
            eastAsian = eastAsian,
            emoji = emoji,
            position = position,
            alternates = alternates,
            fontFeatureSettings = fontFeatureSettings,
            fontKerning = fontKerning,
            fontOpticalSizing = fontOpticalSizing
        )
    }

    private fun extractFontVariantCaps(json: JsonElement?): FontVariantCaps {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return FontVariantCaps.NORMAL
        return try {
            FontVariantCaps.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantCaps.NORMAL
        }
    }

    private fun extractFontVariantLigatures(json: JsonElement?): FontVariantLigatures {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return FontVariantLigatures.NORMAL
        return try {
            FontVariantLigatures.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantLigatures.NORMAL
        }
    }

    private fun extractFontVariantNumeric(json: JsonElement?): FontVariantNumeric {
        if (json == null) return FontVariantNumeric()

        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
        if (keyword == "NORMAL" || keyword == null) return FontVariantNumeric()

        // Parse multiple values
        var figure = NumericFigure.NORMAL
        var spacing = NumericSpacing.NORMAL
        var fraction = NumericFraction.NORMAL
        var ordinal = false
        var slashedZero = false

        val values = keyword.split(" ", "_")
        for (v in values) {
            when (v) {
                "LINING", "LINING_NUMS" -> figure = NumericFigure.LINING_NUMS
                "OLDSTYLE", "OLDSTYLE_NUMS" -> figure = NumericFigure.OLDSTYLE_NUMS
                "PROPORTIONAL", "PROPORTIONAL_NUMS" -> spacing = NumericSpacing.PROPORTIONAL_NUMS
                "TABULAR", "TABULAR_NUMS" -> spacing = NumericSpacing.TABULAR_NUMS
                "DIAGONAL", "DIAGONAL_FRACTIONS" -> fraction = NumericFraction.DIAGONAL_FRACTIONS
                "STACKED", "STACKED_FRACTIONS" -> fraction = NumericFraction.STACKED_FRACTIONS
                "ORDINAL" -> ordinal = true
                "SLASHED", "SLASHED_ZERO" -> slashedZero = true
            }
        }

        return FontVariantNumeric(
            figure = figure,
            spacing = spacing,
            fraction = fraction,
            ordinal = ordinal,
            slashedZero = slashedZero
        )
    }

    private fun extractFontVariantEastAsian(json: JsonElement?): FontVariantEastAsian {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return FontVariantEastAsian.NORMAL
        return try {
            FontVariantEastAsian.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantEastAsian.NORMAL
        }
    }

    private fun extractFontVariantEmoji(json: JsonElement?): FontVariantEmoji {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()
            ?: return FontVariantEmoji.NORMAL
        return try {
            FontVariantEmoji.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantEmoji.NORMAL
        }
    }

    private fun extractFontVariantPosition(json: JsonElement?): FontVariantPosition {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()
            ?: return FontVariantPosition.NORMAL
        return try {
            FontVariantPosition.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantPosition.NORMAL
        }
    }

    private fun extractFontVariantAlternates(json: JsonElement?): FontVariantAlternates {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_")
            ?: return FontVariantAlternates.NORMAL
        return try {
            FontVariantAlternates.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontVariantAlternates.NORMAL
        }
    }

    /**
     * Extract font feature settings from IR data.
     *
     * CSS: font-feature-settings: "liga" on, "calt" 1, "smcp"
     *
     * Handles formats:
     * - Array of settings: [{"tag": "liga", "value": 1}, ...]
     * - String: "liga", "smcp"
     * - Keyword: "normal"
     */
    private fun extractFontFeatureSettings(json: JsonElement?): List<FontFeatureSetting> {
        if (json == null) return emptyList()

        // Check for "normal" keyword
        val keyword = ValueExtractors.extractKeyword(json)
        if (keyword?.lowercase() == "normal") return emptyList()

        // Handle array of settings
        val array = json as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tag = obj["tag"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.intOrNull ?: 1
            FontFeatureSetting(tag = tag, value = value)
        }
    }

    /**
     * Extract font kerning value from IR data.
     *
     * CSS: font-kerning: auto | normal | none
     */
    private fun extractFontKerning(json: JsonElement?): FontKerningValue {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()
            ?: return FontKerningValue.AUTO
        return try {
            FontKerningValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontKerningValue.AUTO
        }
    }

    /**
     * Extract font optical sizing value from IR data.
     *
     * CSS: font-optical-sizing: auto | none
     */
    private fun extractFontOpticalSizing(json: JsonElement?): FontOpticalSizingValue {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()
            ?: return FontOpticalSizingValue.AUTO
        return try {
            FontOpticalSizingValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FontOpticalSizingValue.AUTO
        }
    }

    // ==================== FONT SYNTHESIS ====================

    /**
     * Extract font synthesis configuration from properties.
     *
     * CSS properties: font-synthesis-weight, font-synthesis-style, font-synthesis-small-caps
     */
    fun extractFontSynthesisConfig(properties: List<Pair<String, JsonElement?>>): FontSynthesisConfig {
        var weight = true
        var style = true
        var smallCaps = true

        for ((type, data) in properties) {
            when (type) {
                "FontSynthesisWeight" -> weight = extractFontSynthesisValue(data)
                "FontSynthesisStyle" -> style = extractFontSynthesisValue(data)
                "FontSynthesisSmallCaps" -> smallCaps = extractFontSynthesisValue(data)
                "FontSynthesis" -> {
                    // Shorthand: "none", "weight", "style", "weight style", etc.
                    val values = extractFontSynthesisShorthand(data)
                    weight = values.first
                    style = values.second
                    smallCaps = values.third
                }
            }
        }

        return FontSynthesisConfig(
            weight = weight,
            style = style,
            smallCaps = smallCaps
        )
    }

    private fun extractFontSynthesisValue(json: JsonElement?): Boolean {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return true
        return keyword != "none" && keyword != "off" && keyword != "false"
    }

    private fun extractFontSynthesisShorthand(json: JsonElement?): Triple<Boolean, Boolean, Boolean> {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return Triple(true, true, true)

        if (keyword == "none") return Triple(false, false, false)

        val hasWeight = keyword.contains("weight")
        val hasStyle = keyword.contains("style")
        val hasSmallCaps = keyword.contains("small-caps") || keyword.contains("small_caps")

        // If specific values are mentioned, only those are enabled
        return if (hasWeight || hasStyle || hasSmallCaps) {
            Triple(hasWeight, hasStyle, hasSmallCaps)
        } else {
            Triple(true, true, true) // Default: all enabled
        }
    }

    /**
     * Check if a property type is a typography-related property.
     *
     * Used to filter properties before extraction.
     *
     * @param type The IR property type string
     * @return true if this is a typography property
     */
    fun isTypographyProperty(type: String): Boolean {
        return type in TYPOGRAPHY_PROPERTIES
    }

    /**
     * Set of all typography-related property types.
     */
    private val TYPOGRAPHY_PROPERTIES = setOf(
        // Font properties
        "FontFamily",
        "FontSize",
        "FontWeight",
        "FontStyle",
        "FontStretch",
        "FontVariantCaps",
        "FontVariantNumeric",
        "FontVariantLigatures",
        "FontVariantAlternates",
        "FontVariantEastAsian",
        "FontVariantEmoji",
        "FontVariantPosition",
        "FontFeatureSettings",
        "FontKerning",
        "FontOpticalSizing",
        "FontVariationSettings",

        // Font synthesis
        "FontSynthesis",
        "FontSynthesisWeight",
        "FontSynthesisStyle",
        "FontSynthesisSmallCaps",

        // Text properties
        "Color",
        "TextAlign",
        "TextAlignLast",
        "TextJustify",
        "TextTransform",
        "TextIndent",
        "TextOverflow",
        "TextDecorationLine",
        "TextDecorationColor",
        "TextDecorationStyle",
        "TextDecorationThickness",
        "TextUnderlineOffset",
        "TextUnderlinePosition",
        "TextShadow",
        "TextRendering",

        // Text emphasis
        "TextEmphasis",
        "TextEmphasisStyle",
        "TextEmphasisColor",
        "TextEmphasisPosition",

        // Spacing
        "LetterSpacing",
        "LineHeight",
        "WordSpacing",
        "TabSize",

        // Wrapping
        "WhiteSpace",
        "WordBreak",
        "WordWrap",
        "OverflowWrap",
        "Hyphens",
        "LineBreak",

        // Clipping
        "LineClamp",
        "MaxLines",

        // Direction and writing mode
        "Direction",
        "WritingMode",
        "TextOrientation",
        "UnicodeBidi",

        // Vertical alignment
        "VerticalAlign"
    )
}
