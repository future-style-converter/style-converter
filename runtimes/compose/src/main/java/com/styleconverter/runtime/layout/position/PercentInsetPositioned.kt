package com.styleconverter.runtime.layout.position

// Wave 49 (lane A7) — the COMPOSED half of the percentage-inset lane
// (CSS 2.1 §9.4.3 read through css-position-3 §"Relative Positioning").
//
// Split out of [PositionApplier] purely for the per-file size rule in
// CLAUDE.md ("target ≤200 lines; split past ~300"): folding the composed
// detour inline pushed PositionApplier.kt past 300. The behaviour is
// byte-identical to the inline form — this file is the same statements
// under a different name, and PositionApplier still owns the geometry
// chain both branches run ([PositionApplier.applyResolvedPosition]), so
// the resolved and unresolved paths can never drift apart.
//
// WHY a `composed { }` detour at all: a percentage inset resolves against
// the containing block's corresponding dimension, and that number lives
// on a CompositionLocal (`LocalContainingBlock`, published by
// `DynamicValueResolver.childContainingBlock`). `PositionApplier
// .applyPosition` is a plain function called from the static style chain
// and has no composition scope, so it cannot read it. `Modifier.composed`
// is the documented Compose escape hatch for a modifier factory that
// needs composition (androidx.compose.ui.composed) and is exactly the
// precedent MarginApplier's percent-margin lane already sets in this
// runtime.
//
// See [PercentInsetResolve] for the CSS citation, the wire discriminator
// (a BARE JSON NUMBER is the converter's percentage encoding) and the
// enumerated blast radius — 10 properties across 7 css-position tests in
// all 30 frozen wave-48 sections.

// The modifier type this extension both receives and returns.
import androidx.compose.ui.Modifier
// The composition escape hatch documented above.
import androidx.compose.ui.composed

/**
 * Run [config]'s positioning chain with its PERCENTAGE insets resolved
 * against the live containing block.
 *
 * Called only when [PositionConfig.percent] is populated — i.e. when at
 * least one inset arrived on the wire as a bare number — so every
 * px-inset component keeps the pre-wave-49 modifier chain untouched and
 * pays nothing for this file existing.
 */
internal fun Modifier.percentInsetPositioned(config: PositionConfig): Modifier = composed {
    // WPT-capture gated for the same reason the spacing appliers gate:
    // the committed baselines contain percent insets too
    // (fixtures/properties/layout/position-top-left.json,
    // inset-logical.json, fidelity/layout.combos.json) and were frozen
    // against the number-as-pixels reading. Outside WPT capture the
    // legacy value is kept, so those baselines stay byte-identical by
    // construction rather than by re-measurement.
    val resolved =
        if (com.styleconverter.runtime.core.renderer.LocalWptCaptureMode.current) {
            PercentInsetResolve.resolve(
                config,
                // THIS element's own containing block (CSS 2.1 §10.1), not
                // the one it publishes for its children.
                //
                // Wave 50 (lane B2) — the LEVEL fix. A `composed` factory
                // materialises inside ComponentRenderer.RenderComponentContent,
                // which already runs INSIDE this component's own
                // `LocalContainingBlock provides childContainingBlock`, so the
                // ambient read below is the block this element establishes for
                // its CHILDREN — one level too deep. That is why
                // wave49-final css-position/position-relative-006 android stayed
                // f 0.9966 after the wave-49 repair: the child's own published
                // block is (100, 100), so `-10000%` resolved to the same
                // -10000 px the legacy number-as-pixels path produced.
                // [ElementContainingBlock] is the correctly-levelled channel;
                // the ambient value is kept only as the frozen-behaviour
                // fallback until the renderer seam publishes it, and the
                // fallback leaves a PropertyTracker breadcrumb.
                ElementContainingBlock.containingBlockFor(
                    element = ElementContainingBlock.LocalElementContainingBlock.current,
                    ambient = com.styleconverter.runtime.core.variables
                        .LocalContainingBlock.current,
                ),
            )
        } else {
            config
        }
    // `Modifier` (not the receiver) because `composed` already chains
    // this factory's result onto the receiver — passing the receiver in
    // would apply the whole chain twice.
    PositionApplier.applyResolvedPosition(Modifier, resolved)
}
