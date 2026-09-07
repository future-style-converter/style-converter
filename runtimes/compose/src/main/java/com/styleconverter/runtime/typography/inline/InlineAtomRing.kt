// typography/inline — wave 45 (lane X2): the ATOM RING, the second breach
// in the inline-run wall. Wave 44's fold DROPPED every empty member; U1's
// banner named the cost: CSS2/cascade/inherit-computed-001's empty <em>
// carries `border: inherit`, the Chromium ref paints it as a ~6×29px black
// ring INLINE in the flowing line, and the fold painted nothing
// (wave44-final android-ref 0.8933, the ring being the named residual).
// This module resolves WHETHER an empty member paints a box and WHAT that
// box is; InlineAtomContent turns the answer into a Compose
// InlineTextContent placeholder, and InlineRunFold plants the marker.
package com.styleconverter.runtime.typography.inline

// The typed IR property bag the resolver classifies (pure data).
import com.styleconverter.runtime.core.ir.IRProperty
// The shared wire-value readers — one parse per shape, never re-derived.
import com.styleconverter.runtime.core.types.ValueExtractors
// The ONE Inter face metric pair (hhea 1984/494 over 2048) the runtime
// already uses for half-leading and decoration geometry — the ring's
// content height must come from the same numbers or the ring and the
// glyph band it straddles would disagree by a rounding.
import com.styleconverter.runtime.typography.HalfLeadingBaseline
// Wire JSON access for the css-wide `inherit` detection below.
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * InlineAtomRing — the resolved BOX PAINT of an empty inline member.
 *
 * ## The CSS model (CSS 2.1 §10.8 / css-backgrounds-3 §3)
 * An empty non-replaced inline box still generates a box: zero content
 * width, content HEIGHT equal to the font's content area (ascent+descent
 * — leading is the line's, not the box's), and its borders paint around
 * that area without ever growing the line box. inherit-computed-001's
 * `<em>` (3px solid black each side, from `border: inherit` off the
 * parent's `border: medium solid`) therefore rasterises as a 6px-wide,
 * (ascent+descent+6)px-tall black ring sitting on the baseline.
 *
 * ## The `border: inherit` WIRE DIALECT (converter gap, decoded here)
 * BorderExpander tokenises the `border` shorthand by shape, and a bare
 * alphabetic token that is not a style keyword is classified as the COLOR
 * — so `border: inherit` reaches the wire as exactly four
 * `Border*Color: {"original":"inherit"}` longhands and NOTHING else (the
 * style/width halves of the css-wide keyword are dropped; css-cascade-5
 * §2.3 says a css-wide keyword in a shorthand sets ALL its longhands).
 * The color longhand is thus the shorthand's only surviving residue, and
 * this resolver reads it as carrying the WHOLE side: style, width and
 * color all resolve from the HOST's computed side (css-cascade-4 §7.3,
 * inherit = the parent's computed value). Stated ambiguity: an authored
 * bare `border-color: inherit` (no `border-style`) produces the same wire
 * bytes and would over-paint here; the honest close is the converter
 * expanding css-wide keywords to every longhand, deferred with evidence
 * in the wave-45 lane report.
 */
object InlineAtomRing {

    /** U+FFFC OBJECT REPLACEMENT CHARACTER — the fold's in-string stand-in
     *  for one atom. Chosen because it is Unicode's designated inline-object
     *  placeholder (UAX #14 class CB: break opportunity before and after,
     *  matching an inline box boundary) and never occurs in the corpus's
     *  text; a SOURCE string carrying one bails the fold (alias guard). */
    const val MARKER: Char = '\uFFFC'

    /** A resolved paint: a concrete sRGB value straight off the wire, or
     *  the css-color-4 §7.1 `currentColor` sentinel the COMPOSABLE resolves
     *  against the paragraph's effective text color (the pure layer cannot
     *  know the ambient ink). */
    sealed interface Paint {
        /** Normalized sRGB 0..1 floats — the IR's own color space. */
        data class Concrete(val r: Float, val g: Float, val b: Float, val a: Float) : Paint
        /** Defer to the paragraph's computed text color at draw time. */
        data object CurrentColor : Paint
    }

    /** One side's computed border. [widthPx] is the USED width: forced to
     *  0 when the style is none/hidden (css-backgrounds-3 §3.3 — the
     *  computed border-width of a styleless side is zero), so
     *  `widthPx > 0` alone answers "does this side paint". */
    data class Side(val widthPx: Float, val style: String, val paint: Paint) {
        /** css-backgrounds-3 §3.2: only a styled, positive-width side paints. */
        val paints: Boolean get() = widthPx > 0f
    }

    /** The empty member's resolved border box (content is 0 × font band). */
    data class Spec(val top: Side, val right: Side, val bottom: Side, val left: Side) {
        /** An all-zero ring is paint-inert — resolve() never returns one. */
        val paints: Boolean get() = top.paints || right.paints || bottom.paints || left.paints
    }

    /** One filled rectangle of the ring, in px relative to the ring's own
     *  top-left corner. Solid-style bands only — see [bands]. */
    data class Band(val xPx: Float, val yPx: Float, val widthPx: Float, val heightPx: Float, val paint: Paint)

    // The width keywords' px values live in ValueExtractors.extractBorderWidth;
    // `medium` (the css-backgrounds-3 §3.3 initial, what Chromium uses when a
    // styled side declares no width) is 3px there and 3px here — one number.
    private const val MEDIUM_BORDER_PX = 3f

    // Styles that suppress the side entirely (computed width → 0).
    private val NO_PAINT_STYLES = setOf("NONE", "HIDDEN")

    /** The four physical sides, in the fixed Top/Right/Bottom/Left order the
     *  IR's longhand names use. */
    private val SIDES = listOf("Top", "Right", "Bottom", "Left")

    /** Whether a Border*Color wire value is the css-wide `inherit` keyword —
     *  the shape BorderExpander emits for `border: inherit` (dialect banner
     *  above). Both the {"original":"inherit"} envelope and a bare primitive
     *  are accepted so a future converter tweak cannot silently defeat it. */
    private fun isInheritColor(data: JsonElement?): Boolean = when (data) {
        is JsonObject -> (data["original"] as? JsonPrimitive)?.contentOrNull?.lowercase() == "inherit"
        is JsonPrimitive -> data.contentOrNull?.lowercase() == "inherit"
        else -> false
    }

    /** First property of [type] in [props], by wire name. */
    private fun prop(props: List<IRProperty>, type: String): JsonElement? =
        props.firstOrNull { it.type == type }?.data

    /** Parse a wire color to a [Paint]: concrete sRGB when the converter
     *  resolved one, else `currentColor` — which is ALSO the right reading
     *  for an unparseable original (e.g. the host's `border: medium solid`
     *  mis-expansion leaves BorderColor:{"original":"medium"}): the
     *  browser's un-declared border-color IS currentColor, so falling to
     *  the sentinel reproduces its computed value instead of inventing one. */
    private fun paintOf(data: JsonElement?, ownColor: JsonElement?): Paint {
        // The declared color, when the wire carries a resolved sRGB payload.
        val declared = ValueExtractors.extractColor(data)
        if (declared != null) return Paint.Concrete(declared.red, declared.green, declared.blue, declared.alpha)
        // currentColor resolves against the box's OWN computed `color` first
        // (css-color-4 §7.1); only when that too is absent does the sentinel
        // defer to the ambient inherited ink at draw time.
        val own = ValueExtractors.extractColor(ownColor)
        if (own != null) return Paint.Concrete(own.red, own.green, own.blue, own.alpha)
        return Paint.CurrentColor
    }

    /** One side's computed border from a property bag: style gates, width
     *  defaults to `medium` on a styled side, color falls through
     *  currentColor. [colorData] is passed separately so the inherit branch
     *  can hand the HOST's color longhand in. */
    private fun sideOf(props: List<IRProperty>, side: String, colorData: JsonElement?, ownColor: JsonElement?): Side {
        // Style: absent computes to the `none` initial → the side is off.
        val style = ValueExtractors.extractKeyword(prop(props, "Border${side}Style"))?.uppercase() ?: "NONE"
        // A styleless/hidden side has USED width 0 (css-backgrounds-3 §3.3).
        val widthPx = if (style in NO_PAINT_STYLES) 0f
        // Declared width in px (the wire pre-resolves lengths); a styled
        // side with no declared width takes the `medium` initial (3px).
        else ValueExtractors.extractBorderWidth(prop(props, "Border${side}Width"))?.value ?: MEDIUM_BORDER_PX
        return Side(widthPx, style, paintOf(colorData, ownColor))
    }

    /**
     * Resolve an EMPTY member's box paint, or null when it is paint-inert
     * (the caller keeps wave-44's drop-and-report behaviour for null).
     *
     * Per side: a member Border*Color of `inherit` routes the WHOLE side to
     * the host's computed border (the dialect banner above); any other
     * shape resolves from the member's own longhands. Background is
     * deliberately NOT consulted: an empty inline's background paints the
     * 0-wide content/padding box — invisible without padding, and padded
     * empty members still bail at the fold's property gate.
     */
    fun resolve(memberProps: List<IRProperty>, hostProps: List<IRProperty>): Spec? {
        // The member's own computed `color` — the currentColor base for its
        // OWN sides (the host-inherited sides use the HOST's color instead:
        // `inherit` takes the parent's COMPUTED value, already resolved
        // against the parent's own currentColor, css-cascade-4 §7.3).
        val memberColor = prop(memberProps, "Color")
        val hostColor = prop(hostProps, "Color")
        // Resolve the four sides independently — `border: inherit` always
        // routes all four, but per-side longhands may mix.
        val sides = SIDES.map { side ->
            val memberColorData = prop(memberProps, "Border${side}Color")
            if (isInheritColor(memberColorData)) {
                // Inherited side: style/width/color all from the host's
                // computed side (the color's own currentColor fallback is
                // the HOST's color — see memberColor note above).
                sideOf(hostProps, side, prop(hostProps, "Border${side}Color"), hostColor)
            } else {
                // Own side: the member's longhands, member currentColor.
                sideOf(memberProps, side, memberColorData, memberColor)
            }
        }
        val spec = Spec(top = sides[0], right = sides[1], bottom = sides[2], left = sides[3])
        // Paint-inert (no styled side anywhere) → null → wave-44 drop path.
        return spec.takeIf { it.paints }
    }

    /** The empty box's CONTENT height: the font's ascent+descent band at
     *  [fontSizePx] (CSS 2.1 §10.6.1 — the content area of a non-replaced
     *  inline is font-based). Uses the pinned Inter hhea ratios, UNROUNDED:
     *  the wave44-final ref rasterises inherit-computed-001's ring 29px
     *  tall at 19.2px = (1.209961 × 19.2) + 6 = 29.23 — Blink's per-metric
     *  face rounding (19+5=24 content → 30 ring) would overshoot it. */
    fun contentHeightPx(fontSizePx: Float): Float =
        (HalfLeadingBaseline.ASCENT_EM + HalfLeadingBaseline.DESCENT_EM) * fontSizePx

    /** The ring's border-box size: zero content width plus the side widths. */
    fun ringSizePx(spec: Spec, fontSizePx: Float): Pair<Float, Float> =
        (spec.left.widthPx + spec.right.widthPx) to
            (contentHeightPx(fontSizePx) + spec.top.widthPx + spec.bottom.widthPx)

    /**
     * The ring as filled rectangles, ring-relative px — the classic four
     * border bands around a 0-wide content column (top/bottom span the full
     * ring width; left/right fill the rows between them). All styles paint
     * SOLID here; the composable logs non-solid styles once rather than
     * silently dropping them (repo rule: no silent fallthrough).
     */
    fun bands(spec: Spec, fontSizePx: Float): List<Band> {
        val (ringW, ringH) = ringSizePx(spec, fontSizePx)
        val out = mutableListOf<Band>()
        // Top band — full width, the side's own height.
        if (spec.top.paints) out += Band(0f, 0f, ringW, spec.top.widthPx, spec.top.paint)
        // Bottom band — full width, anchored at the ring's bottom edge.
        if (spec.bottom.paints) out += Band(0f, ringH - spec.bottom.widthPx, ringW, spec.bottom.widthPx, spec.bottom.paint)
        // The vertical run between the horizontal bands (unstyled top/bottom
        // have width 0, so the sides then span the whole ring height).
        val innerTop = spec.top.widthPx
        val innerH = ringH - spec.top.widthPx - spec.bottom.widthPx
        // Left band — the side's own width, between the horizontal bands.
        if (spec.left.paints) out += Band(0f, innerTop, spec.left.widthPx, innerH, spec.left.paint)
        // Right band — anchored at the ring's right edge.
        if (spec.right.paints) out += Band(ringW - spec.right.widthPx, innerTop, spec.right.widthPx, innerH, spec.right.paint)
        return out
    }
}
