package com.styleconverter.runtime.color

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// GradientRamp — wave 47, lane Z1 (byte-parallel twin of iOS
// StyleEngine/background/GradientRamp.swift).
//
// The per-pair colour math of a gradient ramp: css-color-4 §13
// interpolation between two resolved stops (premultiplied alpha, hue
// arc selection, powerless-hue carry) and the subdivision that bakes
// that ramp into micro-stops the Skia shaders can follow. Only
// gradients carrying an authored `interp` clause reach this file:
// LEGACY (no clause) ramps never subdivide on Android — the resolved
// stops go to Skia untouched and Skia's own sRGB lerp IS the historical
// rendering, so every clause-less capture stays byte-identical (the
// platform divergence from iOS, whose SwiftUI gradients interpolate
// perceptually and therefore need the 12-segment sRGB subdivision).
//
// Colour-space conversions live in GradientColorMath; stop positions
// are resolved upstream by GradientStopResolver.

internal object GradientRamp {

    // ── Interpolation (css-color-4 §13) ──────────────────────────────────

    /**
     * Colour at [t] between [a] and [b] in the authored space:
     * premultiplied non-hue components (§13.4), hue arc per method
     * (§13.5), powerless hue carried from the other stop (§13.3).
     */
    fun interpolate(
        a: GradientStopResolver.RGBAStop,
        b: GradientStopResolver.RGBAStop,
        t: Double,
        interp: GradientInterpolation
    ): GradientStopResolver.RGBAStop {
        val space = interp.space
        val ca = comps(GradientColorMath.fromSrgb(GradientColorMath.Triple3(a.r, a.g, a.b), space))
        val cb = comps(GradientColorMath.fromSrgb(GradientColorMath.Triple3(b.r, b.g, b.b), space))
        val alpha = a.a + (b.a - a.a) * t
        val hueIx = GradientColorMath.hueIndex(space)
        // Missing-component carry for the hue (§13.3 "analogous"): a
        // powerless hue takes the other stop's hue before the arc is
        // chosen — `red → black in hsl longer hue` therefore sweeps the
        // whole wheel instead of fading straight to black.
        if (hueIx != null) {
            val pa = GradientColorMath.hueIsPowerless(triple(ca), space)
            val pb = GradientColorMath.hueIsPowerless(triple(cb), space)
            if (pa && !pb) ca[hueIx] = cb[hueIx]
            if (pb && !pa) cb[hueIx] = ca[hueIx]
        }
        val out = DoubleArray(3)
        for (i in 0..2) {
            val va = ca[i]; val vb = cb[i]
            out[i] = when {
                i == hueIx -> hueLerp(va, vb, t, interp.hue)
                space == GradientInterpolation.Space.LEGACY ->
                    // Historical ramp: plain component lerp, no premultiply
                    // (only clipToUnit boundary sampling reaches this arm on
                    // Android — the shader handles legacy in-range lerps).
                    va + (vb - va) * t
                alpha > 0 ->
                    // Premultiplied (§13.4): each component weighted by its
                    // own alpha, un-premultiplied by the interpolated alpha.
                    (va * a.a * (1 - t) + vb * b.a * t) / alpha
                else ->
                    // Both fully transparent — invisible either way.
                    va + (vb - va) * t
            }
        }
        val rgb = GradientColorMath.toSrgb(GradientColorMath.Triple3(out[0], out[1], out[2]), space)
        return GradientStopResolver.RGBAStop(
            r = rgb.c0, g = rgb.c1, b = rgb.c2, a = alpha,
            // Location interpolates in FLOAT — Skia consumes float
            // positions, and the resolver keeps loc float throughout.
            loc = a.loc + (b.loc - a.loc) * t.toFloat()
        )
    }

    /** Triple3 → mutable array so components are index-addressable. */
    private fun comps(c: GradientColorMath.Triple3) = doubleArrayOf(c.c0, c.c1, c.c2)

    /** Array → Triple3 for the powerless analysis. */
    private fun triple(c: DoubleArray) = GradientColorMath.Triple3(c[0], c[1], c[2])

    /**
     * css-color-4 §13.5 arc selection: the (possibly >360 / unordered)
     * endpoint pair the lerp runs between. Exposed for the subdivision
     * density and for JUnit (GradientStopResolverTest.hueArcSelection).
     */
    fun hueArc(h1: Double, h2: Double, method: GradientInterpolation.HueMethod): Pair<Double, Double> {
        var a = norm(h1); var b = norm(h2)
        val d = b - a
        when (method) {
            // Shorter arc: never sweep more than 180°.
            GradientInterpolation.HueMethod.SHORTER ->
                if (d > 180) a += 360 else if (d < -180) b += 360
            // Longer arc: always sweep at least 180° (equal hues → 360°).
            GradientInterpolation.HueMethod.LONGER ->
                if (d > 0 && d < 180) a += 360 else if (d > -180 && d <= 0) b += 360
            // Increasing: h2 must not be below h1 — wrap it up a turn.
            GradientInterpolation.HueMethod.INCREASING -> if (b < a) b += 360
            // Decreasing: h1 must not be below h2 — wrap IT up a turn.
            GradientInterpolation.HueMethod.DECREASING -> if (a < b) a += 360
        }
        return a to b
    }

    /** Hue at [t] along the selected arc, normalised to [0, 360). */
    fun hueLerp(h1: Double, h2: Double, t: Double, method: GradientInterpolation.HueMethod): Double {
        val (a, b) = hueArc(h1, h2, method)
        return norm(a + (b - a) * t)
    }

    /** Wrap any angle into [0, 360). */
    private fun norm(h: Double): Double {
        val m = h % 360
        return if (m < 0) m + 360 else m
    }

    // ── Subdivision ──────────────────────────────────────────────────────

    /**
     * Micro-stops between each adjacent pair, computed in the authored
     * space. Polar sweeps get one stop per ≤10° of hue so a 360° wheel
     * is not flattened to 12 chords; [budget] caps the total so a
     * repeating lattice with hundreds of copies stays renderable. Same
     * constants as the iOS twin (budget 600, per-pair ≤12, densify only
     * while the list is ≤16 stops).
     */
    fun subdivided(
        stops: List<GradientStopResolver.RGBAStop>,
        interp: GradientInterpolation,
        budget: Int = 600
    ): List<GradientStopResolver.RGBAStop> {
        // 0/1 stops have nothing to interpolate between.
        if (stops.size < 2) return stops
        val perPair = max(1, min(12, budget / max(1, stops.size - 1)))
        // The per-10° hue densification only applies while the list is
        // short (a plain 2–16 stop gradient); a repeating lattice with
        // dozens of copies keeps the budgeted count so the stop total
        // stays bounded (~600) whatever the hue sweep.
        val densify = stops.size <= 16
        val out = ArrayList<GradientStopResolver.RGBAStop>(stops.size * perPair)
        out.add(stops[0])
        for (i in 1 until stops.size) {
            val s0 = stops[i - 1]; val s1 = stops[i]
            val segments = if (densify) segmentCount(s0, s1, interp, perPair) else perPair
            for (k in 1..segments) {
                out.add(interpolate(s0, s1, k.toDouble() / segments, interp))
            }
        }
        return out
    }

    /** Hue-sweep-aware segment count for one pair (≥ one per 10°). */
    private fun segmentCount(
        a: GradientStopResolver.RGBAStop,
        b: GradientStopResolver.RGBAStop,
        interp: GradientInterpolation,
        base: Int
    ): Int {
        val h = GradientColorMath.hueIndex(interp.space) ?: return base
        val ca = comps(GradientColorMath.fromSrgb(GradientColorMath.Triple3(a.r, a.g, a.b), interp.space))
        val cb = comps(GradientColorMath.fromSrgb(GradientColorMath.Triple3(b.r, b.g, b.b), interp.space))
        // A powerless endpoint adopts the other hue (as interpolate does),
        // so its sweep is measured on the carried pair.
        val pa = GradientColorMath.hueIsPowerless(triple(ca), interp.space)
        val pb = GradientColorMath.hueIsPowerless(triple(cb), interp.space)
        val h0 = if (pa && !pb) cb[h] else ca[h]
        val h1 = if (pb && !pa) ca[h] else cb[h]
        val (s, e) = hueArc(h0, h1, interp.hue)
        return max(base, ceil(abs(e - s) / 10).toInt())
    }
}
