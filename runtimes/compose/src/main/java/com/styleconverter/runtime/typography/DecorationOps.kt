// DecorationOps.kt — pure decoration-line PATTERN + placement geometry
// (applier campaign wave 21, lane TEXTDECOR, B-RC8/B-RC7).
//
// BYTE-PARALLEL TWIN of the iOS runtime's
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
// decoration/DecorationOps.swift — same functions, same constants, same
// pin table. Change one, change both.
//
// Why this file exists: the extractors already parsed
// `text-decoration-style` / `text-decoration-thickness` into
// TextStyleApplier.TextDecorationConfig, but the owned decoration
// painter SILENTLY dropped both — every style painted as a SOLID rect
// at font-derived auto thickness (the exact lossy:false loss this wave
// exists to kill). This file turns one solid band into the styled op
// list (rect runs / circle runs) with Chromium-matched rhythm.
//
// GEOMETRY ORACLE — the algorithm is ported from Blink and then pinned
// against the live wave-21 ref PNGs (tools/wpt/refs/9b5435e55e0b54a6cd0
// 9c1c563861eb3c999cef1/white-black-ink-font-lh/css-text-decor/):
//   Blink sources (chromium main, fetched 2026-07-26):
//     • third_party/blink/renderer/platform/graphics/styled_stroke_data.cc
//       — SelectBestDashGap + DashEffectFromStrokeStyle (dash/dot fit)
//     • third_party/blink/renderer/core/paint/decoration_line_painter.cc
//       — DrawLineAsStroke (round-cap inset, mid-row snap, odd-width
//       half-pixel shift), StrokeIsDashed (dotted ≤3px = square dashes)
//   Measured pins (text-decoration-dotted-001.png, 390×600 canvas, red
//   underline bands for thickness 10/20/30 @92px Arial, run x-start 62,
//   run width W=460 — solved from all three pitches simultaneously):
//     t=10: band rows 207-216, dot pitch 19.565 (gap 9.565)
//     t=20: band rows 363-382, dot pitch 40.000 (gap 20.000)
//     t=30: band rows 519-548, dot pitch 61.429 (gap 31.429)
//   text-decoration-dotted-002.png (same layout, W=409):
//     pitches 19.950 / 38.900 / 63.167 — same algorithm, second W. ✓
//   Underline placement (both refs): band TOP − text ink bottom row =
//   5 / 10 / 15 for t = 10 / 20 / 30 → gap below the snapped baseline
//   = max(1, ceil(t/2)), Blink's ComputeUnderlineOffsetAuto gap term
//   (third_party/blink/renderer/core/layout/text_decoration_offset.cc).
//
// Pure Kotlin over Float — no Compose/Android types — so the JVM suite
// (DecorationOpsTest) pins every branch without a raster.
package com.styleconverter.runtime.typography

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object DecorationOps {

    /**
     * The five css-text-decor-3 §2.3 line styles, mirrored as an OWN
     * enum so this file stays dependency-free (pure Kotlin, no Compose
     * / applier imports — standalone-compilable for the JVM pin suite).
     * The renderer maps TextStyleApplier.TextDecorationStyleType (same
     * member names) at the call site; the iOS twin maps its
     * TextDecorationPattern the same way.
     */
    enum class LineStyle { SOLID, DOUBLE, DOTTED, DASHED, WAVY }

    /** One paint op in text-layout px space (density-1 harness). */
    sealed interface Op {
        /** Axis-aligned filled rect — solid bands and dash runs. */
        data class Band(val left: Float, val top: Float, val width: Float, val height: Float) : Op
        /** Filled circle — one thick-dotted dot (Blink round cap, diameter = thickness). */
        data class Dot(val centerX: Float, val centerY: Float, val radius: Float) : Op
    }

    /**
     * One per-visual-line decoration band BEFORE style expansion:
     * the same shape TextStyleApplier.DecorationSegment carries — kept
     * separate so this file stays free of the applier's Compose deps.
     */
    data class LineBand(val left: Float, val top: Float, val width: Float, val thickness: Float)

    /** Blink kEpsilon (styled_stroke_data.cc): shaves the dot pitch so
     *  rounding along the line never drops the END dot. */
    private const val DASH_FIT_EPSILON = 0.01f

    /**
     * Blink SelectBestDashGap (styled_stroke_data.cc), open-path form:
     * pick the gap that deviates least from [gapLength] while fitting a
     * whole number of dashes into [strokeLength]. Pinned against both
     * dotted refs (file-header table): (460,10,10)→9.565, (460,20,20)→20,
     * (460,30,30)→31.429, (409,30,30)→33.167.
     */
    fun selectBestDashGap(strokeLength: Float, dashLength: Float, gapLength: Float): Float {
        // Open path: one virtual trailing gap is available beyond the end.
        val available = strokeLength + gapLength
        // Candidate dash counts bracketing the ideal-density fit.
        val minNumDashes = floor(available / (dashLength + gapLength))
        val maxNumDashes = minNumDashes + 1f
        // Open path has one fewer gap than dashes.
        val minNumGaps = minNumDashes - 1f
        val maxNumGaps = maxNumDashes - 1f
        // The gap each candidate count forces.
        val minGap = (strokeLength - minNumDashes * dashLength) / minNumGaps
        val maxGap = (strokeLength - maxNumDashes * dashLength) / maxNumGaps
        // Keep whichever deviates least from the ideal (never a ≤0 gap).
        return if (maxGap <= 0f || abs(minGap - gapLength) < abs(maxGap - gapLength)) minGap else maxGap
    }

    /**
     * Blink GetSnappedPointsForTextLine + DrawLineAsStroke: the stroked
     * centerline row = floor(top + max(h/2, 0.5)), shifted +0.5 when the
     * ROUNDED thickness is odd so an odd-width stroke aligns to the
     * pixel grid ("For odd widths, shift the line down by 0.5").
     */
    private fun snappedMidY(top: Float, thicknessPx: Float, roundedThickness: Int): Float {
        // Integer mid row like Blink's gfx::Point snap.
        val mid = floor(top + max(thicknessPx / 2f, 0.5f))
        // Odd-width pixel-grid alignment.
        return if (roundedThickness % 2 == 1) mid + 0.5f else mid
    }

    /**
     * Expand ONE solid band into the styled op list. SOLID emits the
     * band itself byte-for-byte (rect(left, top, width, thickness) — the
     * legacy draw call, so auto-path captures cannot move). DOUBLE and
     * WAVY are NOT implemented yet — they fall back to the same solid
     * band and the CALLER must surface [isLossyFallback] (no silent
     * fallthroughs; neither style appears in the wave-21 gate corpus).
     */
    fun styleOps(
        left: Float,
        top: Float,
        width: Float,
        thicknessPx: Float,
        style: LineStyle
    ): List<Op> = when (style) {
        // Chromium round-cap dot runs (≤3px: square-dash runs).
        LineStyle.DOTTED -> dottedOps(left, top, width, thicknessPx)
        // Chromium butt-cap dash runs.
        LineStyle.DASHED -> dashedOps(left, top, width, thicknessPx)
        // solid, plus the DOCUMENTED double/wavy solid fallback.
        else -> listOf(Op.Band(left, top, width, thicknessPx))
    }

    /** True when [styleOps] paints a lossy solid stand-in — callers log it. */
    fun isLossyFallback(style: LineStyle): Boolean =
        style == LineStyle.DOUBLE || style == LineStyle.WAVY

    /**
     * `text-decoration-style: dotted` — Blink DashEffectFromStrokeStyle.
     * Thickness > 3px: zero-length dashes + round caps = CIRCLES of
     * diameter = thickness; the line is inset t/2 per end so caps don't
     * overhang (ref: dot LEFT edges sit exactly on the run start x=62
     * for all three thicknesses). Thickness ≤ 3px (StrokeIsDashed):
     * square t-on/t-off dashes, butt caps, no gap fitting.
     */
    fun dottedOps(left: Float, top: Float, width: Float, thicknessPx: Float): List<Op> {
        // Zero inked extent → nothing (browser paints nothing).
        if (width <= 0f) return emptyList()
        // Blink DrawLineAsStroke: pattern lengths use the ROUNDED int
        // thickness (roundf), floored at 1 so thin lines still pattern.
        val t = max(1, Math.round(thicknessPx))
        // Stroke centerline row (odd widths ride +0.5 — header helper).
        val midY = snappedMidY(top, thicknessPx, t)
        // ≤3px dotted is drawn as square dashes (StrokeIsDashed) with
        // dash = gap = t and NO SelectBestDashGap (only kDashedStroke
        // adjusts its gap in Blink).
        if (t <= 3) return dashRunOps(left, midY, width, thicknessPx, t.toFloat(), t.toFloat(), fitGap = false)
        // Round caps extend t/2 beyond each endpoint → Blink insets the
        // stroked segment; the DASH PHASE then runs over width − t.
        val insetPathLength = width - t
        // Blink: path_length (the PRE-inset width) too short for 2 dots
        // → a single dot at the inset start.
        if (width < 2f * t) return listOf(Op.Dot(left + t / 2f, midY, thicknessPx / 2f))
        // Chromium-fit gap (pinned: 9.565 / 20.0 / 31.429 at W=460).
        val gap = selectBestDashGap(width, t.toFloat(), t.toFloat())
        // Dash interval {0, gap + t − ε}: dot centers every gap+t−ε.
        val pitch = gap + t - DASH_FIT_EPSILON
        // One circle per pattern point that lands on the inset segment
        // (the ε above is exactly what keeps the END dot inside). The
        // FIRST dot is unconditional (do-while): at width == 2t exactly,
        // SelectBestDashGap divides by zero gaps → gap = +Inf (Blink hits
        // the same IEEE Inf) and 0·Inf = NaN would skip the k=0 test —
        // but Skia always paints the phase-0 dot of a dash interval, so
        // Blink renders ONE dot there, not zero (wave-21 skeptic pin).
        val ops = mutableListOf<Op>()
        var k = 0
        do {
            // Center = inset start + k pitches; radius = t/2 (round cap).
            // k=0 must not touch pitch at all: 0·Inf = NaN, and the
            // phase-0 dot sits exactly at the inset start regardless.
            val dx = if (k == 0) 0f else k * pitch
            ops.add(Op.Dot(left + t / 2f + dx, midY, thicknessPx / 2f))
            k++
        } while (k * pitch <= insetPathLength)
        return ops
    }

    /**
     * `text-decoration-style: dashed` — Blink DashEffectFromStrokeStyle
     * kDashedStroke: dash = t × (t≥3 ? 2 : 3), ideal gap = dash ×
     * (t≥3 ? 1 : 2) (thin lines need longer dashes/gaps to stay legible
     * — Blink's DashLengthRatio/DashGapRatio), gap then refitted via
     * SelectBestDashGap. Butt caps, no endpoint inset.
     */
    fun dashedOps(left: Float, top: Float, width: Float, thicknessPx: Float): List<Op> {
        // Zero inked extent → nothing.
        if (width <= 0f) return emptyList()
        // Rounded pattern thickness, 1px floor (as in dottedOps).
        val t = max(1, Math.round(thicknessPx))
        // Stroke centerline row.
        val midY = snappedMidY(top, thicknessPx, t)
        // Blink DashLengthRatio / DashGapRatio.
        val dash = t * (if (t >= 3) 2f else 3f)
        val gap = dash * (if (t >= 3) 1f else 2f)
        // Shared dash-run emitter (also serves thin dotted above).
        return dashRunOps(left, midY, width, thicknessPx, dash, gap, fitGap = true)
    }

    /**
     * Butt-cap dash run — the shared tail of Blink's StrokeIsDashed
     * branch. [fitGap] mirrors Blink: only kDashedStroke refits its gap.
     */
    private fun dashRunOps(
        left: Float,
        midY: Float,
        width: Float,
        strokeHeightPx: Float,
        dash: Float,
        idealGap: Float,
        fitGap: Boolean
    ): List<Op> {
        // A stroke of width h centered on midY covers [midY−h/2, +h/2].
        val bandTop = midY - strokeHeightPx / 2f
        // Blink: "No space for dashes" (L ≤ 2·dash) → nullopt → the
        // stroke paints SOLID. Not a silent loss: it is Blink behavior.
        if (width <= dash * 2f) return listOf(Op.Band(left, bandTop, width, strokeHeightPx))
        // Exactly-two-dashes window: both dash and gap scale down
        // proportionally so dash+gap+dash spans the line exactly.
        val twoDashesWithGap = 2f * dash + idealGap
        if (width <= twoDashesWithGap) {
            // Proportional multiplier (Blink: length / two_dashes_...).
            val m = width / twoDashesWithGap
            // First dash at the start, second flush with the end.
            return listOf(
                Op.Band(left, bandTop, dash * m, strokeHeightPx),
                Op.Band(left + (dash + idealGap) * m, bandTop, dash * m, strokeHeightPx)
            )
        }
        // Fitted (dashed) or fixed (thin dotted) gap.
        val g = if (fitGap) selectBestDashGap(width, dash, idealGap) else idealGap
        // Emit dashes from the line start; the LAST dash clips at the
        // line end exactly like Skia clips a dash interval mid-pattern.
        val ops = mutableListOf<Op>()
        var x = 0f
        while (x < width) {
            // Clip the final partial dash to the remaining extent.
            ops.add(Op.Band(left + x, bandTop, min(dash, width - x), strokeHeightPx))
            x += dash + g
        }
        return ops
    }

    // ---- explicit-thickness band placement (css-text-decor-4 §2.4) ----

    /** Inter ascent em — mirrors TextStyleApplier.DECORATION_ASCENT_EM
     *  (private there; the twin files need it, drift is pinned by
     *  DecorationOpsTest's parity case). Wave 22 (lane DECOR): widened
     *  from private to internal so DecorationColorOps.bandTop reuses THIS
     *  constant instead of declaring a third copy that could drift. */
    internal const val ASCENT_EM = 1984f / 2048f

    /** Inter strikeout-position em — mirrors
     *  TextStyleApplier.STRIKEOUT_POSITION_EM (same parity pin). Widened
     *  to internal alongside ASCENT_EM for the same single-source reason. */
    internal const val STRIKEOUT_EM = 671f / 2048f

    /**
     * Blink's gap between baseline and underline TOP when a thickness
     * is known: max(1, ceil(T/2)) (text_decoration_offset.cc
     * ComputeUnderlineOffsetAuto). Ref-pinned: band top − ink bottom =
     * 5 / 10 / 15 px for T = 10 / 20 / 30 in BOTH dotted refs.
     * (The AUTO-thickness path keeps the legacy wave-5 gap == T rule in
     * TextStyleApplier.decorationSegments — its 22px capture oracle and
     * the 327-pair baseline pin those rows; this rule fires only when
     * the IR declares `text-decoration-thickness`.)
     */
    fun explicitUnderlineGapPx(thicknessPx: Float): Float =
        max(1f, ceil(thicknessPx / 2f))

    /**
     * Per-visual-line bands for an EXPLICIT `text-decoration-thickness`.
     *
     * Wave 22 (lane DECOR) note: the RENDERER no longer calls this — the
     * owned pass routes through DecorationColorOps.bands so each band can
     * carry its own §2.2 colour. This function stays as the ref-pinned
     * REFERENCE implementation: DecorationWirePinTest asserts the new
     * path reproduces it exactly across the flag lattice and all three
     * measured thicknesses, so any drift in the fold fails loudly.
     *
     * — the thickness-aware twin of TextStyleApplier.decorationSegments
     * (same accessor-lambda shape so the same call site feeds either):
     *   underline    top = round(baseline) + max(1, ceil(T/2))   (ref-pinned)
     *   overline     top = round(baseline) − round(ascent·fs) − T (bottom
     *                 edge stays flush with the line-box top, T swapped in)
     *   line-through CENTER stays at baseline − strike·fs; the band
     *                 straddles it (±T/2) then snaps — same
     *                 parameterization as the auto path, T swapped in.
     */
    fun explicitBands(
        lineCount: Int,
        fontSizePx: Float,
        thicknessPx: Float,
        underline: Boolean,
        overline: Boolean,
        lineThrough: Boolean,
        lineBaseline: (Int) -> Float,
        lineLeft: (Int) -> Float,
        lineRight: (Int) -> Float
    ): List<LineBand> {
        // Nothing flagged → nothing owned (total, like decorationSegments).
        if (!underline && !overline && !lineThrough) return emptyList()
        // Chromium's integral rounded ascent (same as the auto path).
        val roundedAscent = Math.round(fontSizePx * ASCENT_EM)
        return (0 until lineCount).flatMap { i ->
            // This visual line's inked extent.
            val leftX = lineLeft(i)
            val rightX = lineRight(i)
            // Empty visual line → no decoration.
            if (rightX <= leftX) return@flatMap emptyList<LineBand>()
            val width = rightX - leftX
            // Integral baseline row — web rows are pixel-snapped.
            val baseline = Math.round(lineBaseline(i)).toFloat()
            // Same under → over → through emit order as the auto path.
            buildList {
                if (underline) add(LineBand(
                    // Ref-pinned Blink gap rule (function header).
                    leftX, baseline + explicitUnderlineGapPx(thicknessPx), width, thicknessPx
                ))
                if (overline) add(LineBand(
                    // Bottom edge flush with the line-box top.
                    leftX, baseline - roundedAscent - thicknessPx, width, thicknessPx
                ))
                if (lineThrough) add(LineBand(
                    // Strike CENTER unchanged; band straddles ±T/2.
                    leftX,
                    Math.round(baseline - fontSizePx * STRIKEOUT_EM - thicknessPx / 2f).toFloat(),
                    width, thicknessPx
                ))
            }
        }
    }

    // ---- unbreakable-run detection (B-RC7, UAX #14 approximation) ----

    /**
     * True when [text] offers at least one soft-wrap opportunity — the
     * UAX #14 approximation this wave prescribes: whitespace (class BA/
     * SP/BK…), explicit break enablers (soft hyphen U+00AD, ZWSP
     * U+200B), break-AFTER punctuation (hyphen-minus, U+2010 hyphen,
     * en/em dashes — UAX #14 classes HY/BA), or any ideographic char
     * (class ID breaks between any pair — CJK, kana, Hangul, fullwidth
     * forms). 'fooשלוםbaz' (dotted-001) and 'foobarbaz' (dotted-002)
     * contain NONE of these → unbreakable → the run must lay out on one
     * overflowing line like the Chromium ref (which keeps each on ONE
     * line at 92px inside a 390px canvas), instead of the engines'
     * emergency mid-run wrap.
     */
    fun hasSoftWrapOpportunity(text: String): Boolean = text.any { ch ->
        // Whitespace of any flavor (space, tab, newline) EXCEPT NBSP —
        // Kotlin's isWhitespace includes the SPACE_SEPARATOR category,
        // but NBSP FORBIDS breaks (css-text-3 §5.2 / UAX #14 class GL).
        // The Swift twin carves out the same scalar.
        (ch.isWhitespace() && ch != '\u00A0') ||
            // NEL U+0085 (UAX #14 class BK, a MANDATORY break): Java's
            // isWhitespace/isSpaceChar both exclude it (category Cc)
            // while Swift's Unicode White_Space includes it, so carve it
            // IN explicitly to keep the twins identical (skeptic pin).
            ch == '\u0085' ||
            // Soft hyphen / zero-width space: explicit opportunities.
            ch == '\u00AD' || ch == '\u200B' ||
            // Break-after hyphens and dashes (UAX #14 HY/BA).
            ch == '-' || ch == '\u2010' || ch == '\u2013' || ch == '\u2014' ||
            // Ideographic (UAX #14 ID): breaks allowed between any two.
            isIdeographic(ch)
    }

    /** UAX #14 class-ID block approximation (see caller). */
    private fun isIdeographic(ch: Char): Boolean {
        val c = ch.code
        // CJK radicals … unified ideographs (incl. kana + CJK punct).
        return (c in 0x2E80..0x9FFF) ||
            // Hangul syllables.
            (c in 0xAC00..0xD7AF) ||
            // CJK compatibility ideographs.
            (c in 0xF900..0xFAFF) ||
            // Full-width / half-width forms.
            (c in 0xFF00..0xFFEF)
    }
}
