package com.styleconverter.test.style.spacing

// MarginTrimApplier — documented no-op for Phase 2. Compose has no primitive
// to cancel a specific child's resolved margin at its container boundary.
// Implementing it correctly requires writing a custom Layout that inspects
// every child's MarginConfig and zeroes the relevant sides based on its
// position (first / last). That work is out of scope here but we register
// the Applier so the PropertyTracker reports "implemented stub".
//
// When a future implementation lands, this class should become a
// CompositionLocal provider that MarginApplier reads to suppress the
// matching sides when rendering the first/last child of the container.

import androidx.compose.ui.Modifier

object MarginTrimApplier {

    /**
     * Apply margin-trim to a container's Modifier. Currently a no-op.
     * Returns [modifier] unchanged regardless of [config].
     */
    @Suppress("UNUSED_PARAMETER")
    fun apply(modifier: Modifier, config: MarginTrimConfig): Modifier {
        // Intentionally empty — see file-level comment for rationale.
        return modifier
    }
}
