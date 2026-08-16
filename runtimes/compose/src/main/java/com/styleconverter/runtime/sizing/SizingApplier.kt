package com.styleconverter.runtime.sizing

// Phase 3 SizingApplier — collapses a SizingConfig into a Modifier chain. For
// px-only inputs (the common visual-test case) the emitted modifier chain is
// byte-identical to the old SizingApplier: Modifier.width/height/widthIn/
// heightIn/aspectRatio with the same Dp values.
//
// Relative units (em, rem, vw, %) are resolved through the spacing module's
// SpacingResolve helper so there's one source of truth. min-content /
// max-content map to Compose's intrinsic channel — through IntrinsicChannel's
// guarded twins of Modifier.width/height(IntrinsicSize.*), because a subtree
// holding any SubcomposeLayout-based renderer refuses the intrinsic read by
// THROWING (see IntrinsicChannel's banner) and an unguarded throw kills the
// whole capture composition, not just this box.

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
import com.styleconverter.runtime.layout.IntrinsicChannel
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.resolveToDp

object SizingApplier {

    // ── Guarded-intrinsic refusal wording (see IntrinsicChannel) ──────────
    //
    // Logged ONCE per (tag, platform message) when a min-/max-content sized
    // box's subtree refuses the intrinsic channel — any SubcomposeLayout-
    // based renderer below it (multicol's BoxWithConstraints, grid, scroll,
    // sticky, container-query, line-clamp) is enough. The box then keeps
    // the measure its incoming constraints give it: mis-sized at worst,
    // never a dead capture. Same fallback contract as TableApplier's
    // §17.5.2/§17.5.3 reads, which hit this hazard first.

    /** Log tag — points refusals at this applier, not the shared guard. */
    private const val TAG = "SizingApplier"

    /**
     * One refusal sentence per declaration. [IntrinsicChannel.probe] logs
     * the FIRST refusal per (tag, platform message), so the sentence names
     * the exact declaration skipped — the winning log line must say which
     * keyword lane fell back, per the no-silent-fallthrough rule.
     */
    private fun intrinsicRefusal(declaration: String) =
        "css-sizing-3 §4 `$declaration` skipped — this box's subtree has " +
            "no intrinsic channel; the box keeps the measure its incoming " +
            "constraints give it (auto-like, mis-sized at worst, alive)."

    /** Apply [config] to [modifier]. Returns [modifier] unchanged if empty.
     *
     *  Wave-18 lane 2 (pin P1) — [ctx] is threaded from StyleApplier and
     *  carries the element's resolved font size plus the measured ch
     *  advance (ChUnitMetrics), so `width: 63.1ch` resolves against real
     *  font metrics. The default keeps every legacy call site (flex paths,
     *  tests) source-compatible and byte-identical for non-font-relative
     *  values. */
    fun applySizing(
        modifier: Modifier,
        config: SizingConfig,
        ctx: SpacingContext = SpacingContext(),
    ): Modifier {
        if (!config.hasSizing) return modifier
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
        // css-values-5 calc-size() (wave 42 lane W3): a calc-size MIN slot
        // that coexists with a definite Exact size takes over the WHOLE
        // axis — CSS resolves used = max(preferred, floor) in one clamp
        // (css-sizing-3 §5.2) and splitting that across two modifiers
        // would let the outer exact node coerce the floored measure back
        // down (the reported box and the painted ink would disagree). The
        // min modifier receives the specified px and owns the clamp, so
        // the normal width/height emission is suppressed for that axis.
        val minWCalcOwnsAxis = config.minWidthCalc != null && rawW is LengthValue.Exact
        val minHCalcOwnsAxis = config.minHeightCalc != null && rawH is LengthValue.Exact
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
        if (!minWCalcOwnsAxis) r = applyWidth(r, inflateForContentBox(
            clampLength(rawW, minWv, maxWv, ctx),
            // RC-B6b: the WPT flag rides the config into the width branch
            // so ch/em widths can take the overflow-aware exact path.
            // Wave 44 (lane U6): the SAME threaded ctx rides into the
            // inflation so a content-box Relative resolves against the
            // exact font/line-height basis the apply branch would use.
            config.boxSizing, config.contentBoxInflateX, ctx), ctx, config.wptCaptureMode)
        if (!minHCalcOwnsAxis) r = applyHeight(r, inflateForContentBox(
            clampLength(rawH, minHv, maxHv, ctx),
            // Wave 44 (lane U6): ctx threaded — see the width twin above.
            config.boxSizing, config.contentBoxInflateY, ctx), ctx)
        // calc-size PREFERRED lane — sits exactly where Modifier.width/
        // height would have gone (the slot pair is exclusive: the extractor
        // never fills width AND widthCalc together), so the background
        // chained inside paints at the resolved target.
        config.widthCalc?.let { r = r.calcSizePreferred(rowAxis = true, spec = it) }
        config.heightCalc?.let { r = r.calcSizePreferred(rowAxis = false, spec = it) }
        // Min/max constraints — kept for the case where no explicit
        // width/height was set (then clamp short-circuits to null and
        // widthIn/heightIn carry the intent).
        r = applyWidthIn(r, minWv, maxWv, ctx)
        r = applyHeightIn(r, minHv, maxHv, ctx)
        // calc-size MIN (floor) lane — css-flexbox-1 §4.5 / css-sizing-3
        // §5.2. The specified px rides in when the axis had a definite
        // size (the suppressed emission above); a Relative/intrinsic
        // preferred size keeps its own modifier and the floor lane runs
        // with a null specified suggestion (documented approximation: the
        // §4.5 specified-size suggestion is only read from Exact px — the
        // corpus family is all px or auto).
        config.minWidthCalc?.let {
            r = r.calcSizeMin(rowAxis = true, spec = it,
                specifiedPx = (rawW as? LengthValue.Exact)?.px?.let { px ->
                    kotlin.math.round(px).toInt() })
        }
        config.minHeightCalc?.let {
            r = r.calcSizeMin(rowAxis = false, spec = it,
                specifiedPx = (rawH as? LengthValue.Exact)?.px?.let { px ->
                    kotlin.math.round(px).toInt() })
        }
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
            // A calc-size preferred slot PINS its axis just like a definite
            // length (its modifier resolves a concrete px at measure time),
            // so it joins the both-axes-pinned skip — without this,
            // calc-size-aspect-ratio-001 would chain aspectRatio on top of
            // an already fully-sized 50×100 box (harmless today only
            // because tight constraints win; the gate keeps it structural).
            val wPinned = isDefiniteAxis(rawW) || config.widthCalc != null
            val hPinned = isDefiniteAxis(rawH) || config.heightCalc != null
            if (ar.ratio > 0.0 && !(wPinned && hPinned)) {
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
     * fixture's passing V1_border sentinel) untouched.
     *
     * Wave 44 (lane U6) — DEFINITE sizes now inflate in BOTH shapes:
     *   * Exact px — the original wave-11 arithmetic, unchanged;
     *   * non-% Relative (lh/em/ch/rem/vw…) — css-sizing-3 §3 makes no
     *     px-vs-font-relative distinction: `height: 2lh` under content-box
     *     is exactly as definite as `height: 32.5px` once resolved, so it
     *     resolves HERE through the same LhUnitLineHeight-aware
     *     [resolveToDp] the apply branches use ([ctx] is the identical
     *     threaded context — the two resolves can never disagree) and
     *     gains the band. Measured defect this closes: discard-multicol-
     *     001/002/004's `height: 2lh` (monospace-13 → 32.5px) rendered
     *     32.5 as the BORDER box on Android — the ref's box is 32.5
     *     content + 1px borders = 34.5 → the second text row clipped
     *     mid-glyph (android rows 104..136 = 33px vs ref 88..122 = 35px,
     *     wave43-final css-overflow captures).
     *   Gated on a NONZERO band: with nothing to add, converting the
     *   shape would only flip the apply branch (width()/exactWidth) for
     *   no geometric reason, so zero-band Relatives pass through and the
     *   whole borderless-WPT + dark-stage corpus keeps byte-identical
     *   modifier chains (grep at wave 44: the ONLY committed fixture
     *   declaring content-box — fixtures/properties/sizing/box-sizing.json
     *   — is all-px, so no committed baseline can move).
     *   BLAST RADIUS, measured not assumed (wave-44 skeptic S5, D2): 40
     *   wave43-final WPT tests carry a component with a non-% Relative
     *   width/height AND a nonzero band (the ch-sized css-text/hyphens and
     *   css-overflow/line-clamp families, plus the discard-multicol trio).
     *   The band is added on top of a PLATFORM-resolved basis, so a capture
     *   does NOT necessarily move toward its ref: where Android's resolved
     *   basis already overshoots Chromium's, the band overshoots further.
     *   Simulated post-fix captures (the band spliced into the wave43-final
     *   Android PNG, scored against the same run's web capture with the
     *   comparator's ssim.js `fast` mssim) — css-text/hyphens:
     *   hyphens-auto-control 0.9632 → 0.9618 and hyphens-vertical-001
     *   0.9558 → 0.9538 both move AWAY (still passing, both nearer the 0.95
     *   line), hyphens-manual-010 0.9552 → 0.9554 moves toward. The arm is
     *   still right by §3 — the residual is font metrics (see the 27ch case
     *   in ContentBoxRelativeInflationTest: 27 × Android's 8.0px advance + 2
     *   = 218 vs the ref's 27 × 7.8 + 2 = 213) — but "content-box arithmetic
     *   fixed" is the claim, NOT "every affected cell improves".
     * Still passing through, each an honest documented divergence:
     *   * PERCENT — routes to fillMaxWidth/Height (fractional; a px band
     *     cannot be added to a fraction — needs a layout-time add);
     *   * Calc — the apply branches skip unresolved Calc entirely, and
     *     resolving it here would CREATE a size modifier where none
     *     existed (out of this lane's scope);
     *   * Auto/Intrinsic/None — no definite size to reinterpret (§3).
     * Min/max clamping of Relative sizes stays absent (clampLength is
     * Exact-only) — pre-existing gap, unchanged by this lane.
     */
    internal fun inflateForContentBox(
        v: LengthValue?,
        boxSizing: BoxSizingKeyword?,
        inflatePx: Float,
        // Resolution context for the Relative arm — defaulted so the
        // wave-11 three-arg call shape (BoxSizingContentBoxTest and the
        // flex-item lanes before ctx threading) stays source-compatible.
        ctx: SpacingContext = SpacingContext(),
    ): LengthValue? {
        // Not explicitly content-box → border-box status quo, untouched.
        if (boxSizing != BoxSizingKeyword.CONTENT_BOX) return v
        return when {
            // Exact px: frame = declared content size + padding + border.
            v is LengthValue.Exact -> LengthValue.Exact(v.px + inflatePx)
            // Non-% Relative with a real band: resolve to definite px via
            // the shared SpacingResolve (lh takes ctx.lineHeightPx — the
            // wave-43 LhUnitLineHeight channel; ch takes the measured
            // advance; em the font size), then add the band. Float→Double
            // via the resolved Dp's value, same as the apply-time path.
            v is LengthValue.Relative && v.unit != LengthUnit.PERCENT &&
                inflatePx != 0f ->
                LengthValue.Exact(
                    (resolveToDp(v, ctx).value + inflatePx).toDouble())
            // Everything else (%, calc, auto, intrinsic, zero-band
            // relative): unchanged — the documented pass-throughs above.
            else -> v
        }
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

    /** Width axis.
     *
     *  [wptCaptureMode] (RC-B6b, default false) only changes the NON-%
     *  Relative branch — see the routing comment there. */
    private fun applyWidth(
        m: Modifier,
        v: LengthValue?,
        ctx: SpacingContext,
        wptCaptureMode: Boolean = false,
    ): Modifier = when (v) {
        null, LengthValue.Unknown, LengthValue.Auto, LengthValue.None -> m  // no override
        // Definite px → the wave-12 overflow-aware exact width (see
        // exactWidth above: Modifier.width semantics when fitting, declared
        // size + start-aligned visible overflow when larger than the parent).
        is LengthValue.Exact -> m.exactWidth(v.px.toFloat().dp)
        is LengthValue.Relative -> if (v.unit == LengthUnit.PERCENT) {
            // % on width resolves against parent width — Compose has a direct
            // modifier for that. We clamp to [0,1] since fillMaxWidth rejects
            // values outside that range at runtime.
            m.fillMaxWidth((v.value.toFloat() / 100f).coerceIn(0f, 1f))
        } else if (wptCaptureMode) {
            // RC-B6b (WPT capture only): a non-% relative width resolves to
            // a FIXED px value here (ch via the measured ChUnitMetrics
            // advance, em/rem via the font context — the same resolveToDp
            // the else-branch uses), so it is exactly as definite as the
            // Exact branch above and must get the same css-overflow-3 §3
            // semantics: measure/place at the DECLARED size and let the ink
            // overflow the parent (start-anchored) instead of letting
            // Modifier.width COERCE it into the incoming constraints.
            // block-ellipsis-001: `width: 63.1ch` (monospace ≈ 605px)
            // inside the 358px content envelope wrapped at 358px on
            // Android while the browser-ref wraps at 605px — every wrap
            // point (and the 2-line clamp geometry) shifted. Gated on the
            // WPT flag so the dark-stage corpus keeps the historical
            // coercing Modifier.width byte-identically (the wave-1 "+2px
            // placeholder" lesson: never move non-WPT geometry from a
            // sizing lane change).
            m.exactWidth(resolveToDp(v, ctx))
        } else {
            // Non-% relative (em/vw/…) goes through the spacing resolver.
            m.width(resolveToDp(v, ctx))
        }
        // Both keyword lanes read the child's intrinsics through
        // IntrinsicChannel's guarded twins — measure-identical to the former
        // unguarded width(IntrinsicSize.*) wherever the channel answers
        // (IntrinsicChannel's exactness contract), but a SubcomposeLayout
        // anywhere in the subtree now logs one refusal and keeps the
        // incoming measure instead of throwing away the whole capture.
        is LengthValue.Intrinsic -> when (v.kind) {
            // width: min-content → the box takes its MIN intrinsic width
            // (css-sizing-3 §4: the narrowest width that avoids overflow —
            // for text, the widest unbreakable run). The pre-wave
            // wrapContentWidth() let content pick its PREFERRED width, so
            // `width: min-content` rendered max-content-wide — web showed a
            // letter-wrapped sliver while Android filled the line
            // (PW_Sizing_Spacing_02, A-w 0.698 / 28.5% px).
            LengthValue.IntrinsicKind.MIN_CONTENT -> with(IntrinsicChannel) {
                m.widthAtMinIntrinsic(TAG, intrinsicRefusal("width: min-content"))
            }
            // width: max-content → MAX intrinsic width (no-wrap preferred
            // size, css-sizing-3 §4).
            LengthValue.IntrinsicKind.MAX_CONTENT -> with(IntrinsicChannel) {
                m.widthAtMaxIntrinsic(TAG, intrinsicRefusal("width: max-content"))
            }
            // fit-content(<bound>): Compose has no direct analog; use the
            // bound as a max-width constraint which approximates "content,
            // but capped at bound".
            LengthValue.IntrinsicKind.FIT_CONTENT -> {
                val bound = v.bound?.let { resolveToDp(it, ctx) }
                if (bound != null) m.widthIn(max = bound) else {
                    // Bare fit-content. Wave 42 (lane W3): in WPT capture
                    // the wrapContent lane gains the css-sizing-3 §5.1
                    // min-content floor INSIDE it — a squeezing container
                    // (calc-size-min-max-sizes-001/004's width:0 outer)
                    // collapsed the box to nothing where the ref paints
                    // its 100px min-content. Pass-through whenever the
                    // container offers at least min-content, and absent
                    // entirely (no extra node) off the WPT path — see
                    // FitContentSqueeze's banner for the measured defect.
                    val wrapped = m.wrapContentWidth()
                    if (wptCaptureMode) with(FitContentSqueeze) {
                        wrapped.fitContentWidthSqueezeGuard()
                    } else wrapped
                }
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
        // Block-axis mirror of the width branch — same guarded channel,
        // same refusal contract, height twins.
        is LengthValue.Intrinsic -> when (v.kind) {
            // height: min-content → MIN intrinsic content height under the
            // incoming width (css-sizing-3 §4).
            LengthValue.IntrinsicKind.MIN_CONTENT -> with(IntrinsicChannel) {
                m.heightAtMinIntrinsic(TAG, intrinsicRefusal("height: min-content"))
            }
            // height: max-content → MAX (preferred) intrinsic content height.
            LengthValue.IntrinsicKind.MAX_CONTENT -> with(IntrinsicChannel) {
                m.heightAtMaxIntrinsic(TAG, intrinsicRefusal("height: max-content"))
            }
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
        // Wave 42 (lane W3): calc-size lanes mirror applySizing — a min
        // calc over a definite Exact width owns the whole axis (one clamp,
        // see the main lane's comment), otherwise the normal emission runs
        // and the calc modifiers chain in their canonical positions.
        val rawW = config.width ?: config.inlineSize
        val minOwns = config.minWidthCalc != null && rawW is LengthValue.Exact
        // Lane BX — flex items honour content-box the same way the main
        // lane does (identity unless the item explicitly declared it).
        // RC-B6b: the WPT relative-overflow routing also rides the config
        // here so a flex item's ch/em width resolves identically.
        if (!minOwns) r = applyWidth(r, inflateForContentBox(
            rawW,
            // Wave 44 (lane U6): the flex-item lane threads its own local
            // ctx (default context — same one its apply branch resolves
            // with) so a content-box Relative width inflates identically.
            config.boxSizing, config.contentBoxInflateX, ctx), ctx, config.wptCaptureMode)
        config.widthCalc?.let { r = r.calcSizePreferred(rowAxis = true, spec = it) }
        r = applyWidthIn(r, config.minWidth ?: config.minInlineSize,
            config.maxWidth ?: config.maxInlineSize, ctx)
        config.minWidthCalc?.let {
            r = r.calcSizeMin(rowAxis = true, spec = it,
                specifiedPx = (rawW as? LengthValue.Exact)?.px?.let { px ->
                    kotlin.math.round(px).toInt() })
        }
        return r
    }

    /** Height-only variant for flex items. */
    fun applyHeightOnly(modifier: Modifier, config: SizingConfig): Modifier {
        val ctx = SpacingContext()
        var r = modifier
        // Wave 42 (lane W3): the height twin of applyWidthOnly's calc lanes.
        val rawH = config.height ?: config.blockSize
        val minOwns = config.minHeightCalc != null && rawH is LengthValue.Exact
        // Lane BX — same explicit-content-box inflation as applyWidthOnly.
        if (!minOwns) r = applyHeight(r, inflateForContentBox(
            rawH,
            // Wave 44 (lane U6): ctx threaded — the height twin of
            // applyWidthOnly's inflation call above.
            config.boxSizing, config.contentBoxInflateY, ctx), ctx)
        config.heightCalc?.let { r = r.calcSizePreferred(rowAxis = false, spec = it) }
        r = applyHeightIn(r, config.minHeight ?: config.minBlockSize,
            config.maxHeight ?: config.maxBlockSize, ctx)
        config.minHeightCalc?.let {
            r = r.calcSizeMin(rowAxis = false, spec = it,
                specifiedPx = (rawH as? LengthValue.Exact)?.px?.let { px ->
                    kotlin.math.round(px).toInt() })
        }
        return r
    }
}
