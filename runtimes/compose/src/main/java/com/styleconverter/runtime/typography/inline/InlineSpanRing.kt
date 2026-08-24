// typography/inline — wave 47 (lane Z6): the STYLED-SPAN RING, the third
// breach in the inline-run wall. Waves 44/45 fold text-and-plain-span
// hosts into one paragraph but bail the moment a member carries live
// styling — the folded string is one uniform style. Measured victims
// (wave46-final, browser-ref SSIM): css-overflow/block-ellipsis-004/005/
// 006 (a bold-italic-1.5em bordered <span> member renders as a
// full-width block on iOS — ios-ref 0.8997/0.9093 — and vanishes under
// the clamp on Android — android-ref 0.9767/0.9761) and css-text-decor/
// text-decoration-inset-014 (an underlined <u> member stacks on its own
// line — android-ref 0.9302 / ios-ref 0.9317). This module answers, for
// ONE glyph-bearing member, "which styling can ride the fold as a
// per-range span attribution?" — and maps the fold's ranges through the
// leaf pipeline's string surgery. InlineRunFold plants the ranges;
// InlineSpanContent turns them into Compose SpanStyle overlays.
package com.styleconverter.runtime.typography.inline

// The typed IR property bag the admission gate classifies (pure data).
import com.styleconverter.runtime.core.ir.IRProperty
// The shared wire-value readers — one parse per shape, never re-derived.
import com.styleconverter.runtime.core.types.ValueExtractors
// Wire JSON access for the FontSize / decoration-list shapes below.
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * InlineSpanRing — the styling a folded member may carry per-range.
 *
 * ## The CSS model
 * An inline box's own `color` / `font-*` / `text-decoration-*` apply to
 * exactly its glyphs inside the shared inline formatting context
 * (CSS 2.1 §9.4.2; css-text-decor-3 §2.1 propagates a decoration over
 * the box's own inline content). One paragraph with per-range character
 * styles IS that model — Compose's AnnotatedString SpanStyle ranges and
 * SwiftUI's concatenated per-segment Texts both express it natively, so
 * the fold no longer needs to refuse a styled member: it records the
 * member's range in the merged string and this ring's typed [Style].
 *
 * ## What is admitted (and what still refuses, loudly)
 *   • Color — SpanStyle color over the member range.
 *   • FontSize — px directly; em/% resolve against the PARAGRAPH's
 *     resolved size at the seam (css-values-4 §5.1.1: em on font-size
 *     resolves against the inherited size — the fold host IS the parent);
 *     rem against the 16px root the harness pins. Any other unit refuses
 *     (no static base — never forge one).
 *   • FontWeight / FontStyle — SpanStyle weight / italic.
 *   • TextDecorationLine underline / line-through — per-range
 *     TextDecoration; `overline` refuses (Compose SpanStyle and SwiftUI
 *     Text both have no per-range overline).
 *   • TextDecorationColor — admitted ONLY when it equals the member's
 *     effective ink (own Color, else the host's): Compose paints span
 *     decorations in the span's TEXT color (no per-range decoration
 *     color API), so an equal wire color renders exactly and a diverging
 *     one would paint a lie — text-decoration-inset-011's blue/green
 *     underlines on black text stay refused (with `TextUnderlineOffset`,
 *     which this ring cannot place either — both walls named).
 *   • TextDecorationStyle — solid only (span decorations are solid).
 *   • Border longhands — a STATED LOSS: a styled member's border box
 *     (block-ellipsis-004's `border: 2px solid blue` around "Line 3
 *     Line 4") cannot be painted by a character-style ring. It is
 *     admitted — the fold's paragraph geometry beats the stacked
 *     fallback's full-width block box by the measured margins above —
 *     and reported via [Admitted.statedLossTypes] so the seam leaves the
 *     no-silent-fallthrough breadcrumb.
 *   • BoxDecorationBreak — fragments a box paint this ring does not
 *     paint (only meaningful for the border loss above); admitted as
 *     inert alongside it.
 * Everything else refuses with the property named, exactly like the
 * wave-44 member gate (`member-prop:<type>` in the fold's bail).
 *
 * Pure Kotlin (no Compose imports) so the JVM suite pins admission and
 * remap on the verbatim wave46-final wire payloads without a device.
 */
object InlineSpanRing {

    /** Normalized sRGB 0..1 floats — the IR's own color space (the same
     *  shape InlineAtomRing.Paint.Concrete carries, kept Compose-free so
     *  the fold stays pure). */
    data class Ink(val r: Float, val g: Float, val b: Float, val a: Float)

    /** One member's admitted span attribution. Every field null/false =
     *  [PLAIN]: the member folds exactly like a wave-44 policy-only span
     *  and no span overlay is emitted for it. */
    data class Style(
        /** The member's own `color`, or null = inherit the paragraph's. */
        val ink: Ink? = null,
        /** Absolute font-size in px (px/pt/keyword wires), if declared. */
        val fontSizePx: Float? = null,
        /** Relative font-size FACTOR (em / % / smaller / larger wires) —
         *  multiplied against the paragraph's resolved size at the seam
         *  (the fold host is the CSS parent, css-values-4 §5.1.1). */
        val fontSizeEm: Float? = null,
        /** Numeric weight 100..900 (the wire is pre-normalized). */
        val fontWeight: Int? = null,
        /** css-fonts-4 §2.2 italic/oblique — SpanStyle Italic. */
        val italic: Boolean = false,
        /** Underline: `text-decoration-line: underline` or the UA `<u>`
         *  rule (HTML rendering §15.3.3 `u { text-decoration: underline }`
         *  — the one UA ink this ring models, which is why `u` joins the
         *  styled tag ring while em/strong/… stay out). */
        val underline: Boolean = false,
        /** `text-decoration-line: line-through`. */
        val lineThrough: Boolean = false,
    ) {
        /** True when no attribute differs from plain inherited text. */
        val isPlain: Boolean get() = this == PLAIN
    }

    /** The no-attribution instance ([Style.isPlain]'s comparand). */
    val PLAIN = Style()

    /** The admission verdict for one glyph-bearing member. */
    sealed interface Admission {
        /** The member folds; [statedLossTypes] names any box ink the ring
         *  consciously drops (border longhands) for the seam's log. */
        data class Admitted(val style: Style, val statedLossTypes: List<String>) : Admission
        /** Outside the ring — the fold bails with this reason verbatim. */
        data class Refused(val reason: String) : Admission
    }

    // The border-box longhands a styled member may carry as a STATED loss
    // (see the class banner). Colors are already paint-inert without a
    // style (css-backgrounds-3 §3.2) and become part of the same stated
    // loss when a style+width pair makes the box real.
    private val BORDER_LOSS_TYPES = setOf(
        "BorderTopStyle", "BorderRightStyle", "BorderBottomStyle", "BorderLeftStyle",
        "BorderTopWidth", "BorderRightWidth", "BorderBottomWidth", "BorderLeftWidth",
    )
    private val BORDER_COLOR_TYPES = setOf(
        "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
    )

    /** The glyph-member tags this ring styles: the wave-44 no-UA-ink trio
     *  plus `u`, whose single UA rule (underline) the ring models. */
    val STYLED_MEMBER_TAGS = setOf("span", "time", "data", "u")

    /**
     * Admit one glyph-bearing member's property bag, or refuse with the
     * first offending property named. `Hyphens` is skipped here — the
     * fold's adoption walk owns it (css-text-3 §6.1).
     *
     * @param tag the member's lowercased source tag (already checked
     *   against [STYLED_MEMBER_TAGS] by the fold).
     * @param properties the member's own wire property list.
     * @param hostProperties the host's inheritance-merged list — the
     *   effective-ink base for the TextDecorationColor equality gate.
     */
    fun admit(
        tag: String,
        properties: List<IRProperty>,
        hostProperties: List<IRProperty>,
    ): Admission {
        // The UA <u> underline (HTML rendering §15.3.3) seeds the style.
        var ink: Ink? = null
        var sizePx: Float? = null
        var sizeEm: Float? = null
        var weight: Int? = null
        var italic = false
        var underline = tag == "u"
        var lineThrough = false
        // Border longhands seen — reported as ONE stated loss when any
        // side actually paints (style keyword present; §3.2 initial none).
        var borderStyleSeen = false
        val losses = mutableListOf<String>()
        for (prop in properties) when (prop.type) {
            // Paragraph policy — the fold's adoption walk owns it.
            "Hyphens" -> {}
            // The member's own text ink (css-color-4 §3.1).
            "Color" -> {
                ink = extractInk(prop.data)
                    ?: return Admission.Refused("member-prop:Color-unresolved")
            }
            // css-fonts-4 §2.4 — absolute px or a paragraph-relative factor.
            "FontSize" -> when (val size = extractFontSize(prop.data)) {
                is FontSizeValue.Px -> sizePx = size.px
                is FontSizeValue.Factor -> sizeEm = size.factor
                null -> return Admission.Refused("member-prop:FontSize-unresolved")
            }
            // css-fonts-4 §2.2 — the wire is pre-normalized 100..900.
            "FontWeight" -> {
                weight = (prop.data as? JsonObject)?.get("weight")?.jsonPrimitive?.intOrNull
                    ?: return Admission.Refused("member-prop:FontWeight-unresolved")
            }
            // css-fonts-4 §2.2 font-style — italic/oblique slant, normal
            // no-op; anything else (oblique with angle object…) refuses.
            "FontStyle" -> when (extractKeywordish(prop.data)) {
                "italic", "oblique" -> italic = true
                "normal" -> {}
                else -> return Admission.Refused("member-prop:FontStyle-unresolved")
            }
            // css-text-decor-3 §2.1 — underline/line-through only:
            // per-range overline exists on neither platform's span API.
            "TextDecorationLine" -> {
                val lines = extractDecorationLines(prop.data)
                    ?: return Admission.Refused("member-prop:TextDecorationLine-unresolved")
                for (line in lines) when (line) {
                    "underline" -> underline = true
                    "line-through" -> lineThrough = true
                    "none" -> {}
                    else -> return Admission.Refused("member-prop:TextDecorationLine:$line")
                }
            }
            // §2.2 — admitted only when it EQUALS the member's effective
            // ink (see the class banner: span decorations paint in the
            // span's text color; a diverging wire color would be a lie).
            "TextDecorationColor" -> {
                val declared = extractInk(prop.data)
                    ?: return Admission.Refused("member-prop:TextDecorationColor-unresolved")
                val effective = ink ?: hostProperties.firstOrNull { it.type == "Color" }
                    ?.let { extractInk(it.data) }
                if (effective == null || !inkEquals(declared, effective)) {
                    return Admission.Refused("member-prop:TextDecorationColor-divergence")
                }
            }
            // §2.3 — span decorations are solid; only solid may ride.
            "TextDecorationStyle" -> when (extractKeywordish(prop.data)) {
                "solid", null -> {}
                else -> return Admission.Refused("member-prop:TextDecorationStyle-unsolid")
            }
            // css-break-4 §5 — fragments a box paint this ring does not
            // paint; inert alongside the border stated-loss below.
            "BoxDecorationBreak" -> {}
            // The border box — a stated loss, not a refusal (banner).
            in BORDER_LOSS_TYPES -> {
                if (prop.type.endsWith("Style")) borderStyleSeen = true
                if (prop.type !in losses) losses += prop.type
            }
            // Paint-inert alone; folded into the border loss when styled.
            in BORDER_COLOR_TYPES -> {}
            // Everything else is outside the ring — the fold bails with
            // the property named, the wave-44 contract.
            else -> return Admission.Refused("member-prop:${prop.type}")
        }
        // A styleless border never paints (css-backgrounds-3 §3.2) — only
        // report the loss when a style keyword makes the box real.
        val stated = if (borderStyleSeen) listOf("border-box-ink(${losses.joinToString(",")})") else emptyList()
        return Admission.Admitted(
            Style(ink, sizePx, sizeEm, weight, italic, underline, lineThrough),
            stated,
        )
    }

    /** FontSize wire → absolute px or a paragraph-relative factor. */
    private sealed interface FontSizeValue {
        data class Px(val px: Float) : FontSizeValue
        data class Factor(val factor: Float) : FontSizeValue
    }

    /**
     * The FontSize wire shapes, mirrored from TextStyleApplier's
     * extractFontSize (the paragraph pipeline's own read) so the span and
     * the paragraph can never disagree about a shape:
     *   {"px":N}/"pixels" → absolute; absolute keywords → the §2.4 ladder;
     *   {"original":{"type":"length","original":{"v":N,"u":"EM"|"REM"}}} →
     *   factor / rem px; percentage → factor; relative smaller/larger →
     *   the ×1.2 ladder step factor. Unknown → null (the caller refuses).
     */
    private fun extractFontSize(data: kotlinx.serialization.json.JsonElement): FontSizeValue? {
        if (data is JsonObject) {
            // Resolved pixels (DynamicValueResolver adds "px" upstream).
            data["px"]?.jsonPrimitive?.floatOrNull?.let { return FontSizeValue.Px(it) }
            data["pixels"]?.jsonPrimitive?.floatOrNull?.let { return FontSizeValue.Px(it) }
            val original = data["original"] as? JsonObject ?: return null
            when (original["type"]?.jsonPrimitive?.contentOrNull) {
                // css-fonts-4 §2.4 absolute-size ladder (TextStyleApplier's
                // exact px values, so the two reads agree to the pixel).
                "absolute", "absoluteKeyword" -> {
                    return when (original["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                        "xx-small" -> FontSizeValue.Px(9f)
                        "x-small" -> FontSizeValue.Px(10f)
                        "small" -> FontSizeValue.Px(13f)
                        "medium" -> FontSizeValue.Px(16f)
                        "large" -> FontSizeValue.Px(18f)
                        "x-large" -> FontSizeValue.Px(24f)
                        "xx-large" -> FontSizeValue.Px(32f)
                        "xxx-large" -> FontSizeValue.Px(48f)
                        else -> null
                    }
                }
                // §2.5 smaller/larger — one ×1.2 ladder step off the parent.
                "relative" -> {
                    return when (original["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                        "larger" -> FontSizeValue.Factor(1.2f)
                        "smaller" -> FontSizeValue.Factor(1f / 1.2f)
                        else -> null
                    }
                }
                // css-values-4 §5.1.1 — em against the parent (= the fold
                // host), rem against the 16px pinned root.
                "length" -> {
                    val inner = original["original"] as? JsonObject
                    val v = inner?.get("v")?.jsonPrimitive?.floatOrNull ?: return null
                    return when (inner["u"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                        "EM" -> FontSizeValue.Factor(v)
                        "REM" -> FontSizeValue.Px(v * 16f)
                        else -> null
                    }
                }
                // css-fonts-4 §2.4 <percentage> — against the parent size.
                "percentage" -> {
                    val pct = original["value"]?.jsonPrimitive?.floatOrNull ?: return null
                    return FontSizeValue.Factor(pct / 100f)
                }
            }
        }
        return null
    }

    /** The wire's sRGB color shape ({"srgb":{r,g,b[,a]}}) → [Ink]. */
    private fun extractInk(data: kotlinx.serialization.json.JsonElement): Ink? {
        val srgb = (data as? JsonObject)?.get("srgb") as? JsonObject ?: return null
        val r = srgb["r"]?.jsonPrimitive?.floatOrNull ?: return null
        val g = srgb["g"]?.jsonPrimitive?.floatOrNull ?: return null
        val b = srgb["b"]?.jsonPrimitive?.floatOrNull ?: return null
        // Alpha is optional on the wire (opaque when absent).
        val a = srgb["a"]?.jsonPrimitive?.floatOrNull ?: 1f
        return Ink(r, g, b, a)
    }

    /** Component-wise equality with a 1/255 tolerance — the wire ships
     *  0..1 floats quantized from 8-bit channels, so exact-float compare
     *  would refuse colors that rasterize identically. */
    private fun inkEquals(a: Ink, b: Ink): Boolean {
        val eps = 1f / 255f
        return kotlin.math.abs(a.r - b.r) <= eps && kotlin.math.abs(a.g - b.g) <= eps &&
            kotlin.math.abs(a.b - b.b) <= eps && kotlin.math.abs(a.a - b.a) <= eps
    }

    /** A lowercased bare-keyword read (string primitive or the shared
     *  keyword-object shapes), for FontStyle / TextDecorationStyle. */
    private fun extractKeywordish(data: kotlinx.serialization.json.JsonElement): String? =
        ValueExtractors.extractKeyword(data)?.lowercase()

    /** TextDecorationLine wire: a JsonArray of keywords or one bare
     *  keyword — normalized lowercase with the wire's LINE_THROUGH
     *  underscore turned back into the css hyphen (the same
     *  canonicalization TextStyleApplier.extractDecorationLineFlags
     *  applies). Null = unreadable shape (the caller refuses). */
    private fun extractDecorationLines(data: kotlinx.serialization.json.JsonElement): List<String>? =
        when (data) {
            is JsonArray -> data.map {
                (it as? JsonPrimitive)?.contentOrNull?.lowercase()?.replace('_', '-') ?: return null
            }
            else -> ValueExtractors.extractKeyword(data)?.lowercase()?.replace('_', '-')
                ?.let { listOf(it) }
        }

    /**
     * Map a span range from the fold's MERGED string into the leaf
     * pipeline's final render string. Between the two, the pipeline's
     * only character-level rewrites are (both platforms, verified against
     * the actual surgery sites):
     *   • U+00AD soft-hyphen DELETION (SoftHyphenPolicy, `hyphens: none`);
     *   • collapsible-space DELETION or '\n' REPLACEMENT and hard-break
     *     '\n' INSERTION (the greedy pre-break — Compose rule B /
     *     iOS GreedyLineBreaker);
     *   • hyphen-character INSERTION at a taken break (U+2010 / '-').
     * The alignment below walks both strings under exactly that op set —
     * matching characters advance both, a deletable original char
     * (space / soft hyphen) advances the original, an insertable
     * transformed char ('\n' / hyphen) advances the transformed — and
     * returns null the moment the strings cannot be explained by those
     * ops (the caller logs and falls back to an un-spanned render:
     * degraded STYLE, never wrong GLYPHS).
     *
     * @return transformed-string index for each original index 0..len
     *   (inclusive end sentinel), or null when unalignable.
     */
    fun alignment(original: String, transformed: String): IntArray? {
        val map = IntArray(original.length + 1)
        var i = 0 // original cursor
        var j = 0 // transformed cursor
        while (i < original.length) {
            val oc = original[i]
            when {
                // Verbatim character — the overwhelming common case.
                j < transformed.length && oc == transformed[j] -> {
                    map[i] = j; i++; j++
                }
                // A collapsible space rewritten as the line break it
                // became (the pre-break's space→'\n' replacement).
                oc == ' ' && j < transformed.length && transformed[j] == '\n' -> {
                    map[i] = j; i++; j++
                }
                // Deleted original char: collapsed space or stripped shy
                // (U+00AD written escaped — the glyph is invisible in code).
                oc == ' ' || oc == '\u00AD' -> {
                    map[i] = j; i++
                }
                // Inserted transformed char: a hard break or the
                // materialized hyphen (UA U+2010, ASCII '-').
                j < transformed.length && (transformed[j] == '\n' ||
                    transformed[j] == '\u2010' || transformed[j] == '-') -> {
                    j++
                }
                // Anything else is surgery this map does not model.
                else -> return null
            }
        }
        map[original.length] = transformed.length
        return map
    }
}
