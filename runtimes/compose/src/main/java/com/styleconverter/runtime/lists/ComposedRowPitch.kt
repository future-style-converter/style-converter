package com.styleconverter.runtime.lists

import androidx.compose.runtime.compositionLocalOf

/**
 * The residual carrier that makes [ListMarkerLineBox.snappedHeightPx]'s
 * ACCUMULATED rounding reachable from a marker row — wave 30, lane 3
 * (fix B3). Compose-only; iOS already matches the reference pitch and has
 * no twin of this file, deliberately (see the measured table in
 * [ListMarkerLineBox.snappedHeightPx]).
 *
 * ## Why a carrier at all
 * The rule is "place row k at `round(Σ exact heights above k)`", and the
 * only place that knows a row's EXACT (fractional) line box is the row's
 * own snap modifier — Compose's layout pass carries integers everywhere
 * else, so a stacking container measuring its children can no longer tell
 * a 31 that meant 31.0 from a 31 that meant 31.25. The exact value is
 * therefore published where it is still known, and the prefix is read
 * back from the same object.
 *
 * ## The ORDERING CONSTRAINT (the price of not writing a custom Layout)
 * [RowPitchAccumulator.prefixFor] returns "the sum of the exact heights
 * registered BEFORE this key", so the sequence of first registrations
 * must be the container's PLACEMENT order. That holds because Compose's
 * flow containers (`Column`, and the block branch of
 * `ComponentRenderer.RenderContent` that installs this) measure their
 * children in the order they were emitted, which is the order they are
 * placed. Two properties keep it from silently rotting:
 *
 *  - registration is IDEMPOTENT per key, so a re-measure (intrinsics
 *    pass, settle frame, recomposition) re-reads the same prefix instead
 *    of advancing the accumulator a second time;
 *  - the key is the item's IR id, which is stable across passes.
 *
 * If a future container ever measures out of order, the failure is NOT
 * the "±1px pitch" an earlier draft of this header claimed. MEASURED
 * (wave-30 skeptic, PROBE3b): make the FIRST registration a row other
 * than the first-placed one and that row is displaced by 63px on the
 * `css3-counter-styles-007` shape — two whole line boxes at its 31.25px
 * pitch, plainly visible, not sub-pixel noise. The reason is structural:
 * a mis-ordered registration hands a row the prefix belonging to a
 * DIFFERENT position in the stack, so the error is bounded by the
 * mis-ordered rows' line boxes, not by the ±1px the rounding itself can
 * move. It is still not a crash and still the same set of boxes — the
 * ordering constraint is a correctness precondition with a visible
 * failure mode, and is stated as one. A custom `Layout` reading the exact
 * heights off parent data would remove the constraint; it also needs every
 * intervening container (`<ol>` → `<li>` → row) to forward that parent
 * data, which is the reason it was not done in this lane.
 */
class RowPitchAccumulator {

    /**
     * Insertion-ordered `key → exact line-box height in px`. A
     * LinkedHashMap because the ORDER is the whole datum: the prefix of a
     * row is the sum of everything inserted before it.
     */
    private val rows = LinkedHashMap<Any, Float>()

    /**
     * Register [exactPx] for [key] and return the exact total of every
     * row registered BEFORE it.
     *
     * Re-registering a known key UPDATES its height (a settle frame can
     * take a row from 0 lines to 1) but does NOT move it in the order and
     * does not append a duplicate — that idempotence is what makes a
     * re-measure safe (see the ordering constraint in the file header).
     */
    fun prefixFor(key: Any, exactPx: Float): Float {
        var prefix = 0f
        for ((existing, height) in rows) {
            if (existing == key) {
                rows[existing] = exactPx
                return prefix
            }
            prefix += height
        }
        rows[key] = exactPx
        return prefix
    }

    /** Rows registered so far — the JVM pin's window onto the order. */
    val size: Int get() = rows.size
}

/**
 * The accumulator in scope for a marker row, or null outside composed WPT
 * capture (every product path and the whole 327-pair dark stage), where
 * the isolated rounding stands and no capture may move.
 *
 * `compositionLocalOf` (not `staticCompositionLocalOf`): the value changes
 * per stacking container, and a static local would invalidate the whole
 * subtree on every change.
 *
 * Installed by the OUTERMOST block container of a capture — the provider
 * reads `current ?: RowPitchAccumulator()`, so a nested container
 * inherits its ancestor's instead of starting a fresh count, and a row's
 * prefix is the sum of every line box stacked above it anywhere in that
 * capture rather than just within its immediate parent. That is what the
 * `css3-counter-styles-007` shape needs: the 24 rows are 24 separate
 * `<ol>`s under one wrapper, so each row is an only child of its own
 * container.
 */
val LocalRowPitchAccumulator = compositionLocalOf<RowPitchAccumulator?> { null }
