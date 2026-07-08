package com.styleconverter.test.style.sizing

// Phase 3 SizingApplier — collapses a SizingConfig into a Modifier chain. For
// px-only inputs (the common visual-test case) the emitted modifier chain is
// byte-identical to the old SizingApplier: Modifier.width/height/widthIn/
// heightIn/aspectRatio with the same Dp values.
//
// Relative units (em, rem, vw, %) are resolved through the spacing module's
// SpacingResolve helper so there's one source of truth. Compose can't express
// min-content/max-content directly on a size modifier — we approximate with
// wrapContentWidth/Height() which at least reads as "shrink to content".

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
import com.styleconverter.test.style.core.types.LengthUnit
import com.styleconverter.test.style.core.types.LengthValue
import com.styleconverter.test.style.spacing.SpacingContext
import com.styleconverter.test.style.spacing.resolveToDp

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
        r = applyWidth(r, clampLength(rawW, minWv, maxWv, ctx), ctx)
        r = applyHeight(r, clampLength(rawH, minHv, maxHv, ctx), ctx)
        // Min/max constraints — kept for the case where no explicit
        // width/height was set (then clamp short-circuits to null and
        // widthIn/heightIn carry the intent).
        r = applyWidthIn(r, minWv, maxWv, ctx)
        r = applyHeightIn(r, minHv, maxHv, ctx)
        // aspect-ratio. ratio=0.0 with isAuto means auto-only — skip modifier
        // and let Compose auto-size.
        config.aspectRatio?.let { ar ->
            if (ar.ratio > 0.0) r = r.aspectRatio(ar.ratio.toFloat())
        }
        return r
    }

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
            // min-content/max-content: Compose approximation is wrapContentWidth,
            // which shrinks to intrinsic content width.
            LengthValue.IntrinsicKind.MIN_CONTENT,
            LengthValue.IntrinsicKind.MAX_CONTENT -> m.wrapContentWidth()
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
            LengthValue.IntrinsicKind.MIN_CONTENT,
            LengthValue.IntrinsicKind.MAX_CONTENT -> m.wrapContentHeight()
            LengthValue.IntrinsicKind.FIT_CONTENT -> {
                val bound = v.bound?.let { resolveToDp(it, ctx) }
                if (bound != null) m.heightIn(max = bound) else m.wrapContentHeight()
            }
        }
        is LengthValue.Calc, is LengthValue.Fraction -> m
    }

    /** Min/max width constraint. */
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
        r = applyWidth(r, config.width ?: config.inlineSize, ctx)
        r = applyWidthIn(r, config.minWidth ?: config.minInlineSize,
            config.maxWidth ?: config.maxInlineSize, ctx)
        return r
    }

    /** Height-only variant for flex items. */
    fun applyHeightOnly(modifier: Modifier, config: SizingConfig): Modifier {
        val ctx = SpacingContext()
        var r = modifier
        r = applyHeight(r, config.height ?: config.blockSize, ctx)
        r = applyHeightIn(r, config.minHeight ?: config.minBlockSize,
            config.maxHeight ?: config.maxBlockSize, ctx)
        return r
    }
}
