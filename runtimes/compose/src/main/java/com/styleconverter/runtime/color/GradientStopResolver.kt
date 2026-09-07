package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// GradientStopResolver — wave 47, lane Z1 (the Android half of the
// wave-46 Y2 css-images gradient pipeline; byte-parallel twin of iOS
// StyleEngine/background/GradientStopResolver.swift).
//
// The PURE stop pipeline every gradient flavour shares:
//   1. css-images-3 §3.4.3 / css-images-4 §3.5.3 colour-stop FIXUP — declared percents win,
//      <length> stops resolve against the gradient-line length, a stop
//      behind its predecessor is clamped forward, and unpositioned runs
//      spread evenly between their positioned neighbours. Before this
//      lane the Compose extractor spread EVERY unpositioned stop over
//      the whole count and dropped px stops entirely, so `white, black,
//      white 30px` had no period at all (wave-46 gate: gradient-border-
//      box android-ref 0.646 — one full-box ramp instead of 30px
//      stripes; the ref PNG shows 13 diagonal stripe periods).
//   2. REPEATING expansion (css-images-4 §3.4 / images-3 §3.3):
//      the stop span tiles the line in both directions, materialised as
//      explicit stops — replacing RepeatingGradientHelper's
//      fraction-range × diagonal approximation, which could never carry
//      a px period and phase-centred the lattice off-spec.
//   3. Unit-range CLIP — Skia wants 0…1 locations; §3.4.3's "before
//      the first stop / after the last" colours become boundary stops.
// Step 4 — SUBDIVISION in the authored interpolation space — lives in
// GradientRamp and runs ONLY for gradients with an `interp` clause:
// clause-less ramps go to the Skia shader untouched, whose native sRGB
// lerp IS the historical rendering (the deliberate divergence from the
// iOS twin, which must subdivide even legacy ramps because SwiftUI
// gradients interpolate perceptually).

internal object GradientStopResolver {

    /**
     * One resolved stop: concrete sRGB components (Double, matching the
     * iOS twin's precision for the colour math) + a FLOAT 0…1 location —
     * Skia consumes float positions, and float keeps the all-nil spread
     * bit-identical to the pre-wave extractor's `i/(n−1)` (pinned by
     * GradientStopResolverTest's dark-stage block).
     */
    data class RGBAStop(val r: Double, val g: Double, val b: Double, val a: Double, val loc: Float) {
        /** Back to a Compose colour (sRGB float ctor — the packed ARGB
         *  round trip is lossless for extractor-built colours). */
        fun toColor(): Color = Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
    }

    // ── 1. Fixup (§3.4.3) ────────────────────────────────────────────────

    /**
     * Resolve positions. [lengthPx] is the gradient line length (the
     * radius for radial flavours); null means px stops cannot resolve
     * here (conic stops are angles — the converter never emits a length
     * for them) and such a stop behaves as unpositioned, with a
     * breadcrumb so the gap is never silent.
     */
    fun fixup(stops: List<ColorStop>, lengthPx: Float?): List<RGBAStop> {
        if (stops.isEmpty()) return emptyList()
        // Step 0 — declared position per stop (fraction) or null. The
        // declared percent wins over a px length (grammar allows one or
        // the other; the converter never emits both — iOS twin order).
        val loc = arrayOfNulls<Float>(stops.size)
        stops.forEachIndexed { i, s ->
            val p = s.declaredPosition
            if (p != null) { loc[i] = p; return@forEachIndexed }
            val px = s.positionPx
            if (px != null) {
                if (lengthPx != null && lengthPx > 0f) {
                    loc[i] = px / lengthPx
                } else {
                    GradientLog.once("gradient-px-stop-no-length",
                        "gradient <length> stop position has no gradient-line length here — treating it as unpositioned")
                }
            }
        }
        // Step 1 — first/last default to 0% / 100% (§3.4.3 rule 1).
        if (loc[0] == null) loc[0] = 0f
        if (loc[stops.size - 1] == null) loc[stops.size - 1] = 1f
        // Step 2 — a stop behind any predecessor is clamped forward
        // (§3.4.3 rule 2; WPT gradient-move-stops' `…, green 0`).
        var running = loc[0]!!
        for (i in 1 until stops.size) {
            val v = loc[i]
            if (v != null) { running = max(running, v); loc[i] = running }
        }
        // Step 3 — unpositioned runs spread evenly between positioned
        // neighbours (§3.4.3 rule 3). For an all-nil list `prev = 0f,
        // next = 1f` makes this EXACTLY (k+1)/(n−1) in float — the
        // pre-wave extractor's even spread, bit for bit.
        var i = 1
        while (i < stops.size) {
            if (loc[i] != null) { i++; continue }
            var j = i
            while (loc[j] == null) j++          // j = next positioned (last is never null)
            val prev = loc[i - 1]!!; val next = loc[j]!!
            val runLen = j - i
            for (k in 0 until runLen) {
                loc[i + k] = prev + (next - prev) * ((k + 1).toFloat() / (runLen + 1).toFloat())
            }
            i = j
        }
        // Colours ride through as sRGB doubles for the interp math.
        return stops.mapIndexed { idx, s ->
            RGBAStop(
                r = s.color.red.toDouble(), g = s.color.green.toDouble(),
                b = s.color.blue.toDouble(), a = s.color.alpha.toDouble(),
                loc = loc[idx]!!
            )
        }
    }

    // ── 2. Repeating expansion ───────────────────────────────────────────

    /**
     * Ceiling on materialised copies — SAME cap as the iOS twin. A
     * period needing more copies than this is finer than ~1/253 of the
     * gradient line (≈1.5px on the 390px capture line) — a lattice no
     * capture resolves, and enumerating its thousands of stops would
     * cost every render for a moiré nobody can see. Past the cap the
     * tiling degrades to the §3.3.3 average solid below, NEVER to a
     * partial lattice (the wave-46 F2 rule: a truncated lattice striped
     * the first 256 periods and left the rest flat with a hard seam —
     * a WRONG picture, not a degraded one).
     */
    const val MAX_COPIES = 256

    /**
     * Tile the stop span across [0, 1]. A span that cannot be tiled —
     * zero-length, or finer than [MAX_COPIES] allows — renders as the
     * spec's "solid average colour" (css-images-3 §3.3).
     */
    fun expandRepeating(stops: List<RGBAStop>): List<RGBAStop> {
        // Fewer than two stops: nothing to tile (a single stop is the
        // §3.4.4 uniform fill — the widening below handles the shader).
        if (stops.size < 2) return stops
        val first = stops.first(); val last = stops.last()
        val period = last.loc - first.loc
        // Zero-length period — §3.3.3's stated degenerate rendering.
        if (period <= 1e-9f) return solidAverage(stops)
        // Copies k with first.loc + k·period spanning [−period, 1+period]
        // so the clip step always finds a crossing segment at 0 and 1.
        val kMin = floor((0f - first.loc) / period).toInt() - 1
        val kMax = ceil((1f - first.loc) / period).toInt() + 1
        if (kMax - kMin + 1 > MAX_COPIES) {
            // More copies than the cap allows: paint the §3.3.3 average
            // flat across the whole line (the F2 rule — see MAX_COPIES),
            // with a breadcrumb so the degradation is never silent.
            GradientLog.once("gradient-repeating-period-too-fine",
                "repeating gradient period needs ${kMax - kMin + 1} copies (cap $MAX_COPIES) — painting the css-images-3 §3.3 average colour instead of a partial lattice")
            return solidAverage(stops)
        }
        val out = ArrayList<RGBAStop>((kMax - kMin + 1) * stops.size)
        for (k in kMin..kMax) {
            val shift = k * period
            for (s in stops) out.add(s.copy(loc = s.loc + shift))
        }
        // Monotonicity repair: copy k's LAST stop and copy k+1's FIRST
        // stop are meant to coincide at first.loc + (k+1)·period, but the
        // two float paths (`last.loc + k·p` vs `first.loc + (k+1)·p`)
        // can land one ulp apart in either order (found by the JVM pin:
        // 0.1f-period copies produced …0.70000005, 0.69999999… — a
        // NON-monotonic list, which Skia's gradient shaders treat as
        // undefined input). Clamp forward to the running max — the same
        // §3.4.3 rule-2 clamp, applied to the materialised lattice; a
        // one-ulp shift is invisible, an unordered shader input is not.
        var run = out[0].loc
        for (idx in 1 until out.size) {
            if (out[idx].loc < run) out[idx] = out[idx].copy(loc = run) else run = out[idx].loc
        }
        return out
    }

    /**
     * css-images-3 §3.3's degenerate rendering: "the average color of
     * all the color stops", painted flat across the whole line. The
     * unweighted stop mean is the spec's own wording (not the
     * length-weighted integral of the ramp), and both boundary stops
     * carry the SAME colour, so no seam can appear anywhere on the line.
     */
    private fun solidAverage(stops: List<RGBAStop>): List<RGBAStop> {
        val n = stops.size.toDouble()
        // Sum-of-quotients, matching the iOS twin's reduce expression
        // term for term so the two natives quantise identically.
        var r = 0.0; var g = 0.0; var b = 0.0; var a = 0.0
        for (s in stops) { r += s.r / n; g += s.g / n; b += s.b / n; a += s.a / n }
        return listOf(RGBAStop(r, g, b, a, 0f), RGBAStop(r, g, b, a, 1f))
    }

    // ── 3. Unit clip ─────────────────────────────────────────────────────

    /**
     * Confine to 0…1: stops outside are replaced by the colour the ramp
     * has AT the boundary (interpolated in [interp]), and a ramp that
     * starts after 0 / ends before 1 pads with its end colours (§3.4.3
     * "the first stop's colour before it, the last stop's after it").
     * Already-in-range lists come back untouched (byte stability — the
     * committed clause-less baselines all take that early return).
     */
    fun clipToUnit(stops: List<RGBAStop>, interp: GradientInterpolation): List<RGBAStop> {
        if (stops.isEmpty()) return stops
        val first = stops.first(); val last = stops.last()
        if (first.loc >= 0f && last.loc <= 1f) return stops
        val out = ArrayList<RGBAStop>(stops.size)
        for ((i, s) in stops.withIndex()) {
            if (s.loc < 0f) {
                // Crossing into range: emit the colour at 0.
                if (i + 1 < stops.size && stops[i + 1].loc >= 0f) out.add(sample(s, stops[i + 1], 0f, interp))
                continue
            }
            if (s.loc > 1f) {
                // Leaving range: emit the colour at 1 and stop.
                if (i > 0 && stops[i - 1].loc <= 1f) out.add(sample(stops[i - 1], s, 1f, interp))
                break
            }
            out.add(s)
        }
        // Wave 48 (lane W1): EVERY stop past one end of the line — the loop
        // above finds no in-range stop and no crossing pair, so `out` is
        // empty. §3.4.3's padding rule still defines the rendering: the
        // ramp "before the first stop" is the first stop's colour and
        // "after the last stop" the last's, so a line that ends before its
        // first stop paints the FIRST colour everywhere, and one that
        // starts after its last stop paints the LAST colour everywhere.
        // Returning emptyList() here instead was a live crash: WPT
        // css-break/background-image-006 clones `linear-gradient(green
        // 80px, red 140px)` into fragments whose gradient line is shorter
        // than 80px (its ref is all-green for exactly that reason), both
        // px stops resolved past 1.0, shaderStops returned null, and
        // ColorApplier's contract-`!!` NPE'd every draw — the app crashed
        // per frame and the css-break cell shipped unmeasured (feeder
        // TIMEOUT). Uniform fill = the same colour at both ends, the
        // §3.4.4 idiom the one-stop widening already uses.
        if (out.isEmpty()) {
            // All stops above 1 ⇒ [0,1] lies before the first stop.
            val pad = if (first.loc > 1f) first else last
            return listOf(pad.copy(loc = 0f), pad.copy(loc = 1f))
        }
        // §3.4.3 padding: the first stop's colour before it, the last
        // stop's after it — as boundary stops so Skia's clamp shows them.
        if (out[0].loc > 0f) out.add(0, out[0].copy(loc = 0f))
        if (out[out.size - 1].loc < 1f) out.add(out[out.size - 1].copy(loc = 1f))
        return out
    }

    /** The ramp colour at absolute location [x] between two stops. */
    private fun sample(a: RGBAStop, b: RGBAStop, x: Float, interp: GradientInterpolation): RGBAStop {
        val span = b.loc - a.loc
        val t = if (span > 0f) ((x - a.loc) / span).toDouble() else 1.0
        return GradientRamp.interpolate(a, b, t, interp).copy(loc = x)
    }

    // ── Composed pipeline ────────────────────────────────────────────────

    /**
     * The full stop pipeline: §3.4.3 fixup → repeating expansion → unit
     * clip → interp subdivision. Clause-less gradients skip subdivision
     * entirely — Skia's native sRGB shader lerp IS the historical ramp,
     * so a fixed-up in-range list renders byte-identically to the
     * pre-wave `stops.map { it.position }` hand-off (measured over the
     * five committed gradient fixtures in GradientStopResolverTest's
     * dark-stage block). Authored `interp` clauses take GradientRamp's
     * css-color-4 §13 path.
     */
    fun resolvedRamp(
        stops: List<ColorStop>,
        lengthPx: Float?,
        repeating: Boolean,
        interp: GradientInterpolation
    ): List<RGBAStop> {
        var ramp = fixup(stops, lengthPx)
        if (repeating) ramp = expandRepeating(ramp)
        ramp = clipToUnit(ramp, interp)
        if (interp.space == GradientInterpolation.Space.LEGACY) return ramp
        return GradientRamp.subdivided(ramp, interp)
    }

    /**
     * Shader-ready (colours, positions) for one gradient, or null when
     * there are NO stops at all (the brush factories' existing contract:
     * a malformed payload fails visibly rather than inventing a colour).
     * A one-stop result is widened to the same colour at 0 and 1 — the
     * §3.4.4 uniform fill — because every Skia gradient shader requires
     * at least two entries (the wave-36 M8 rule, now applied at the end
     * of the pipeline instead of before it).
     */
    fun shaderStops(
        stops: List<ColorStop>,
        lengthPx: Float?,
        repeating: Boolean,
        interp: GradientInterpolation
    ): Pair<List<Color>, List<Float>>? {
        if (stops.isEmpty()) return null
        var ramp = resolvedRamp(stops, lengthPx, repeating, interp)
        if (ramp.isEmpty()) return null
        // One resolved stop → uniform fill via two identical entries.
        if (ramp.size == 1) ramp = listOf(ramp[0].copy(loc = 0f), ramp[0].copy(loc = 1f))
        return ramp.map { it.toColor() } to ramp.map { it.loc }
    }
}
