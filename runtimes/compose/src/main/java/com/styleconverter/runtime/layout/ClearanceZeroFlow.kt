package com.styleconverter.runtime.layout

// ClearanceZeroFlow — the Compose adapter half of the wave-42 lane-W5
// CSS 2.2 §9.5.2 clearance emulation (pure math: FloatClearance.kt; iOS
// twin: the ClearanceZeroFlow modifier in StyleEngine/layout/
// FloatClearance.swift). Two pieces:
//   • the CompositionLocal carrying a resolved FloatClearancePlan from
//     its scope-root block container down to every nested block loop
//     (adjustments are id-keyed, so a plan leaking past its scope is
//     inert by construction — ids are document-unique);
//   • the zero-flow wrapper that renders a FLOAT at its flow slot while
//     reporting ZERO block-axis size to the parent Column, the "floats
//     are out of flow" half of §9.5.2 (CSS 2.1 §9.5: following in-flow
//     block boxes position as if the float were not there).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.Layout

/**
 * The scope's §9.5.2 plan, provided by the block container FloatClearance
 * resolved for (a composed root or BFC root) and read by every descendant
 * block child loop. Default null = no clearance scope — the entire
 * pre-wave-42 corpus renders through that identity branch byte-for-byte.
 */
val LocalFloatClearancePlan = compositionLocalOf<FloatClearancePlan?> { null }

/**
 * Render [content] (one float child's full style chain) with ZERO
 * reported height. The child measures under the parent's incoming
 * constraints — its own width/height modifiers (including % widths
 * against the loosened max) resolve exactly as in the plain block loop —
 * and paints at the flow slot's top-left; only the REPORTED size shrinks
 * to (width, 0), so the parent Column advances its cursor as if the
 * float were not there. Compose does not clip children by default, so
 * the float's ink overlays following in-flow content exactly like the
 * browser's out-of-flow paint (CSS 2.1 §9.5 / Appendix E step 4).
 */
@Composable
internal fun ClearanceZeroFlow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        // Loosened mins: the float's own size chain decides its box (the
        // same discipline FloatRowLayout's P7 unbounded measure pins),
        // while the max stays the container's so % widths resolve.
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // One float per wrapper in practice; map defensively anyway.
        val placeables = measurables.map { it.measure(loose) }
        // Report the measured width (the Column's cross-size max() still
        // sees the float) and ZERO height — the out-of-flow contract.
        val width = (placeables.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, 0.coerceAtLeast(constraints.minHeight)) {
            // Place at the flow slot's origin; a float:right child's own
            // TopEnd alignment wrapper (floatEndAlignment) already parks
            // its ink at the right edge inside the measured box.
            placeables.forEach { it.place(0, 0) }
        }
    }
}
