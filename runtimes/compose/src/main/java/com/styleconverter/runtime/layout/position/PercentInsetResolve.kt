package com.styleconverter.runtime.layout.position

// Wave 49 (lane A7) — PERCENTAGE INSETS (top/right/bottom/left and their
// logical spellings), CSS 2.1 §9.4.3 read through css-position-3
// §"Relative Positioning" (drafts.csswg.org/css-position-3/#relpos-insets).
//
// ## The measured defect
// `tools/titan/runs/wave48-final/sections/css-position/per-test-ir/
//  wpt__css-position__position-relative-006.json` is three components:
//
//   { "type":"Width",  "data":{"type":"length","px":100} }
//   { "type":"MinHeight","data":{"type":"length","px":100} }   ← parent, RED
//   …
//   { "type":"Top", "data":-10000 }                            ← child, GREEN
//   { "type":"Position", "data":"RELATIVE" }
//
// `data: -10000` is the converter's IRPercentage wire for `top: -10000%`
// (InsetValueSerializer emits a BARE JSON NUMBER for
// InsetValue.PercentageValue; a length is always the object shape
// `{"px":N}` / `{"type":"length","px":N}`). Both natives fed that bare
// number to their px extractor — Compose `ValueExtractors.extractDp`
// takes `is JsonPrimitive -> json.doubleOrNull?.dp` — so `-10000%`
// became `-10000.dp`, translating the green square 10 000 px off the
// canvas and leaving the RED parent bare. Frozen captures:
// `…/report/images/{Android,iOS}/wpt__css-position__position-relative-006.png`
// are a pure-red 100×100 square where
// `tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/
//  css-position/position-relative-006.png` paints it green.
//
// ## What CSS actually says
// A percentage inset resolves against the CORRESPONDING dimension of the
// containing block — the inline (width) axis for left/right, the block
// (height) axis for top/bottom (CSS 2.1 §9.4.3). css-position-3's
// relpos-insets section adds the clause this test asserts: when that
// dimension is INDEFINITE the percentage resolves to ZERO rather than
// scaling something unknown. position-relative-006's own
// `<meta name=assert>` states it verbatim — "a relative-positioned
// element inset doesn't resolve against an indefinite size" — and its
// parent declares `min-height`, never `height`, so the block axis is
// indefinite and the used inset is 0.
//
// ## The channel this reads, and the guard on it
// `DynamicValueResolver.childContainingBlock` publishes the ancestor's
// CONTENT box on `LocalContainingBlock` with a null axis meaning "not
// statically definite". That null CONFLATES two different things:
//   (a) the ancestor IS modelled and its block size is genuinely `auto`
//       — CSS-indefinite, the §relpos-insets zero case; and
//   (b) the ancestor declared NO size at all (an inline `<span>`, a
//       `<tbody>`), so the channel simply has no model of the real
//       containing block, which per CSS 2.1 §10.1 is the nearest BLOCK
//       CONTAINER ancestor — a box the channel skipped over.
// Only (a) is CSS-indefinite. [resolve] therefore treats a null axis as
// zero only when the OTHER axis of the same containing block is
// definite, which is the evidence that the ancestor was modelled at all;
// with both axes null it keeps the pre-wave-49 value untouched.
// That guard is a CHANNEL-GAP conservatism, not spec text — see the
// honest write-up in the lane report. Live corpus witnesses of both
// shapes, from the frozen wave-48 IR:
//   • position-relative-006 child → parent has Width=100px + MinHeight,
//     no Height → cb=(100, null) → case (a) → 0. REPAIRED.
//   • position-relative-002 child → parent is a `<span>` with no size →
//     cb=(null, null) → case (b) → untouched (and that test passes today
//     only because its containing block happens to be exactly 100px, so
//     `-100%` and `-100px` coincide).
//
// ## Blast radius, enumerated before the change
// Scanning every per-test-ir document of all 30 frozen wave-48 sections
// for a bare-number (percentage) inset yields 10 properties across
// exactly 7 tests, all in css-position: position-relative-001…006 and
// -008. Of those, only -006's child sits under a modelled containing
// block with an indefinite block axis; -001/-003/-004/-005 read
// cb=(100,100) and resolve to the same number they already used, and
// -002/-008 hit the both-axes-null guard. Every other inset in the
// corpus is the `{"px":N}` object shape, which never enters this file.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.variables.ContainingBlock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The percentage magnitudes (already in CSS percent units — `-100%` is
 * carried as `-100f`) of whichever inset longhands the IR wrote as a
 * bare number. One nullable slot per inset longhand the parser
 * recognises, so a mixed declaration (`top: 50%; left: 10px`) keeps the
 * px side on the legacy path untouched.
 */
data class PercentInsets(
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    val left: Float? = null,
    val insetBlockStart: Float? = null,
    val insetBlockEnd: Float? = null,
    val insetInlineStart: Float? = null,
    val insetInlineEnd: Float? = null,
) {
    /** True when at least one side was declared as a percentage. */
    val any: Boolean
        get() = top != null || right != null || bottom != null || left != null ||
            insetBlockStart != null || insetBlockEnd != null ||
            insetInlineStart != null || insetInlineEnd != null
}

object PercentInsetResolve {

    /**
     * The percentage an inset property's IR payload carries, or null when
     * it is not the percentage wire shape.
     *
     * The converter's `InsetValueSerializer` (converter/src/main/kotlin/
     * app/irmodels/properties/layout/position/PositionValueTypes.kt)
     * encodes exactly three inset shapes: the string `"auto"`, an OBJECT
     * for lengths / `expr` / `anchor()`, and a BARE JSON NUMBER for
     * `InsetValue.PercentageValue` (IRPercentage serialises as its scalar).
     * So "bare number ⇔ percentage" is a property of the wire, not a
     * heuristic — and it is exactly the shape `ValueExtractors.extractDp`
     * mistakes for pixels.
     */
    fun percentOf(json: JsonElement?): Float? {
        val primitive = json as? JsonPrimitive ?: return null
        // A quoted primitive is the "auto" keyword, never a percentage.
        if (primitive.isString) return null
        return primitive.doubleOrNull?.toFloat()
    }

    /**
     * Resolve [config]'s percentage insets against [cb] and return a copy
     * with the physical/logical Dp slots replaced. Returns [config]
     * unchanged when there is nothing to resolve or when the channel
     * carries no model of the containing block (the case-(b) guard in the
     * file header).
     */
    fun resolve(config: PositionConfig, cb: ContainingBlock): PositionConfig {
        val pct = config.percent ?: return config
        if (!pct.any) return config
        // Case-(b) guard: both axes null ⇒ the ancestor published no
        // containing block at all, so we cannot tell CSS-indefinite from
        // channel-gap. Keeping the legacy value is the honest degradation
        // — it can never move a box that renders correctly today.
        // TODO(wave-49 A7): the spec-true repair is for a non-block-
        // container ancestor to REPUBLISH its own containing block
        // (CSS 2.1 §10.1) instead of nulling it; that lives in the
        // renderer's LocalContainingBlock provider, outside this lane.
        if (cb.widthPx == null && cb.heightPx == null) return config

        // Axis assignment per CSS 2.1 §9.4.3: left/right (and the inline
        // logical spellings) resolve against the containing block's WIDTH;
        // top/bottom (and the block logical spellings) against its HEIGHT.
        val inlineBase = cb.widthPx
        val blockBase = cb.heightPx

        return config.copy(
            top = side(pct.top, blockBase, config.top),
            end = side(pct.right, inlineBase, config.end),
            bottom = side(pct.bottom, blockBase, config.bottom),
            start = side(pct.left, inlineBase, config.start),
            insetBlockStart = side(pct.insetBlockStart, blockBase, config.insetBlockStart),
            insetBlockEnd = side(pct.insetBlockEnd, blockBase, config.insetBlockEnd),
            insetInlineStart = side(pct.insetInlineStart, inlineBase, config.insetInlineStart),
            insetInlineEnd = side(pct.insetInlineEnd, inlineBase, config.insetInlineEnd),
        )
    }

    /**
     * One side's used inset.
     *
     * [percent] null ⇒ this side was a length, keep [legacy] verbatim.
     * [base] non-null ⇒ the containing block axis is definite, so the
     * percentage scales it (css-values-4 §5.5). [base] null with the
     * other axis definite ⇒ the axis is CSS-indefinite and the used value
     * is 0 (css-position-3 §relpos-insets — position-relative-006's
     * assert). The `px`/`dp` unit swap is the engine-wide convention:
     * every IR pixel is applied as a Compose `Dp` (see
     * ValueExtractors.extractDp), and [cb] is denominated in the same
     * IR pixels, so the product needs no density conversion.
     */
    private fun side(percent: Float?, base: Float?, legacy: Dp?): Dp? {
        if (percent == null) return legacy
        if (base == null) return 0.dp
        return (base * percent / 100f).dp
    }
}
