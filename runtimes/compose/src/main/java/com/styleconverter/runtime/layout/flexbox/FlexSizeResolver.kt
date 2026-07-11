package com.styleconverter.runtime.layout.flexbox

// css-flexbox-1 §9.7 "Resolve Flexible Lengths" — pure-Kotlin, px-space.
//
// Compose's Modifier.weight() cannot express the CSS flex sizing model:
// weight distributes the WHOLE main axis proportionally, while CSS grows /
// shrinks each item FROM ITS FLEX BASE (flex-basis, falling back to the
// main-size property). The wave-2 measurement showed all three basis
// fixtures diverging (FR_GrowBasis 0.842, FC_GrowBasis 0.902,
// FR_ShrinkBasis 0.894): Android children sat at their placeholder floor
// while web ran the real algorithm.
//
// This resolver runs the spec loop AHEAD of composition whenever every
// participating child has a definite (px) flex base, and the renderer then
// pins each child to its resolved main size with Modifier.width/height.
// Content-based bases (`flex-basis: auto` with no main-size declaration)
// are NOT resolvable statically — resolve() returns null. The renderer then
// runs the wave-9 INTRINSIC pass instead (FlexIntrinsicLayout.kt): it
// measures each content-sized item's max-content main size at layout time
// (css-flexbox-1 §9.2.3.E) and re-enters this same loop with the bases
// filled in. The legacy Modifier.weight fallback survives ONLY for lines
// whose container main size is genuinely unknowable ahead of composition
// (percentage / unresolvable-relative container sizes).
object FlexSizeResolver {

    /**
     * One flex item's inputs, all in px.
     *
     * @param basisPx  the used flex base size (flex-basis, else the item's
     *                 main-size property). Null = content-sized → resolve()
     *                 bails; FlexIntrinsicLayout fills it from the item's
     *                 measured max-content size (§9.2.3.E) and retries.
     * @param grow     flex-grow (CSS initial 0).
     * @param shrink   flex-shrink (CSS initial 1).
     * @param minPx    the item's main-axis minimum. Mirrors the WEB
     *                 harness wrapper (`ComponentRenderer.tsx`):
     *                 min = main-size ?? min-size ?? placeholder floor
     *                 (50px inline / 30px block). That floor is what makes
     *                 web clamp FR_GrowBasis `a` (basis 40) to 50px and
     *                 FR_ShrinkBasis `c` to 50px — pixel-verified against
     *                 the wave-2 web captures.
     * @param maxPx    the item's main-axis maximum (max-width / max-height,
     *                 css-flexbox-1 §9.7.4.d "max violation"). Defaults to
     *                 +∞ = no declared maximum, which keeps every pre-wave-9
     *                 caller and test byte-identical.
     */
    data class Item(
        val basisPx: Double?,
        val grow: Double,
        val shrink: Double,
        val minPx: Double,
        val maxPx: Double = Double.POSITIVE_INFINITY
    )

    /**
     * Resolve the used main sizes for a single non-wrapping flex line.
     *
     * @param contentMainPx the container's main-axis CONTENT size: the
     *        declared border-box size minus padding and border bands on the
     *        main axis (web is `box-sizing: border-box` throughout).
     * @param gapPx main-axis gap between adjacent items.
     * @return one used size per item (same order), or null when the line
     *         can't be resolved statically (missing basis / no room info).
     */
    fun resolve(contentMainPx: Double, gapPx: Double, items: List<Item>): List<Double>? {
        if (items.isEmpty() || contentMainPx <= 0.0) return null
        // Any content-sized basis → bail; the renderer keeps legacy behaviour.
        val bases = items.map { it.basisPx ?: return null }

        // §9.7.1 — determine the used flex factor: grow when the sum of the
        // (gap-inclusive) bases undershoots the container, shrink otherwise.
        val gaps = gapPx * (items.size - 1)
        val initialFree = contentMainPx - bases.sum() - gaps
        val growing = initialFree > 0.0

        // Working state: size per item + frozen flags. Items freeze when
        // they hit a min/max violation (or are inflexible from the start).
        val sizes = bases.toMutableList()
        val frozen = BooleanArray(items.size)

        // Clamp helper — an item's used size always lands inside its
        // [min, max] band; a min larger than the max wins (CSS 2.1 §10.4
        // min-beats-max rule, inherited by flexbox).
        fun clamp(i: Int, v: Double) = v.coerceIn(items[i].minPx, maxOf(items[i].maxPx, items[i].minPx))

        // §9.7.2 — freeze inflexible items at their (min/max-clamped) base.
        items.forEachIndexed { i, it ->
            val inflexible = if (growing) it.grow <= 0.0 else it.shrink <= 0.0
            if (inflexible) {
                sizes[i] = clamp(i, bases[i])
                frozen[i] = true
            }
        }

        // §9.7.4 loop — distribute the remaining free space over unfrozen
        // items, clamp min/max violations, freeze violators, repeat. Bounded
        // by items.size iterations (each pass freezes ≥1 item or terminates).
        repeat(items.size + 1) {
            val unfrozen = items.indices.filter { !frozen[it] }
            if (unfrozen.isEmpty()) return sizes
            // Free space is measured against frozen items' FINAL sizes and
            // unfrozen items' bases (the spec's "remaining free space").
            val free = contentMainPx - gaps -
                items.indices.sumOf { if (frozen[it]) sizes[it] else bases[it] }
            if (growing) {
                val totalGrow = unfrozen.sumOf { items[it].grow }
                if (totalGrow <= 0.0 || free <= 0.0) {
                    // Nothing left to distribute: clamp and finish.
                    unfrozen.forEach { sizes[it] = clamp(it, bases[it]) }
                    return sizes
                }
                unfrozen.forEach { i ->
                    sizes[i] = bases[i] + free * (items[i].grow / totalGrow)
                }
            } else {
                // Shrink weights are scaled by the base (§9.7.4.c) so large
                // items give up proportionally more space.
                val totalScaled = unfrozen.sumOf { items[it].shrink * bases[it] }
                if (totalScaled <= 0.0 || free >= 0.0) {
                    unfrozen.forEach { sizes[it] = clamp(it, bases[it]) }
                    return sizes
                }
                unfrozen.forEach { i ->
                    val ratio = (items[i].shrink * bases[i]) / totalScaled
                    // free is negative here — this subtracts.
                    sizes[i] = bases[i] + free * ratio
                }
            }
            // §9.7.4.d/e — fix min/max violations. Clamp every unfrozen item
            // into its band, total the (clamped − unclamped) violations, then
            // freeze by the TOTAL's sign: positive → the min clamps consumed
            // space (freeze min violators), negative → the max clamps freed
            // space (freeze max violators), zero → done. The loop then
            // redistributes what the clamps changed among the still-unfrozen.
            var totalViolation = 0.0
            val clamped = DoubleArray(items.size)
            unfrozen.forEach { i ->
                clamped[i] = clamp(i, sizes[i])
                totalViolation += clamped[i] - sizes[i]
            }
            if (totalViolation == 0.0) {
                // Zero total → freeze everything at its clamped size (§9.7.4.e
                // "Zero: freeze all items"). With no violations clamped ==
                // distributed, so the pre-wave-9 return value is preserved.
                unfrozen.forEach { i -> sizes[i] = clamped[i] }
                return sizes
            }
            unfrozen.forEach { i ->
                // A violator is a min violator when the clamp grew it, a max
                // violator when the clamp shrank it (§9.7.4.e).
                val minViolated = clamped[i] > sizes[i]
                val maxViolated = clamped[i] < sizes[i]
                sizes[i] = clamped[i]
                if ((totalViolation > 0.0 && minViolated) ||
                    (totalViolation < 0.0 && maxViolated)
                ) frozen[i] = true
            }
        }
        return sizes
    }
}
