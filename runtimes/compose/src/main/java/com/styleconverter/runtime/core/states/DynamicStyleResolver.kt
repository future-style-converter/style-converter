package com.styleconverter.runtime.core.states

// DynamicStyleResolver — the dynamic-styling runtime v1 style-resolution
// step (schema/spec/06-dynamic-styling.md §2/§3/§6): decides which selector
// buckets are ACTIVE for the current interaction/forced state and folds
// active media + selector buckets over the base property list.
//
// Resolution happens BEFORE extraction (spec 06 §1): extractors and appliers
// never see buckets, only the effective list this object produces. The
// functions are pure (JVM-testable) — ComponentRenderer supplies the live
// InteractionState / forced set / media activation and re-runs resolution on
// every state flip (restyle without recomposition of the tree, §5).

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRMedia
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRSelector
import com.styleconverter.runtime.core.states.StateHandler.SelectorCondition

object DynamicStyleResolver {

    // Logcat tag for the one-time inert-condition warnings below.
    private const val TAG = "DynamicStyleResolver"

    /**
     * The forced-state hook (spec 06 §6): condition names in this set are
     * treated as ACTIVE at style resolution regardless of real input state.
     * The harness capture screen provides it from the launch intent
     * (docs/DYNAMIC_CAPTURE.md §1 — one forced state per capture run);
     * production hosts leave it empty. Names are the canonical lowercase
     * colon-stripped forms in [FORCEABLE_STATES].
     */
    val LocalForcedStates = compositionLocalOf<Set<String>> { emptySet() }

    /** The runtime-v1 condition vocabulary the harness may force (spec 06 §2/§6). */
    val FORCEABLE_STATES = setOf("hover", "active", "focus", "disabled", "checked")

    // Conditions already reported inert — spec 06 §2 wants the miss logged
    // once per condition, not once per recomposition frame.
    private val loggedInert = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * True when `selectors` contains at least one condition whose activation
     * is driven by REAL INPUT on Android — hover / active / focus. This is
     * the efficiency gate (only components WITH such buckets pay for an
     * InteractionSource + input modifiers). disabled / checked are excluded:
     * they have no self-service input source — the host app or the harness
     * forced-state hook supplies them (spec 06 §2 platform notes).
     */
    fun needsInteractionSource(selectors: List<IRSelector>): Boolean {
        return selectors.any { sel ->
            // Pseudo-elements ride the selectors channel but belong to the
            // ContentApplier — they are never interaction states.
            if (isPseudoElement(sel.condition)) return@any false
            when (StateHandler.parseCondition(sel.condition)) {
                SelectorCondition.HOVER,
                SelectorCondition.ACTIVE,
                SelectorCondition.FOCUS -> true
                else -> false
            }
        }
    }

    /**
     * `::before` / `::after` (and legacy single-colon spellings) are
     * pseudo-ELEMENTS: the ContentApplier consumes them for generated
     * content (see ContentApplier.extractBeforeAfterConfig), so they are
     * silently skipped here — neither activated nor logged as inert.
     */
    internal fun isPseudoElement(condition: String): Boolean {
        val c = condition.trim().lowercase()
        // "::" prefix is the css-pseudo-4 canonical form; the single-colon
        // aliases are the CSS2 legacy forms ContentApplier also accepts.
        return c.startsWith("::") || c == ":before" || c == ":after" || c == ":marker"
    }

    /**
     * Activation test for ONE selector condition at runtime v1 (spec 06 §2).
     *
     * @param state live interaction state, or null when the component has no
     *   input wiring (then only forced states can activate anything).
     * @param forcedStates the §6 forced set — checked FIRST so a forced run
     *   resolves identically whether or not real input is present.
     */
    fun isConditionActive(
        condition: String,
        state: StateHandler.InteractionState?,
        forcedStates: Set<String>
    ): Boolean {
        return when (StateHandler.parseCondition(condition)) {
            // hover — pointer surfaces only. Compose only ever emits
            // HoverInteraction for mouse/stylus enter/exit; touch presses
            // raise PressInteraction, never Hover, so the spec's "defined
            // no-op on touch, no first-tap latching" holds by construction.
            SelectorCondition.HOVER -> "hover" in forcedStates || (state?.isHovered ?: false)
            // active — a press gesture in progress (InteractionSource
            // pressed), deactivating on release/cancel.
            SelectorCondition.ACTIVE -> "active" in forcedStates || (state?.isPressed ?: false)
            // focus — the platform focus system via FocusInteraction.
            SelectorCondition.FOCUS -> "focus" in forcedStates || (state?.isFocused ?: false)
            // disabled — host-supplied flag (or forced); the runtime only
            // styles it (spec 06 §2).
            SelectorCondition.DISABLED -> "disabled" in forcedStates || (state != null && !state.isEnabled)
            // checked — host-supplied checkable state (or forced).
            SelectorCondition.CHECKED -> "checked" in forcedStates || (state?.isChecked ?: false)
            // focus-visible / focus-within are RESERVED at runtime v1 —
            // inert + logged (spec 06 §2), NOT approximated by focus.
            SelectorCondition.FOCUS_VISIBLE,
            SelectorCondition.FOCUS_WITHIN,
            // enabled is not in the runtime-v1 table either — inert.
            SelectorCondition.ENABLED,
            // Structural pseudo-classes, :visited, :nth-child(…), … —
            // preserved on the wire, inactive at runtime v1.
            SelectorCondition.UNKNOWN -> reportInert(condition)
        }
    }

    /**
     * Filter `selectors` down to the ACTIVE buckets, PRESERVING ARRAY ORDER
     * — spec 06 §3 layers active selector buckets in `selectors[]` order
     * (last writer wins per property type), never by a priority heuristic.
     */
    fun activeSelectorBuckets(
        selectors: List<IRSelector>,
        state: StateHandler.InteractionState?,
        forcedStates: Set<String>
    ): List<IRSelector> {
        // Fast path — the overwhelmingly common bucket-free component.
        if (selectors.isEmpty()) return emptyList()
        return selectors.filter { sel ->
            // Pseudo-elements: the ContentApplier channel, skipped silently.
            !isPseudoElement(sel.condition) &&
                isConditionActive(sel.condition, state, forcedStates)
        }
    }

    /**
     * Spec 06 §3 — fold the active buckets over the base list:
     * base → each active MEDIA bucket in array order → each active SELECTOR
     * bucket in array order. State-over-media is fixed by the spec (a
     * pressed/hovered recolor must beat a width-bucket recolor).
     *
     * Identity guarantee: with no active buckets the base list is returned
     * UNCHANGED (same instance), so the static-fixture corpus keeps its
     * remember()-key stability and renders byte-identically.
     */
    fun resolve(
        base: List<IRProperty>,
        activeMediaBuckets: List<IRMedia>,
        activeSelectorBuckets: List<IRSelector>
    ): List<IRProperty> {
        // Step 1 starts from the base `properties` list.
        var effective = base
        // Step 2 — active media buckets, media[] array order.
        for (bucket in activeMediaBuckets) effective = overlay(effective, bucket.properties)
        // Step 3 — active selector buckets, selectors[] array order.
        for (bucket in activeSelectorBuckets) effective = overlay(effective, bucket.properties)
        return effective
    }

    /**
     * Overlay one bucket per spec 06 §3: for each property envelope in the
     * bucket, REPLACE the current entry with the same `type` IN PLACE
     * (whole-value replacement — never a partial/deep merge of `data`), or
     * APPEND it when no entry with that type exists yet.
     */
    internal fun overlay(
        current: List<IRProperty>,
        bucket: List<IRProperty>
    ): List<IRProperty> {
        // Empty bucket contributes nothing — keep the current instance.
        if (bucket.isEmpty()) return current
        // LinkedHashMap gives exactly the required position semantics:
        // put() on an existing key replaces the value but keeps the key's
        // original position; a new key appends at the end.
        val out = LinkedHashMap<String, IRProperty>(current.size + bucket.size)
        for (p in current) out[p.type] = p
        for (p in bucket) out[p.type] = p
        return out.values.toList()
    }

    /**
     * The inert-condition path (spec 06 §2): mark the condition unhandled in
     * the PropertyTracker and warn once per condition string. Never a crash,
     * never drops the component. Always returns false so isConditionActive
     * can `-> reportInert(condition)` directly.
     */
    private fun reportInert(condition: String): Boolean {
        // Namespaced key so selector-condition and property-type misses
        // don't mix in coverage reports.
        PropertyTracker.markUnhandled("Selector[$condition]")
        // Log once per distinct condition; add() is atomic on the synced set.
        if (loggedInert.add(condition)) {
            try {
                Log.w(TAG, "selector condition inert at runtime v1 (bucket inactive): \"$condition\"")
            } catch (_: RuntimeException) {
                // android.util.Log is unmocked in plain-JVM unit tests; the
                // PropertyTracker mark above already recorded the miss.
            }
        }
        return false
    }
}
