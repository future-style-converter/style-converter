package com.styleconverter.test.style.layout

import androidx.compose.ui.Modifier

// Phase 7 step 1 scaffold applier.
//
// Split mirror of LayoutExtractor: the extractor produces a LayoutConfig, the
// applier consumes it to produce either:
//   (a) a Compose Modifier contribution for CHILD-level layout properties
//       (zIndex, alignSelf, order) — applied on the component itself; or
//   (b) a ContainerDecision that the ComponentRenderer consumes to pick
//       the correct Compose container primitive (Row / Column / Box /
//       LazyVerticalGrid / custom position-layer).
//
// Step 1 leaves both hooks as identity: Modifier is returned unchanged, and
// ContainerDecision.default signals "let the legacy renderer decide." This
// keeps the Phase 7 rollout zero-behavioral-change until step 2 starts
// populating FlexContainer decisions.

/**
 * High-level classification of the Compose container primitive the renderer
 * should use for this component. The enum is intentionally coarse — the
 * finer details (arrangement, alignment, scroll behaviour, etc.) live on
 * the enclosing [ContainerDecision] struct so they can be expanded per
 * category without churning this enum.
 */
enum class ContainerKind {
    /** Step 1 sentinel: defer to the legacy [com.styleconverter.test.style.core.renderer.ComponentRenderer] container logic. */
    Legacy,

    /** CSS `display: flex` or `inline-flex` — render via Row/Column + Arrangement. */
    Flex,

    /** CSS `display: grid` or `inline-grid` — render via LazyVerticalGrid / GridRenderer. */
    Grid,

    /** CSS `display: block | inline-block | inline | flow-root`. */
    Block,

    /** CSS `display: none` — suppress render. */
    None
}

/**
 * Decision payload returned by [LayoutApplier.containerDecision]. Encapsulates
 * everything the ComponentRenderer needs to pick + configure its container
 * primitive.
 *
 * Step 1: every field is null and [kind] is [ContainerKind.Legacy]. The
 * ComponentRenderer consumer falls through to the existing logic.
 *
 * Later steps populate:
 *   - flexbox step: kind = Flex + arrangement + alignment
 *   - grid step: kind = Grid + gridTemplate
 *   - position step: a wrapping layer is applied around whatever container
 *     the flex/grid step picks (composition, not replacement).
 */
data class ContainerDecision(
    val kind: ContainerKind = ContainerKind.Legacy,
    // Placeholder slots — filled in by later phase steps. Typed as Any? so
    // step 1 doesn't have to import the Compose Arrangement / Alignment
    // types prematurely; step 2 will tighten these.
    val arrangement: Any? = null,
    val alignment: Any? = null
) {
    companion object {
        /**
         * Step 1 default — instructs the renderer to keep using the legacy
         * path. All follow-up steps compare against this instance to detect
         * "no engine override" cheaply.
         */
        val default: ContainerDecision = ContainerDecision()
    }
}

/**
 * Applier for layout configs. Owns both sides of the Phase 7 contract:
 *   - [childModifier]     — contributions to the component's own Modifier
 *     (zIndex, alignSelf, order, position-relative offsets).
 *   - [containerDecision] — how the component should render ITS CHILDREN
 *     (flex row/column, grid, legacy fallback).
 *
 * Step 1: both are identity / default.
 */
object LayoutApplier {

    /**
     * Build the Compose [Modifier] contribution for child-level layout
     * properties (zIndex, alignSelf, order, position inset when
     * position=relative/sticky, etc.).
     *
     * Step 1: identity. The legacy [com.styleconverter.test.style.StyleApplier]
     * still produces the real modifier chain because every layout property
     * stays on its legacy path until step 6 reconciliation.
     */
    fun childModifier(config: LayoutConfig): Modifier {
        // Phase 7b: wire z-index + position offsets via the position sub-applier.
        // alignSelf / order remain on the legacy StyleApplier path — the flexbox
        // agent owns those and chains them separately. Composition order:
        //   [position modifiers] .then([future alignSelf/order])
        // which keeps z-index established BEFORE any offset applied by the
        // legacy chain (identical visual stacking either way).
        return com.styleconverter.test.style.layout.position.PositionLayoutApplier
            .childModifier(config)
    }

    /**
     * Decide which Compose container to render children with.
     *
     * Step 1: always returns [ContainerDecision.default] so the
     * ComponentRenderer falls through to its existing dispatch.
     */
    fun containerDecision(config: LayoutConfig): ContainerDecision {
        // Phase 7b step 2: consult the flexbox sub-applier first. It returns
        // null when the component is NOT a flex container, letting us fall
        // through to the legacy renderer path (ContainerDecision.default).
        val flex = com.styleconverter.test.style.layout.flexbox.FlexboxApplier.decide(config)
        if (flex != null) {
            val kind = when (flex.kind) {
                // display: none is owned by the flex applier's None kind —
                // map to the engine-level None so ComponentRenderer suppresses.
                com.styleconverter.test.style.layout.flexbox.FlexContainerKind.None ->
                    ContainerKind.None
                // Everything else is a real flex container.
                else -> ContainerKind.Flex
            }
            // Stash the FlexDecision in the `arrangement` slot. `arrangement`
            // is typed Any? precisely so each sub-applier can carry its own
            // payload without introducing new fields on ContainerDecision.
            return ContainerDecision(kind = kind, arrangement = flex)
        }
        // Phase 7b: grid container decision. We classify as Grid when the
        // display keyword is grid/inline-grid OR any grid-template-* field is
        // populated (matches CSS's "implicit grid container" behaviour — a
        // component with grid-template-columns but no display keyword still
        // establishes a grid context in several browser implementations; we
        // model that loosely here).
        if (com.styleconverter.test.style.layout.grid.GridLayoutApplier.isGridContainer(config)) {
            // Stash the container LayoutConfig on `arrangement` for future
            // GridContainer dispatch. Downstream consumers read it back from
            // there — matching the flex branch's convention above.
            return ContainerDecision(kind = ContainerKind.Grid, arrangement = config)
        }
        // TODO(phase7/step4+): advanced / root branches go here if/when the
        // engine starts owning them (anchor positioning, motion path, etc.).
        return ContainerDecision.default
    }
}
