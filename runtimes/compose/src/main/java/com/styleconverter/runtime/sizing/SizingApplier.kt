package com.styleconverter.runtime.sizing

// Phase 3 SizingApplier — collapses a SizingConfig into a Modifier chain. For
// px-only inputs (the common visual-test case) the emitted modifier chain is
// byte-identical to the old SizingApplier: Modifier.width/height/widthIn/
// heightIn/aspectRatio with the same Dp values.
//
// Relative units (em, rem, vw, %) are resolved through the spacing module's
// SpacingResolve helper so there's one source of truth. Compose can't express
// min-content/max-content directly on a size modifier — we approximate with
// wrapContentWidth/Height() which at least reads as "shrink to content".

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.resolveToDp

object SizingApplier {

    /** Apply [config] to [modifier]. Returns [modifier] unchanged if empty. */
    fun applySizing(modifier: Modifier, config: SizingConfig): Modifier {
        if (!config.hasSizing) return modifier
        val ctx = SpacingContext()
        var r = modifier
        // CSS clamp semantics: `effective = max(min, min(width, max))`.
        // Compose's `Modifier.width(W).widthIn(max=M)` does NOT enforce
        // this — width(W) is an EXACT constraint that wins regardless
        // of the chained widthIn, so `width: 300; max-width: 50` rendered
        // as 300 wide on Android (matching iOS's same bug, both fixed
        // together). Pre-clamp the explicit width here so .width() gets
        // the already-clamped value.
        val rawW = config.width ?: config.inlineSize
        val rawH = config.height ?: config.blockSize
        val maxWv = config.maxWidth ?: config.maxInlineSize
        val maxHv = config.maxHeight ?: config.maxBlockSize
        val minWv = config.minWidth ?: config.minInlineSize
        val minHv = config.minHeight ?: config.minBlockSize
        // Physical width wins over logical inlineSize (CSS spec).
        // Lane BX — `box-sizing: content-box` (css-sizing-3 §3): the
        // declared width/height size the CONTENT box, but this chain's
        // Modifier.width/height frame is the BORDER box (padding is
        // chained innermost — StyleApplier.applyConfig step 8 — and the
        // border band inset sits inside too), so the frame must grow by
        // the pre-resolved padding+border bands. Inflation runs AFTER
        // clampLength because min/max also operate in content-box
        // coordinates per spec; only the final frame value converts.
        // inflateForContentBox is identity unless boxSizing is an
        // EXPLICIT CONTENT_BOX (null = unset keeps border-box), so the
        // whole existing corpus keeps byte-identical modifier chains.
        r = applyWidth(r, inflateForContentBox(
            clampLength(rawW, minWv, maxWv, ctx),
            config.boxSizing, config.contentBoxInflateX), ctx)
        r = applyHeight(r, inflateForContentBox(
            clampLength(rawH, minHv, maxHv, ctx),
            config.boxSizing, config.contentBoxInflateY), ctx)
        // Min/max constraints — kept for the case where no explicit
        // width/height was set (then clamp short-circuits to null and
        // widthIn/heightIn carry the intent).
        r = applyWidthIn(r, minWv, maxWv, ctx)
        r = applyHeightIn(r, minHv, maxHv, ctx)
        // aspect-ratio. ratio=0.0 with isAuto means auto-only — skip modifier
        // and let Compose auto-size.
        //
        // css-sizing-4 §5.1: a preferred aspect ratio only takes effect when
        // AT LEAST ONE of the two sizes is auto — with both width and height
        // explicitly set, the ratio is ignored entirely. Compose's
        // aspectRatio modifier doesn't know that rule and re-measured the
        // box to 180×101 on Spacing_C01_AspectRatio (`width:180; height:150;
        // aspect-ratio:16/9`) while web/iOS kept the declared 180×150
        // (Android-web 0.806). Skip the modifier when both axes are pinned.
        config.aspectRatio?.let { ar ->
            if (ar.ratio > 0.0 && !(isDefiniteAxis(rawW) && isDefiniteAxis(rawH))) {
                r = r.aspectRatio(ar.ratio.toFloat())
            }
        }
        return r
    }

    /**
     * True when a size slot pins its axis (any value that produces a size
     * modifier above). `auto` / `none` / absent / unresolvable-calc leave
     * the axis auto — those are the cases where aspect-ratio may act.
     */
    private fun isDefiniteAxis(v: LengthValue?): Boolean = when (v) {
        is LengthValue.Exact, is LengthValue.Relative, is LengthValue.Intrinsic -> true
        else -> false
    }

    /**
     * Lane BX — pure content-box→border-box axis conversion, internal so
     * JUnit pins the arithmetic (width 100 + padding 16×2 + border 2×2 →
     * frame 136). Identity unless [boxSizing] is an EXPLICIT
     * [BoxSizingKeyword.CONTENT_BOX] — the tri-state guard (null = unset)
     * that keeps every fixture captured against web's border-box reset
     * byte-stable, and keeps explicit `border-box` declarations (the
     * fixture's passing V1_border sentinel) untouched. Only Exact px
     * inflates: percent widths route to fillMaxWidth (fractional — no px
     * to add) and em/rem resolve at apply time; both stay border-box with
     * the honest divergence noted here rather than half-inflating (no
     * silent fallthrough: content-box + non-px sizes have no fixture yet).
     */
    internal fun inflateForContentBox(
        v: LengthValue?,
        boxSizing: BoxSizingKeyword?,
        inflatePx: Float
    ): LengthValue? {
        // Not explicitly content-box → border-box status quo, untouched.
        if (boxSizing != BoxSizingKeyword.CONTENT_BOX) return v
        // Only definite px sizes reinterpret (css-sizing-3 §3); auto /
        // intrinsic / relative shapes pass through unchanged.
        if (v !is LengthValue.Exact) return v
        // content-box: frame = declared content size + padding + border.
        return LengthValue.Exact(v.px + inflatePx)
    }

    /**
     * TITAN WPT lane — resolve the box-sizing TRI-STATE for a capture mode.
     * Pure decision (JUnit-pinned in WptBoxSizingDefaultTest) so the mode
     * split can never silently drift:
     *   - a DECLARED keyword always wins verbatim (a WPT test that writes
     *     `box-sizing: border-box` must keep it);
     *   - undeclared (null) in WPT capture mode defaults to CONTENT_BOX —
     *     css-sizing-3 §3's initial value IS content-box (the UA default
     *     the WPT refs are authored against; the browser-ref render in
     *     tools/titan/capture-browser-ref.mjs applies no reset), the twin
     *     of the web harness's `body.wpt-mode [data-component-id] {
     *     box-sizing: content-box }` override in apps/web-harness/index.html.
     *     Without it the 4 grid *-large-border-padding tests rendered
     *     100x500 border boxes where the ref shows the padding+border-grown
     *     172-wide content-box arithmetic (pixel-verified);
     *   - undeclared OUTSIDE WPT mode stays null — the border-box status
     *     quo the whole dark-stage/327-pair baseline corpus is captured
     *     against (web's `* { box-sizing: border-box }` reset) is frozen.
     */
    internal fun effectiveBoxSizing(
        declared: BoxSizingKeyword?,
        wptCaptureMode: Boolean
    ): BoxSizingKeyword? =
        // Declared wins; only the UNSET slot picks up the WPT UA default.
        declared ?: if (wptCaptureMode) BoxSizingKeyword.CONTENT_BOX else null

    /**
     * Pre-clamp an explicit width/height by min/max so the resulting
     * Modifier.width/height honors CSS clamp semantics. Only operates on
     * the LengthValue.Exact + LengthValue.Relative-Px shapes — intrinsic
     * (min-content/etc.) and percentage values pass through unchanged
     * because their resolution depends on parent context that's only
     * available at layout time.
     */
    private fun clampLength(
        v: LengthValue?,
        min: LengthValue?,
        max: LengthValue?,
        ctx: SpacingContext
    ): LengthValue? {
        if (v !is LengthValue.Exact) return v
        var px = v.px
        // toDpOrNull resolves min/max to Dp using the same context as the
        // applier's own widthIn/heightIn path — keeps unit handling consistent.
        val mxDp = toDpOrNull(max, ctx)?.value
        val mnDp = toDpOrNull(min, ctx)?.value
        if (mxDp != null) px = kotlin.math.min(px, mxDp.toDouble())
        if (mnDp != null) px = kotlin.math.max(px, mnDp.toDouble())
        return LengthValue.Exact(px)
    }

    /** Width axis. */
    private fun applyWidth(m: Modifier, v: LengthValue?, ctx: SpacingContext): Modifier = when (v) {
        null, LengthValue.Unknown, LengthValue.Auto, LengthValue.None -> m  // no override
        is LengthValue.Exact -> m.width(v.px.toFloat().dp)
        is LengthValue.Relative -> if (v.unit == LengthUnit.PERCENT) {
            // % on width resolves against parent width — Compose has a direct
            // modifier for that. We clamp to [0,1] since fillMaxWidth rejects
            // values outside that range at runtime.
            m.fillMaxWidth((v.value.toFloat() / 100f).coerceIn(0f, 1f))
        } else {
            // Non-% relative (em/vw/…) goes through the spacing resolver.
            m.width(resolveToDp(v, ctx))
        }
        is LengthValue.Intrinsic -> when (v.kind) {
            // width: min-content → the box takes its MIN intrinsic width
            // (css-sizing-3 §4: the narrowest width that avoids overflow —
            // for text, the widest unbreakable run). Compose expresses this
            // directly as Modifier.width(IntrinsicSize.Min). The previous
            // wrapContentWidth() let content pick its PREFERRED width, so
            // `width: min-content` rendered max-content-wide — web showed a
            // letter-wrapped sliver while Android filled the line
            // (PW_Sizing_Spacing_02, A-w 0.698 / 28.5% px).
            LengthValue.IntrinsicKind.MIN_CONTENT -> m.width(IntrinsicSize.Min)
            // width: max-content → MAX intrinsic width (no-wrap preferred
            // size, css-sizing-3 §4).
            LengthValue.IntrinsicKind.MAX_CONTENT -> m.width(IntrinsicSize.Max)
            // fit-content(<bound>): Compose has no direct analog; use the
            // bound as a max-width constraint which approximates "content,
            // but capped at bound".
            LengthValue.IntrinsicKind.FIT_CONTENT -> {
                val bound = v.bound?.let { resolveToDp(it, ctx) }
                if (bound != null) m.widthIn(max = bound) else m.wrapContentWidth()
            }
        }
        is LengthValue.Calc, is LengthValue.Fraction -> m  // unresolved → skip
    }

    /** Height axis. Mirror of applyWidth. */
    private fun applyHeight(m: Modifier, v: LengthValue?, ctx: SpacingContext): Modifier = when (v) {
        null, LengthValue.Unknown, LengthValue.Auto, LengthValue.None -> m
        is LengthValue.Exact -> m.height(v.px.toFloat().dp)
        is LengthValue.Relative -> if (v.unit == LengthUnit.PERCENT) {
            m.fillMaxHeight((v.value.toFloat() / 100f).coerceIn(0f, 1f))
        } else {
            m.height(resolveToDp(v, ctx))
        }
        is LengthValue.Intrinsic -> when (v.kind) {
            // Block-axis mirror of the width branch: height(IntrinsicSize.*)
            // gives the true min/max intrinsic content heights.
            LengthValue.IntrinsicKind.MIN_CONTENT -> m.height(IntrinsicSize.Min)
            LengthValue.IntrinsicKind.MAX_CONTENT -> m.height(IntrinsicSize.Max)
            LengthValue.IntrinsicKind.FIT_CONTENT -> {
                val bound = v.bound?.let { resolveToDp(it, ctx) }
                if (bound != null) m.heightIn(max = bound) else m.wrapContentHeight()
            }
        }
        is LengthValue.Calc, is LengthValue.Fraction -> m
    }

    /** Min/max width constraint.
     *
     *  KNOWN GAP (wave-3 skeptic, mirrored by the explicit TODO in iOS
     *  SizeApplier.swift): under an explicit `box-sizing: content-box`,
     *  css-sizing-3 §3 resolves min/max in the SAME box as width, so the
     *  frame constraint should be min/max + padding + border — e.g.
     *  `content-box; min-width: 100px; padding: 20px; border: 2px` means a
     *  144px frame floor on web while this emits 100. The explicit-size
     *  lane inflates (inflateForContentBox); this min/max-WITHOUT-size lane
     *  does not yet — deferred with the repro until a fixture exercises it,
     *  documented here so the fallthrough is not silent. */
    private fun applyWidthIn(m: Modifier, min: LengthValue?, max: LengthValue?, ctx: SpacingContext): Modifier {
        val mn = toDpOrNull(min, ctx)
        val mx = toDpOrNull(max, ctx)
        if (mn == null && mx == null) return m
        return m.widthIn(min = mn ?: 0.dp, max = mx ?: Dp.Infinity)
    }

    /** Min/max height constraint. */
    private fun applyHeightIn(m: Modifier, min: LengthValue?, max: LengthValue?, ctx: SpacingContext): Modifier {
        val mn = toDpOrNull(min, ctx)
        val mx = toDpOrNull(max, ctx)
        if (mn == null && mx == null) return m
        return m.heightIn(min = mn ?: 0.dp, max = mx ?: Dp.Infinity)
    }

    /**
     * Reduce a min/max value to Dp. None/Auto/Unknown → null (no constraint).
     * Percentage/Relative → resolved via SpacingResolve using a default ctx.
     */
    private fun toDpOrNull(v: LengthValue?, ctx: SpacingContext): Dp? = when (v) {
        null, LengthValue.Unknown, LengthValue.Auto, LengthValue.None -> null
        is LengthValue.Exact -> v.px.toFloat().dp
        is LengthValue.Relative, is LengthValue.Calc -> resolveToDp(v, ctx)
        is LengthValue.Intrinsic, is LengthValue.Fraction -> null  // invalid here
    }

    /** Width-only variant for flex items. Mirrors the pre-Phase-3 signature. */
    fun applyWidthOnly(modifier: Modifier, config: SizingConfig): Modifier {
        val ctx = SpacingContext()
        var r = modifier
        // Lane BX — flex items honour content-box the same way the main
        // lane does (identity unless the item explicitly declared it).
        r = applyWidth(r, inflateForContentBox(
            config.width ?: config.inlineSize,
            config.boxSizing, config.contentBoxInflateX), ctx)
        r = applyWidthIn(r, config.minWidth ?: config.minInlineSize,
            config.maxWidth ?: config.maxInlineSize, ctx)
        return r
    }

    /** Height-only variant for flex items. */
    fun applyHeightOnly(modifier: Modifier, config: SizingConfig): Modifier {
        val ctx = SpacingContext()
        var r = modifier
        // Lane BX — same explicit-content-box inflation as applyWidthOnly.
        r = applyHeight(r, inflateForContentBox(
            config.height ?: config.blockSize,
            config.boxSizing, config.contentBoxInflateY), ctx)
        r = applyHeightIn(r, config.minHeight ?: config.minBlockSize,
            config.maxHeight ?: config.maxBlockSize, ctx)
        return r
    }
}
