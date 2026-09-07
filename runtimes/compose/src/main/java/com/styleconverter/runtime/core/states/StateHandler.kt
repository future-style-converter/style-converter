package com.styleconverter.runtime.core.states

// StateHandler — maps CSS interaction pseudo-classes onto Compose's
// InteractionSource machinery (schema/spec/06-dynamic-styling.md §2).
//
// Split of responsibilities in this package:
//   - StateHandler (this file): condition parsing + the live interaction
//     plumbing (collect pressed/hovered/focused, attach input modifiers).
//   - DynamicStyleResolver: which buckets are ACTIVE (incl. forced states,
//     §6) and how they layer over base properties (§3).
//
// Runtime-v1 condition set (spec 06 §2): hover (pointer surfaces only —
// touch is a defined no-op), active (press), focus, disabled, checked.
// focus-visible / focus-within / enabled and everything structural are
// RESERVED: preserved on the wire, inert at resolution (DynamicStyleResolver
// logs the miss once — the no-silent-fallthrough rule).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

object StateHandler {

    /**
     * Selector condition vocabulary. The wire carries colon-stripped names
     * ("hover", not ":hover" — pinned by v2/dynamic-styling.json);
     * [parseCondition] also tolerates the single-colon spelling. Members
     * beyond the runtime-v1 five exist so the resolver can name WHAT is
     * reserved instead of lumping everything into UNKNOWN.
     */
    enum class SelectorCondition {
        HOVER,          // css-selectors-4 §7.1 — pointer designation
        ACTIVE,         // css-selectors-4 §7.2 — activation in progress
        FOCUS,          // css-selectors-4 §9.3 — input focus
        FOCUS_VISIBLE,  // reserved at runtime v1 (inert + logged)
        FOCUS_WITHIN,   // reserved at runtime v1 (inert + logged)
        DISABLED,       // css-selectors-4 §12.1.2 — host-supplied flag
        ENABLED,        // not in the runtime-v1 table (inert + logged)
        CHECKED,        // css-selectors-4 §12.2.1 — host-supplied flag
        UNKNOWN         // anything else — preserved on wire, inert
    }

    /**
     * Parse a wire condition string. Lowercases, trims, and strips ONE
     * leading colon (":hover" → hover) — "::before" keeps a colon and lands
     * in UNKNOWN, which is correct: pseudo-ELEMENTS ride the ContentApplier
     * channel, not interaction state (see DynamicStyleResolver.isPseudoElement).
     */
    fun parseCondition(condition: String): SelectorCondition {
        return when (condition.lowercase().trim().removePrefix(":")) {
            "hover" -> SelectorCondition.HOVER
            "active" -> SelectorCondition.ACTIVE
            "focus" -> SelectorCondition.FOCUS
            "focus-visible" -> SelectorCondition.FOCUS_VISIBLE
            "focus-within" -> SelectorCondition.FOCUS_WITHIN
            "disabled" -> SelectorCondition.DISABLED
            "enabled" -> SelectorCondition.ENABLED
            "checked" -> SelectorCondition.CHECKED
            else -> SelectorCondition.UNKNOWN
        }
    }

    /**
     * Snapshot of a component's interaction state at one resolution pass.
     * hovered/pressed/focused come from the InteractionSource collectors;
     * enabled/checked are HOST-supplied flags (spec 06 §2: the runtime only
     * styles them) — defaulted to the interactive/unchecked baseline, and
     * overridable by the harness forced-state hook at resolution time.
     */
    data class InteractionState(
        val isHovered: Boolean,
        val isPressed: Boolean,
        val isFocused: Boolean,
        val isEnabled: Boolean = true,
        val isChecked: Boolean = false
    ) {
        companion object {
            /** The at-rest state — what a component resolves with before any input. */
            val IDLE = InteractionState(isHovered = false, isPressed = false, isFocused = false)
        }
    }

    /**
     * Collect the live interaction state for one component. Reading the
     * three collect*AsState values here makes the CALLING composable
     * re-resolve styles on every state flip — a restyle of the same
     * composable identity, no subtree recreation (spec 06 §5 re-evaluation
     * contract: stable identity, within one frame).
     */
    @Composable
    fun rememberInteractionState(
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        isEnabled: Boolean = true,
        isChecked: Boolean = false
    ): Pair<MutableInteractionSource, InteractionState> {
        // HoverInteraction — emitted ONLY for pointer (mouse/stylus)
        // enter/exit; Android touch never produces one, which gives the
        // spec's "hover is a defined no-op on touch" for free.
        val isHovered by interactionSource.collectIsHoveredAsState()
        // PressInteraction — a press gesture in progress (:active).
        val isPressed by interactionSource.collectIsPressedAsState()
        // FocusInteraction — the platform focus system (:focus).
        val isFocused by interactionSource.collectIsFocusedAsState()

        return Pair(
            interactionSource,
            InteractionState(
                isHovered = isHovered,
                isPressed = isPressed,
                isFocused = isFocused,
                isEnabled = isEnabled,
                isChecked = isChecked
            )
        )
    }

    /**
     * Attach the input handlers that FEED the interaction source. Applied by
     * ComponentRenderer only to components that carry hover/active/focus
     * selector buckets (the efficiency gate — everyone else pays nothing).
     *
     *  - clickable emits PressInteraction (→ :active). indication = null:
     *    visual state feedback is the selector bucket's job, not a ripple.
     *  - hoverable emits HoverInteraction on pointer enter/exit (→ :hover;
     *    inert on touch by construction — see rememberInteractionState).
     *  - focusable emits FocusInteraction (→ :focus) when the platform
     *    focus system lands on the node (keyboard/d-pad; touch taps do not
     *    focus a plain box, matching web button behaviour).
     */
    fun Modifier.stateInteractions(interactionSource: MutableInteractionSource): Modifier =
        this
            .clickable(
                interactionSource = interactionSource,
                // No indication: the pressed VISUAL comes from the :active
                // bucket via style resolution, never a Material ripple.
                indication = null,
                onClick = { /* press styling only — activation is not an event the IR models */ }
            )
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
}
