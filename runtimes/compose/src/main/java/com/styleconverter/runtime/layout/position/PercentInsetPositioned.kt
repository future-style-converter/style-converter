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
                // The ancestor's content box, published one level up by
                // ComponentRenderer from DynamicValueResolver
                // .childContainingBlock. A null axis means "not
                // statically definite" — PercentInsetResolve owns what
                // that means and the guard on it.
                com.styleconverter.runtime.core.variables.LocalContainingBlock.current,
            )
        } else {
            config
        }
    // `Modifier` (not the receiver) because `composed` already chains
    // this factory's result onto the receiver — passing the receiver in
    // would apply the whole chain twice.
    PositionApplier.applyResolvedPosition(Modifier, resolved)
}
