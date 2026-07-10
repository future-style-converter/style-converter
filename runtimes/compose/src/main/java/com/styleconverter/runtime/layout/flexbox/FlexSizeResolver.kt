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
// are NOT resolvable here — resolve() returns null and the renderer keeps
// its legacy weight fallback.
object FlexSizeResolver {

    /**
     * One flex item's inputs, all in px.
     *
     * @param basisPx  the used flex base size (flex-basis, else the item's
     *                 main-size property). Null = content-sized → whole
     *                 line unresolvable.
     * @param grow     flex-grow (CSS initial 0).
     * @param shrink   flex-shrink (CSS initial 1).
     * @param minPx    the item's main-axis minimum. Mirrors the WEB
     *                 harness wrapper (`ComponentRenderer.tsx`):
     *                 min = main-size ?? min-size ?? placeholder floor
     *                 (50px inline / 30px block). That floor is what makes
     *                 web clamp FR_GrowBasis `a` (basis 40) to 50px and
     *                 FR_ShrinkBasis `c` to 50px — pixel-verified against
     *                 the wave-2 web captures.
     */
    data class Item(
        val basisPx: Double?,
        val grow: Double,
        val shrink: Double,
        val minPx: Double
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
        // they hit a min violation (or are inflexible from the start).
        val sizes = bases.toMutableList()
        val frozen = BooleanArray(items.size)

        // §9.7.2 — freeze inflexible items at their (min-clamped) base.
        items.forEachIndexed { i, it ->
            val inflexible = if (growing) it.grow <= 0.0 else it.shrink <= 0.0
            if (inflexible) {
                sizes[i] = maxOf(bases[i], it.minPx)
                frozen[i] = true
            }
        }

        // §9.7.4 loop — distribute the remaining free space over unfrozen
        // items, clamp min violations, freeze violators, repeat. Bounded by
        // items.size iterations (each pass freezes ≥1 item or terminates).
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
                    // Nothing left to distribute: min-clamp and finish.
                    unfrozen.forEach { sizes[it] = maxOf(bases[it], items[it].minPx) }
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
                    unfrozen.forEach { sizes[it] = maxOf(bases[it], items[it].minPx) }
                    return sizes
                }
                unfrozen.forEach { i ->
                    val ratio = (items[i].shrink * bases[i]) / totalScaled
                    // free is negative here — this subtracts.
                    sizes[i] = bases[i] + free * ratio
                }
            }
            // §9.7.4.d/e — fix min violations: clamp + freeze, then loop to
            // redistribute what the clamp consumed among the still-unfrozen.
            var violated = false
            unfrozen.forEach { i ->
                if (sizes[i] < items[i].minPx) {
                    sizes[i] = items[i].minPx
                    frozen[i] = true
                    violated = true
                }
            }
            if (!violated) return sizes
        }
        return sizes
    }
}
