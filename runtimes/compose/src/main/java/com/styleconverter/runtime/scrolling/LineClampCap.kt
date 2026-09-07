package com.styleconverter.runtime.scrolling

// Wave 41 (lane T6) — the BLOCK-LEVEL line-clamp height cap.
//
// css-overflow-4 §5 expands `line-clamp: <n>` into `max-lines: <n>` +
// `block-ellipsis: auto` + `continue: discard`: the clamp container keeps
// its first <n> LINE BOXES and discards everything after them, and §4.3
// makes the discarded content unpaintable (it "is not rendered"). The
// leaf path already honors this for a component whose text lives in ONE
// Compose Text (ComponentRenderer.placeholderMaxLines → Text(maxLines)),
// but a clamp root whose line boxes live in CHILD components — `<span>`
// runs, nested `<p>`/`<div>` blocks — rendered every child unclamped:
// wave-40 pixels show block-ellipsis-012's 1-line box rendered 3 lines
// tall on Android (border rows 16..66 vs the ref's 16..33), and
// block-ellipsis-032's three 1-line boxes rendered 4 stacked runs each
// (Android SSIM 0.741). Text maxLines cannot reach children, so the
// container itself must cap: this file computes the used cap height
// (N line boxes + the vertical bands that live inside the overflow node)
// and provides the measure-then-cap layout modifier OverflowApplier
// chains whenever a fixed-count LineClamp property is on the wire.

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.renderer.composedDefaultLineHeightPx
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.SpacingExtractor
import com.styleconverter.runtime.spacing.resolveToDp
import com.styleconverter.runtime.typography.LineClampWire
import com.styleconverter.runtime.typography.TypographyExtractor
import kotlin.math.ceil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

object LineClampCap {

    // Tolerance (px) between the estimated cap and a measurement that is
    // ALREADY clamped by the leaf Text path. A leaf's N clamped lines
    // measure N × the same pinned line box this file computes, so the two
    // agree up to Int-rounding of fractional line heights (monospace
    // 13px × 1.25 = 16.25px/line); 2px absorbs that rounding so the cap
    // NEVER re-clips a correctly clamped leaf (block-ellipsis-013 keeps
    // its passing 34px Android box byte-identically), while a genuine
    // overflow is always ≥ one extra line box (≥ 16px) past the cap and
    // still trips it.
    const val SLACK_PX: Float = 2f

    /**
     * The clamp line count, gated on the RAW wire shape.
     *
     * Only the `{"type":"lines","count":N}` variant caps (the shorthand's
     * `<integer [1,∞]>` grammar, css-overflow-4 §5.1 — the converter's
     * LineClampPropertyParser emits exactly this for it). `none` (clamp
     * off), `auto` (clamp at the box's own block-size constraint — no
     * fixed line count to turn into a height) and any legacy shape
     * deliberately return null so this cap never guesses. The count read
     * mirrors TypographyExtractor.extractLineClamp (the typography lane's
     * wire read) including its ≥1 guard — sub-1 counts are outside the
     * grammar and mean "clamp off", never "0-height box".
     */
    fun linesCount(properties: List<Pair<String, JsonElement?>>): Int? {
        // The LAST LineClamp declaration wins, mirroring CSS cascade order
        // exactly like the typography extractor's fold over the same list.
        val data = properties.lastOrNull { it.first == "LineClamp" }?.second
            ?: return null // no declaration → no cap; the common fast path.
        // Non-object payloads are legacy shapes the cap refuses (see doc).
        val obj = data as? JsonObject ?: return null
        // The sealed-variant discriminator the converter serializes.
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
        // Gate: only the fixed-count variant maps to a fixed cap height.
        if (type != "lines") return null
        // IRNumber serializes the count as a float ("count": 2.0); the
        // ≥1 guard is the css-overflow-4 §5.1 <integer [1,∞]> grammar.
        return (obj["count"] as? JsonPrimitive)?.floatOrNull?.toInt()
            ?.takeIf { it >= 1 }
    }

    /**
     * Retrospective R3 (A5#4) — true when the declaration forbids a marker
     * (`line-clamp: 4 no-ellipsis` / `4 ""`, css-overflow-4 §4.2). Twin of
     * Swift's LineClampCap.markerSuppressed, delegated to the ONE Compose
     * wire reader ([LineClampWire]) so the cap, the leaf Text and the
     * typography config can never disagree about the marker. Unlike Swift,
     * the Compose cap needs no re-route on it: Text(maxLines, Clip) already
     * discards without a glyph, so this is exposed for the renderer's
     * overflow DECISION (Clip, never Ellipsis) and for the JVM pins.
     */
    fun markerSuppressed(properties: List<Pair<String, JsonElement?>>): Boolean =
        LineClampWire.markerSuppressedInPairs(properties)

    /**
     * The full cap height in px for this component, or null when no cap
     * applies. The cap is what the STEP-7 overflow node may measure:
     *
     *   N × lineBox  +  vertical CSS padding  +  vertical border band
     *
     * because the modifier chain (StyleApplier.applyConfig steps 4-8 +
     * ComponentRenderer's inner chain) nests the css padding (step 8),
     * the placeholder floor and the border-band content inset all INSIDE
     * the step-7 overflow position — the wave-40 leaf calibration pins
     * it: block-ellipsis-013's PASSING Android box measures 35px against a
     * 34.5px cap (2 lines × 16.25 — monospace 13px × the 1.25 ref pin — +
     * 2 × 1px border band = 34.5, ceil → 35; the wave49-final capture and
     * the frozen ref both ink rows 16..50, and LineClampCapTest's
     * "measurement within slack" pin carries the same 35-vs-34.5 numbers;
     * retro A3#13 corrected the "34px" arithmetic that stood here).
     */
    fun capPx(properties: List<Pair<String, JsonElement?>>): Float? {
        // Fixed-count clamp on the wire, else nothing to do (fast bail —
        // this runs inside every component's overflow extraction).
        val lines = linesCount(properties) ?: return null
        // Reuse the typography lane's OWN resolution of font-size and
        // line-height (the same public function StyleApplier already runs
        // for this very component), so cap metrics and leaf-Text metrics
        // can never disagree: fontSize carries the monospace-13px UA
        // quirk (MonospaceUAFontSize) and lineHeight the multiplier ×
        // font-size resolution — both in sp, and the runtime's
        // px==sp==dp space makes .value the px count.
        val typo = TypographyExtractor.extractTypographyConfig(properties)
        // The element's used font size in px: declared (or UA-monospace
        // 13px, folded in by the extractor) else the CSS body default 16.
        val fontPx = typo.fontSize?.takeIf { it.isSp }?.value ?: 16f
        // One line box: a DECLARED line-height wins verbatim (css-inline-3
        // §4.2 — line-clamp-005 pins `font: 16px/32px` → 32px boxes);
        // otherwise the corpus-v4.1 composed default — font-size × 1.25,
        // the REF_LINE_HEIGHT pin shared by all four surfaces (see
        // core/renderer WptCaptureMode.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO).
        val lineBox = typo.lineHeight?.takeIf { it.isSp }?.value
            ?: composedDefaultLineHeightPx(composedWpt = true, fontSizePx = fontPx)
        // A non-positive line box can only come from a broken wire —
        // refuse to build a 0-height cap out of it.
        if (lineBox <= 0f) return null
        // Vertical CSS padding — resolved EXACTLY like the padding
        // modifier resolves it (same extractor + resolver + the default
        // context StyleApplier.placeholderFloorMinSize uses), so the band
        // the cap budgets is the band the chain actually lays out.
        val resolved = SpacingExtractor.extractPaddingConfig(properties)
            .resolve(isRtl = false)
        // The default resolution context: px-exact values dominate the
        // corpus; relative units resolve against the same defaults the
        // floor math uses, so cap and floor never disagree.
        val ctx = SpacingContext()
        // Negative padding is not a thing in CSS — clamp at 0 exactly
        // like the floor math does.
        val padTop = resolveToDp(resolved.top, ctx).value.coerceAtLeast(0f)
        val padBottom = resolveToDp(resolved.bottom, ctx).value.coerceAtLeast(0f)
        // Vertical border band — the content inset borderContentInset
        // chains inside the overflow node; borderBandInsets is the SAME
        // per-side math (a side reserves width only for a VISIBLE
        // border), returned in (start, top, end, bottom) order.
        val band = StyleApplier.borderBandInsets(
            BorderSideExtractor.extractBorderConfig(properties)
        )
        // Sum the block-axis pieces: N line boxes + padding + border band.
        return lines * lineBox + padTop + padBottom + band[1].value + band[3].value
    }

    /**
     * The pure cap decision (JVM-pinnable without a draw pass): the
     * measured height survives untouched unless it exceeds the cap by
     * more than [SLACK_PX] — i.e. the content genuinely laid out extra
     * line boxes the clamp must discard. The cap itself is ceil'd so a
     * fractional line box (16.25px monospace lines) never loses its last
     * physical pixel row to Int truncation.
     */
    fun cappedHeight(measuredPx: Int, capPx: Float): Int {
        // The integer cap the box reports when clamping fires.
        val cap = ceil(capPx).toInt()
        // Within tolerance → the leaf path (or short content) already
        // satisfies the clamp: keep the measurement byte-identically.
        return if (measuredPx > cap + SLACK_PX) cap else measuredPx
    }
}

/**
 * Cap this node's LAYOUT height at [capPx] (the N-line-box budget), while
 * measuring content on an UNBOUNDED block axis so text keeps its natural
 * line layout — the visible prefix must be the same first N lines the
 * unclamped layout produces (css-overflow-4 §5.3.2: discarding content must
 * not re-flow what precedes it). Chain an [axisSelectiveClip] Y-clip
 * OUTSIDE this node (OverflowApplier does) so the discarded lines' ink is
 * actually unpaintable, and never clip X: a clamp does not create an
 * inline-axis clip (block-ellipsis-013's preserved first line paints past
 * the border box's right edge in the ref, and must keep doing so).
 */
fun Modifier.lineClampHeightCap(capPx: Float): Modifier =
    // Modifier.layout: a measure/report-size wrapper — no layer, no draw.
    layout { measurable, constraints ->
        // Unbound the block axis (keep the inline axis exactly as given so
        // wrapping width is untouched): content lays out ALL its lines.
        val inner = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        // One measurement — the natural (unclamped) content geometry.
        val placeable = measurable.measure(inner)
        // Pure decision: keep the measurement, or discard past the cap.
        val capped = LineClampCap.cappedHeight(placeable.height, capPx)
        // Whatever was decided must still honor the INCOMING constraints —
        // an explicit `height:` (applied outer, chain step 4) wins over
        // the cap exactly like CSS used-height wins over max-lines.
        val h = capped.coerceIn(constraints.minHeight, constraints.maxHeight)
        // Report the capped box; place content at the origin so its first
        // N lines fill it and the discarded tail hangs below (the outer
        // Y-clip erases that ink, css-overflow-4 §5.3.2).
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
