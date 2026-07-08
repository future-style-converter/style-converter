package com.styleconverter.runtime.spacing

// Shared helper that collapses a LengthValue into Dp for the padding/margin
// appliers. The logic lives here so both Appliers share one source of truth
// for em/rem/vw/vh/% resolution. Anything context-dependent (percentages
// measured against parent width, em against a font size that may come from a
// Composable) takes the required context as a parameter — this file has no
// Compose imports so it's cheap to unit-test.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
)

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
        LengthUnit.PERCENT -> (ctx.parentWidthPx ?: ctx.viewportWidthPx) * v / 100f
        LengthUnit.EM -> v * ctx.fontSizePx
        LengthUnit.REM -> v * ctx.rootFontSizePx
        // Viewport-relative units. Compose fronts a small/large/dynamic
        // distinction that we don't meaningfully support yet; treat the three
        // groups as identical to the classic viewport.
        LengthUnit.VW, LengthUnit.SVW, LengthUnit.LVW, LengthUnit.DVW -> ctx.viewportWidthPx * v / 100f
        LengthUnit.VH, LengthUnit.SVH, LengthUnit.LVH, LengthUnit.DVH -> ctx.viewportHeightPx * v / 100f
        LengthUnit.VMIN, LengthUnit.SVMIN, LengthUnit.LVMIN, LengthUnit.DVMIN ->
            minOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        LengthUnit.VMAX, LengthUnit.SVMAX, LengthUnit.LVMAX, LengthUnit.DVMAX ->
            maxOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        // Fallbacks: keep a sane value rather than crashing. Most of these
        // units (ex/ch/cap/ic/lh/rlh/vi/vb/cq*/fr) aren't used by spacing in
        // practice. If the pxFallback is present we take it.
        else -> r.pxFallback?.toFloat() ?: 0f
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
        "%" -> (ctx.parentWidthPx ?: ctx.viewportWidthPx) * v / 100f
        "vw", "svw", "lvw", "dvw" -> ctx.viewportWidthPx * v / 100f
        "vh", "svh", "lvh", "dvh" -> ctx.viewportHeightPx * v / 100f
        "vmin", "svmin", "lvmin", "dvmin" -> minOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
        "vmax", "svmax", "lvmax", "dvmax" -> maxOf(ctx.viewportWidthPx, ctx.viewportHeightPx) * v / 100f
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
