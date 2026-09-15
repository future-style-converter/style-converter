package com.styleconverter.runtime.layout.position

// Wave 50 (lane B2) — THE ELEMENT'S OWN CONTAINING BLOCK, as opposed to the
// containing block the element PUBLISHES for its children.
//
// ## The measured defect this file exists for
// `PercentInsetPositioned` resolves a percentage inset inside a
// `Modifier.composed { }` block, reading `LocalContainingBlock.current`.
// A `composed` factory runs at MATERIALIZATION time — i.e. inside the
// composable that actually hands the modifier to a layout node — and in this
// renderer that composable is `ComponentRenderer.RenderComponentContent`,
// which is invoked from `inheritanceWrappedContent`, i.e. INSIDE this
// component's own
// `CompositionLocalProvider(… LocalContainingBlock provides childContainingBlock …)`.
// So the ambient value a component's OWN modifier chain sees is the
// containing block it establishes for its CHILDREN — one level too deep.
//
// Corpus discriminator (executed, not argued —
// `node tools/titan/results/wave50-B2/census.mjs insets` re-derives it). All
// 30 frozen wave49-final sections hold seven bare-number (percentage) inset
// carriers. The two levels give different containing blocks on six of them,
// but the same USED inset on six — the percentage and the two candidate bases
// coincide. On exactly ONE the used value differs:
//   tools/titan/runs/wave49-final/sections/css-position/per-test-ir/
//     wpt__css-position__position-relative-006.json
//   child `position-relative-006__1__0-214`: Width 100 + Height 100 + Top
//   -10000 (percent wire) → own published cb = (100, 100); the RED parent
//   `…__1-213` declares Width 100 + MIN-height 100 → the cb it publishes for
//   this child = (100, null).
// Reading the OWN cb gives -10000 % × 100 = -10000 px — bit-for-bit the
// legacy number-as-pixels value — and the gate confirms exactly that: the
// wave-49 lane predicted a flip, the Android cell did NOT move, and its score
// is IDENTICAL to the run before the change landed (wave48-final
// css-position/position-relative-006 android f 0.9966 → wave49-final android
// f 0.9966; all seven carriers byte-stable on Android across the two runs).
// Reading the PARENT cb would have resolved the block axis to 0 and moved the
// picture. The frozen captures say the picture did not move:
// `tools/titan/runs/wave49-final/sections/css-position/android-screenshots/
//  wpt__css-position__position-relative-006.png` is a bare RED 100×100 square
// (10 000 novel red px in the manifest's novelInk block) where
// `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/css-position/
//  position-relative-006.png` paints a GREEN one.
//
// That corrects the diagnosis carried in `tools/titan/results/corpus-v6-15.json`
// and docs/BACKLOG.md queue 0(b) ("PercentInsetResolve declines because the
// ancestor publishes no containing block on either axis"): the ancestor DOES
// publish one — (100, null) — and the resolver never sees it. The §10.1
// republish that item proposes is a real and separate modelling gap (it
// governs `position-relative-002`'s `<span>` parent and `-008`'s `<tbody>`),
// but it is NOT what blocks -006, and on the frozen corpus it would move
// nothing: both of those carriers resolve to the same number with or without
// it (see PercentInsetResolveTest's L4/L5 pins).
//
// ## The channel
// [LocalElementContainingBlock] carries the value `LocalContainingBlock` had
// at the TOP of this component's `RenderComponent` — the containing block the
// element itself is laid out in (CSS 2.1 §10.1). ComponentRenderer provides it
// in the same `CompositionLocalProvider` that publishes the child channel, so
// each component's own modifier chain reads its own, and each child re-provides
// its own on the way down. See `tools/titan/results/wave50-B2/` for the seam.
//
// Until that seam is applied the local is `null` and [containingBlockFor]
// falls back to the ambient (wrong-level) read, which is the behaviour every
// committed capture was frozen against — no silent change, and the fallback
// leaves a PropertyTracker breadcrumb rather than passing quietly.

// The channel type: the renderer's content-box record, px == dp in this runtime.
import com.styleconverter.runtime.core.variables.ContainingBlock
// CompositionLocal factory — same `compositionLocalOf` form as
// LocalContainingBlock itself and as CanvasRootHoist's ancestry flags.
import androidx.compose.runtime.compositionLocalOf
// Breadcrumb sink for the fallback leg (CLAUDE.md: no silent fallthroughs).
import com.styleconverter.runtime.PropertyTracker

object ElementContainingBlock {

    /**
     * The containing block THIS element is laid out in — CSS 2.1 §10.1's
     * "content edge of the nearest block container ancestor box", as the
     * renderer modelled it one level up.
     *
     * `null` means "the renderer has not published this channel" (the state
     * of the tree until the ComponentRenderer seam lands, and the state on
     * every path that renders a box without going through RenderComponent —
     * list markers and the widget shims). [containingBlockFor]
     * owns what that null means; nothing else may read this local directly.
     *
     * ONE PATH RE-PROVIDES IT RATHER THAN FALLING BACK (wave-50 lane F4,
     * skeptic S3): `content/ContentApplier.PseudoElement` composes INSIDE the
     * host's own `CompositionLocalProvider`, so the value it would otherwise
     * inherit is the HOST's containing block — one level too SHALLOW for a
     * pseudo, whose box is generated inside the originating element and is
     * therefore laid out in the host's CONTENT box (CSS 2.1 §10.1 /
     * css-pseudo-4 §4). That call site re-provides this local with
     * `LocalContainingBlock.current`, the host's child block. It changes no
     * pixel today — `PseudoBucketExtractor` types only
     * color/font-size/font-family into the pseudo's property list, so no inset
     * ever reaches the composed factory from there (9 components in 2
     * `css-anchor-position` tests declare one and are dropped, named). The
     * level is published so it cannot be wrong the day that extractor widens.
     */
    val LocalElementContainingBlock = compositionLocalOf<ContainingBlock?> { null }

    /**
     * The breadcrumb key the fallback records. Namespaced like the
     * `FontSizeAdjust[metric:…]` precedent so it can never collide with a real
     * IR property type in `PropertyTracker.getReport()`.
     */
    const val UNPUBLISHED_BREADCRUMB = "Inset[containing-block-level-unpublished]"

    /**
     * Pick the containing block a percentage inset must resolve against.
     *
     * @param element the [LocalElementContainingBlock] read — this element's
     *   OWN containing block, or null when the channel is unpublished.
     * @param ambient the `LocalContainingBlock` read at the same point, which
     *   inside a component's own modifier chain is the block it publishes for
     *   its CHILDREN (the level defect in the file header). Used only as the
     *   frozen-behaviour fallback.
     */
    fun containingBlockFor(
        element: ContainingBlock?,
        ambient: ContainingBlock,
    ): ContainingBlock {
        // Published channel: the spec-correct level, used verbatim.
        if (element != null) return element
        // Unpublished: keep the pre-wave-50 reading so no committed capture
        // moves, and record that this element's percentage inset resolved
        // against the wrong level (CLAUDE.md's no-silent-fallthrough rule).
        PropertyTracker.markUnhandled(UNPUBLISHED_BREADCRUMB)
        return ambient
    }
}
