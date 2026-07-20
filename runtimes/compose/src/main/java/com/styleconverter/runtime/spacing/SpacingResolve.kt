package com.styleconverter.runtime.spacing

// Shared helper that collapses a LengthValue into Dp for the padding/margin
// appliers. The logic lives here so both Appliers share one source of truth
// for em/rem/vw/vh/% resolution. Anything context-dependent (percentages
// measured against parent width, em against a font size that may come from a
// Composable) takes the required context as a parameter — this file has no
// Compose imports so it's cheap to unit-test.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue

/**
 * Context needed to resolve a LengthValue to absolute px. Passed by the
 * Applier which knows the Compose-side environment (font size, viewport,
 * parent width).
 *
 * Percentage resolution: CSS spec says padding-% and margin-% on ALL four
 * sides resolve against the parent's CONTENT WIDTH (yes, even top/bottom).
 * The Applier is responsible for providing [parentWidthPx] from the nearest
 * ancestor box (we use the incoming BoxWithConstraints max width as a
 * pragmatic proxy).
 */
data class SpacingContext(
    // Current font size in px for em resolution (1em = fontSizePx).
    val fontSizePx: Float = 16f,
    // Root font size in px for rem resolution. Defaults to the CSS default.
    val rootFontSizePx: Float = 16f,
    // Viewport width/height in px for vw/vh/vmin/vmax.
    val viewportWidthPx: Float = 390f,
    val viewportHeightPx: Float = 844f,
    // Parent content-box width in px for % resolution. Optional — when null
    // we fall back to viewport width so rendering at least looks plausible.
    val parentWidthPx: Float? = null,
    // Wave-18 lane 2 (pin P1) — the measured advance width of the glyph '0'
    // at the element's resolved font family + size, in px. This is the CSS
    // `ch` unit basis (css-values-4 §6.1.3). Null = metrics unavailable at
    // this call site → resolution falls back to 0.5em, the fallback the
    // same spec section mandates ("assumed to be 0.5em wide"). Populated by
    // StyleApplier via ChUnitMetrics when a value in the config uses ch.
    val chAdvancePx: Float? = null,
    // Wave-18 lane 2 (pin P11) — percent-basis tri-state switch. When TRUE
    // a containing-block percentage with NO definite [parentWidthPx]
    // resolves to 0 (css-position-3 §5.1: inside an abspos auto/fit-content
    // inline-size the basis is indefinite; css-sizing-3 §5.2.1 resolves
    // cyclic percentages against zero). When FALSE (the default) the legacy
    // viewport-width fallback is preserved so the frozen dark-stage corpus
    // renders byte-identically. Set by the WPT-capture percent paths in
    // Padding/MarginApplier and by SizingExtractor's content-box inflation.
    val percentIndefiniteAsZero: Boolean = false,
)

/**
 * Pin P10/P11/P12 — the containing-block percentage base in px:
 *   1. a definite [SpacingContext.parentWidthPx] always wins (P10);
 *   2. indefinite + [SpacingContext.percentIndefiniteAsZero] → 0 (P11);
 *   3. indefinite legacy → viewport width (P12, frozen dark-stage rule).
 * Shared by resolveRelative and the calc() evaluator so a bare `50%` and a
 * `calc(50% + 0px)` can never disagree about their base.
 */
internal fun percentBasePx(ctx: SpacingContext): Float =
    ctx.parentWidthPx ?: if (ctx.percentIndefiniteAsZero) 0f else ctx.viewportWidthPx

/**
 * True when [values] contains a containing-block percentage (a Relative
 * PERCENT length). The appliers use this as the identity gate for the
 * WPT percent path: px-only configs keep the historical modifier chain
 * byte-for-byte, so the whole committed baseline corpus is untouched.
 * (calc() expressions containing % deliberately stay on the legacy path —
 * documented, not silent: they resolve through evalCalc, whose % arm uses
 * the SAME percentBasePx tri-state as a bare % — legacy viewport base in
 * default contexts, ZERO base in percentIndefiniteAsZero intrinsic
 * contexts (SizingExtractor P13) — until a fixture exercises the
 * combination. This calc-% asymmetry is SHARED with iOS by design — the
 * iOS SpacingCalcEvaluator resolves calc-% against its legacy
 * containingBlockWidth base in the applier lane, and mirrors the zero-base
 * intrinsic rule via SpacingContext.calcPercentBasisPx (skeptic
 * follow-up); wave-18 skeptic B3 pinned the pair. Change both or neither.)
 */
internal fun usesContainingBlockPercent(vararg values: LengthValue?): Boolean =
    values.any { it is LengthValue.Relative && it.unit == LengthUnit.PERCENT }

/**
 * True when [values] contains a ch-unit length (pin P1). StyleApplier uses
 * this as the gate for measuring the '0' advance — the measurement touches
 * the platform text engine, so it only runs when a value will consume it.
 */
internal fun usesChUnit(vararg values: LengthValue?): Boolean =
    values.any { it is LengthValue.Relative && it.unit == LengthUnit.CH }

/**
 * Reduce [value] to a concrete Dp. Returns 0.dp when the length is Unknown
 * (treat missing as zero — that matches the legacy SpacingApplier behaviour
 * for px-only inputs and the CSS initial value of padding/margin).
 *
 * Calc() is not yet evaluated here (it needs the full CalcExpressionEvaluator
 * pipeline which has its own IR types); we return 0.dp as a safe default and
 * log via a debug hook left for future Phase 3 work. Callers that care about
 * calc can inspect the LengthValue directly before calling resolve().
 */
fun resolveToDp(value: LengthValue?, ctx: SpacingContext): Dp {
    // Missing side → zero. This matches how the old SpacingApplier defaulted
    // unspecified sides, so px-only regressions stay byte-identical.
    if (value == null) return 0.dp
    return when (value) {
        is LengthValue.Exact -> value.px.toFloat().dp
        is LengthValue.Relative -> resolveRelative(value, ctx).dp
        // Resolve calc() with the same SpacingContext so percent operands
        // measure against the real parent width (ctx.parentWidthPx or the
        // viewport fallback) and em/rem operands use the live font sizes.
        // Previously this was hard-coded to 0.dp, which silently dropped every
        // calc() padding/margin in the audit fixture. See evalCalc() below.
        is LengthValue.Calc -> evalCalc(value.expression, ctx).dp
        is LengthValue.Auto -> 0.dp  // Auto on padding isn't meaningful; margin handled separately.
        is LengthValue.Intrinsic -> 0.dp  // min-content/max-content invalid here.
        is LengthValue.Fraction -> 0.dp  // fr invalid outside grid tracks.
        is LengthValue.None -> 0.dp  // `none` on padding/margin isn't meaningful.
        is LengthValue.Unknown -> 0.dp
    }
}

/** Convert a Relative LengthValue into a Float px count. */
private fun resolveRelative(r: LengthValue.Relative, ctx: SpacingContext): Float {
    val v = r.value.toFloat()
    return when (r.unit) {
        // Containing-block percentage — base picked by the P10/P11/P12
        // tri-state (definite parent → parent; indefinite → 0 in WPT
        // capture / viewport legacy). See percentBasePx above.
        LengthUnit.PERCENT -> percentBasePx(ctx) * v / 100f
        LengthUnit.EM -> v * ctx.fontSizePx
        LengthUnit.REM -> v * ctx.rootFontSizePx
        // Pin P1 — `ch`: the advance width of '0' in the element's font
        // (css-values-4 §6.1.3). Measured metrics when the plumbing
        // provided them (ChUnitMetrics via StyleApplier), else the spec's
        // own 0.5em fallback. This branch previously fell into the
        // else→0 arm, collapsing `width: 63.1ch` boxes to nothing
        // (css-overflow block-ellipsis-001 rendered a blank canvas).
        LengthUnit.CH -> v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx)
        // Pin P2 — `ex`: x-height. §6.1.3 mandates a 0.5em assumption
        // when the metric can't be determined; we don't measure x-height
        // yet, so the spec constant is the honest resolution.
        LengthUnit.EX -> v * 0.5f * ctx.fontSizePx
        // Pin P3 — `ic`: CJK water ideograph advance; §6.1.3 fallback 1em.
        LengthUnit.IC -> v * ctx.fontSizePx
        // Pin P4 — `cap`: cap-height. The spec fallback is the font's
        // ascent, which needs metrics we don't thread here; 1em is the
        // documented approximation, surfaced via the tracker so the
        // shortcut is visible in coverage reports, never silent.
        LengthUnit.CAP -> {
            PropertyTracker.markUnhandled("SpacingResolve:CAP≈1em")
            v * ctx.fontSizePx
        }
        // Pin P5 — `lh`: the element's line-height. We don't thread the
        // used line-height; `normal` computes to ≈1.2 × font-size in
        // every browser default stylesheet, so 1.2em is the approximation.
        LengthUnit.LH -> v * 1.2f * ctx.fontSizePx
        // Pin P6 — `rlh`: root line-height, same 1.2 ratio on the root
        // font size (the harness never styles the root element).
        LengthUnit.RLH -> v * 1.2f * ctx.rootFontSizePx
        // Viewport-relative units. Compose fronts a small/large/dynamic
        // distinction that we don't meaningfully support yet; treat the three
        // groups as identical to the classic viewport. Pins P7/P8: the
        // logical vi/vb axes map to vw/vh under the horizontal-tb writing
        // mode (the only mode the renderer supports) — previously vi/vb
        // fell into the silent else→0 arm.
        // Container-query lengths ride the same arms: css-contain-3 §9
        // says cq* fall back to the SMALL viewport size when no eligible
        // container exists — this runtime has no container context, so
        // cqw/cqi = vw, cqh/cqb = vh, cqmin/cqmax = vmin/vmax. This is
        // also what the iOS resolver has always done; the wave-18 skeptic
        // cross-native probe caught Compose collapsing cq* to 0 instead.
        LengthUnit.VW, LengthUnit.SVW, LengthUnit.LVW, LengthUnit.DVW,
        LengthUnit.VI, LengthUnit.SVI, LengthUnit.LVI, LengthUnit.DVI,
        LengthUnit.CQW, LengthUnit.CQI ->
            ctx.viewportWidthPx * v / 100f
        LengthUnit.VH, LengthUnit.SVH, LengthUnit.LVH, LengthUnit.DVH,
        LengthUnit.VB, LengthUnit.SVB, LengthUnit.LVB, LengthUnit.DVB,
        LengthUnit.CQH, LengthUnit.CQB ->
            ctx.viewportHeightPx * v / 100f
        LengthUnit.VMIN, LengthUnit.SVMIN, LengthUnit.LVMIN, LengthUnit.DVMIN,
        LengthUnit.CQMIN ->
            minOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        LengthUnit.VMAX, LengthUnit.SVMAX, LengthUnit.LVMAX, LengthUnit.DVMAX,
        LengthUnit.CQMAX ->
            maxOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        // Pin P9 (amended) — remaining units (fr, wire-drift UNKNOWN, and
        // Relative-tagged absolute units the converter normally pre-folds
        // to Exact) have no honest base in this context. Take the
        // converter's pxFallback when present, else 0 — and RECORD the
        // fallthrough via the tracker (markUnhandled is Log-free and
        // JVM-test-safe) so the collapse is auditable, never silent.
        else -> {
            PropertyTracker.markUnhandled("SpacingResolve:${r.unit}")
            r.pxFallback?.toFloat() ?: 0f
        }
    }
}

// ── calc() evaluator ────────────────────────────────────────────────────────
//
// Minimal recursive-descent evaluator for CSS calc() expressions that appear
// in padding/margin/gap values. Supports:
//   • lengths in px, pt, em, rem, %, vw, vh, vmin, vmax
//   • + - * / and parentheses
//   • nested calc(...) (the outer call strips exactly one wrapping layer)
//
// Not supported (returns 0f as a safe fallback):
//   • var() — the parser should have resolved these before we see the
//     expression; if it didn't, custom-property resolution is out of scope
//     here.
//   • min()/max()/clamp() function wrappers — the CSS parser emits these as
//     separate IR shapes, not inside Calc.expression strings.
//   • mixing two lengths with * (spec-invalid; returns 0).
//
// The evaluator intentionally lives next to resolveToDp() so both share the
// same SpacingContext and unit-resolution table (any new unit added to
// resolveRelative() Just Works here too).

/** Public entry point — accepts a raw expression string, returns px. */
internal fun evalCalc(raw: String, ctx: SpacingContext): Float {
    // Strip a leading `calc(` ... `)` wrapper if present; nested calls land
    // inside already-stripped expressions during recursion.
    val trimmed = raw.trim()
    val inner = if (trimmed.startsWith("calc(", ignoreCase = true) && trimmed.endsWith(")")) {
        trimmed.substring(5, trimmed.length - 1)
    } else {
        trimmed
    }
    return try {
        CalcParser(inner, ctx).parseExpression()
    } catch (_: Throwable) {
        // Any parse error → 0f. Safer than throwing inside a renderer path
        // and the audit fixture can catch the silent-zero case via SSIM.
        0f
    }
}

private class CalcParser(private val src: String, private val ctx: SpacingContext) {
    private var pos = 0

    // Top-level: handle + and - left-to-right.
    fun parseExpression(): Float {
        var left = parseTerm()
        while (true) {
            skipWs()
            if (pos >= src.length) break
            val op = src[pos]
            if (op != '+' && op != '-') break
            // CSS spec: + and - around calc operands MUST have whitespace on
            // both sides. We enforce that by requiring at least one space
            // either before or after — otherwise "20px-5px" is a single
            // length token, not a subtraction.
            if (!hasWsAround(pos)) break
            pos++
            val right = parseTerm()
            left = if (op == '+') left + right else left - right
        }
        return left
    }

    // Term: handle * and / (higher precedence than + -).
    private fun parseTerm(): Float {
        var left = parseFactor()
        while (true) {
            skipWs()
            if (pos >= src.length) break
            val op = src[pos]
            if (op != '*' && op != '/') break
            pos++
            val right = parseFactor()
            left = if (op == '*') left * right else if (right != 0f) left / right else 0f
        }
        return left
    }

    // Factor: parens, nested calc(), or a length/number token.
    private fun parseFactor(): Float {
        skipWs()
        if (pos < src.length && src[pos] == '(') {
            pos++ // consume '('
            val v = parseExpression()
            skipWs()
            if (pos < src.length && src[pos] == ')') pos++ // consume ')'
            return v
        }
        // Nested calc(...) — strip and recurse.
        if (src.regionMatches(pos, "calc(", 0, 5, ignoreCase = true)) {
            pos += 5
            val v = parseExpression()
            skipWs()
            if (pos < src.length && src[pos] == ')') pos++
            return v
        }
        return parseLengthOrNumber()
    }

    // Length or unitless number. Returns px (unitless numbers are treated as
    // bare multipliers; they return their numeric value and the caller
    // multiplies through if the surrounding op is *).
    private fun parseLengthOrNumber(): Float {
        skipWs()
        val start = pos
        // Number: digits, one optional dot, optional leading sign.
        if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        if (pos == start || (pos == start + 1 && !src[start].isDigit())) {
            // No digits consumed — malformed token.
            throw IllegalStateException("expected number at pos $start in `$src`")
        }
        val num = src.substring(start, pos).toFloat()
        // Unit: letters or %.
        val unitStart = pos
        while (pos < src.length && (src[pos].isLetter() || src[pos] == '%')) pos++
        val unitStr = src.substring(unitStart, pos).lowercase()
        return convertToPx(num, unitStr)
    }

    // Map a unit string to a px value using the surrounding SpacingContext.
    // Mirrors resolveRelative() above — keep these two in sync if new units
    // land.
    private fun convertToPx(v: Float, unit: String): Float = when (unit) {
        "" -> v                                    // bare number (multiplier)
        "px" -> v
        "pt" -> v * 1.3333334f                     // 1pt = 4/3 px (CSS spec)
        "em" -> v * ctx.fontSizePx
        "rem" -> v * ctx.rootFontSizePx
        // Same P10/P11/P12 tri-state base as resolveRelative — a % inside
        // calc() must agree with a bare % about its containing block.
        "%" -> percentBasePx(ctx) * v / 100f
        // Pins P1–P6 mirrored from resolveRelative (see the table there):
        // ch = advance of '0' (0.5em fallback), ex = 0.5em, ic/cap = 1em,
        // lh = 1.2em, rlh = 1.2rem — kept in lockstep per the file rule
        // "any new unit added to resolveRelative Just Works here too".
        "ch" -> v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx)
        "ex" -> v * 0.5f * ctx.fontSizePx
        "ic", "cap" -> v * ctx.fontSizePx
        "lh" -> v * 1.2f * ctx.fontSizePx
        "rlh" -> v * 1.2f * ctx.rootFontSizePx
        // Logical viewport axes fold onto vw/vh (horizontal-tb, pins P7/P8);
        // container-query lengths ride the same arms (css-contain-3 §9
        // no-container fallback — kept in lockstep with resolveRelative).
        "vw", "svw", "lvw", "dvw", "vi", "svi", "lvi", "dvi", "cqw", "cqi" ->
            ctx.viewportWidthPx * v / 100f
        "vh", "svh", "lvh", "dvh", "vb", "svb", "lvb", "dvb", "cqh", "cqb" ->
            ctx.viewportHeightPx * v / 100f
        "vmin", "svmin", "lvmin", "dvmin", "cqmin" -> minOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        "vmax", "svmax", "lvmax", "dvmax", "cqmax" -> maxOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        else -> 0f                                 // unknown unit — safe zero
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    // CSS calc spec: + and - require whitespace on BOTH sides. This lets us
    // disambiguate between "calc(10px-5px)" (invalid — a single token with
    // a negative sign in the middle, which is itself a parse error, but we
    // err on the "token" side) and "calc(10px - 5px)" (subtraction).
    private fun hasWsAround(at: Int): Boolean {
        val before = at > 0 && src[at - 1].isWhitespace()
        val after = at + 1 < src.length && src[at + 1].isWhitespace()
        return before || after
    }
}
