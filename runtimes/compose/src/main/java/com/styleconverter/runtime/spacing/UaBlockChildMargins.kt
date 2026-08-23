package com.styleconverter.runtime.spacing

// UaBlockChildMargins — wave 25, lane UAM (BD-RC3): the UA default block
// margins of block-level DESCENDANTS, folded into the CSS 2.1 §8.3.1
// collapse plan the block child stack already builds.
//
// ## Why this exists
// TITAN Round 4 taught the natives the UA block-margin table for composed
// capture ROOTS only (apps/android-harness UaBlockMargins.kt + the root
// stack fold; iOS UABlockMargin.swift + ComposedRootStack.swift). A `<p>`
// that is a CHILD of another block never went through that fold, so the
// natives rendered prose FLUSH against its container where the Chromium
// browser-ref (tools/titan/capture-browser-ref.mjs, UA sheet intact) gives
// it 1em. Measured on a probe page that mirrors the ref methodology (16px
// root, headless Chromium): `<div><p></p><p></p></div>` puts the first bar
// 16px below the div's own top edge position and 16px between the bars —
// the natives put both flush, i.e. every paragraph rendered 16px too high,
// compounding down the document.
//
// ## What this module is
// A PURE per-edge merge that the container's plan builder calls for each
// child before folding: the UA default for the child's `meta.sourceTag`
// (`_tag` on the wire) on any block edge the IR leaves UNDECLARED, and the
// child's DECLARED px on any edge it does declare (author > UA — the UA
// origin is the lowest-priority cascade origin, css-cascade-4 §6.1).
// Everything downstream is unchanged: the interior max() collapse, the
// parent-edge hoist gates, and the per-child override channel all already
// implement §8.3.1 (BlockMarginCollapse) and simply see richer edges.
//
// ## Byte-parallel twin
// runtimes/swiftui/.../StyleEngine/spacing/UABlockChildMargin.swift — the
// shared U1-U12 pin table asserts IDENTICAL expected values on both
// natives so the two emulations cannot drift.
//
// ## Dark-stage 327 protection
// Every entry point takes an explicit `enabled` flag and returns the
// caller's declared edges VERBATIM when it is false. The renderers pass
// their ambient WPT-capture flag (Compose `LocalWptCaptureMode`, iOS
// `wptCaptureMode`), which is false on every property-fixture path — so
// the 327 committed baselines route through the identity branch and stay
// byte-identical. The fidelity fixtures declare their own margins, which
// the merge below hands back unchanged on the declared edge anyway.

/**
 * The UA stylesheet's default VERTICAL (block-axis) margins for a source
 * tag, in px at a 16px root font — the exact numbers the browser-ref's
 * Chromium UA sheet lays out (HTML §15.3.3-15.3.7 / CSS 2.1 Appendix D.2,
 * each `<hN>` em resolving against its OWN font face).
 *
 * This is the ONE Kotlin copy of the table: the harness root-stack model
 * (apps/android-harness UaBlockMargins.uaBlockMargins) reads its vertical
 * numbers from here, so a root and a descendant can never disagree about
 * what a `<p>` margin is worth. Horizontal UA insets (blockquote/figure
 * 40px left+right) stay with the harness model — §8.3.1 collapses the
 * block axis only, so the child fold has no use for them.
 *
 * Unknown / flow-container tags (div, section, article, header, footer,
 * main, nav, aside, li, …) get (0, 0): the UA sheet declares no block
 * margin for them.
 *
 * @return `(top, bottom)` in px.
 */
fun uaVerticalBlockMargins(sourceTag: String?): Pair<Float, Float> =
    when (sourceTag?.lowercase()) {
        // <p>: `margin: 1em 0` → 16px at the 16px root face.
        "p" -> 16f to 16f
        // <h1>: `margin: .67em 0` over a 2em (32px) face → ≈21px.
        "h1" -> 21f to 21f
        // <h2>: `margin: .83em 0` over a 1.5em (24px) face → ≈19px.
        "h2" -> 19f to 19f
        // <h3>: `margin: 1em 0` over a 1.17em (~18.7px) face → ≈16px
        // (the Round-4 ref-calibrated value, kept verbatim).
        "h3" -> 16f to 16f
        // <h4>: `margin: 1.33em 0` over a 1em (16px) face → ≈21px.
        "h4" -> 21f to 21f
        // <h5>: `margin: 1.67em 0` over a .83em (~13.3px) face → ≈27px.
        "h5" -> 27f to 27f
        // <h6>: `margin: 2.33em 0` over a .67em (~10.7px) face → ≈37px.
        "h6" -> 37f to 37f
        // <ul>/<ol>: `margin-block: 1em` → 16px (the left padding is the
        // marker inset — an inline-axis value the block fold ignores).
        "ul", "ol" -> 16f to 16f
        // <blockquote>: `margin: 1em 40px` → 16px block (40px inline).
        "blockquote" -> 16f to 16f
        // <pre>: `margin: 1em 0` → 16px.
        "pre" -> 16f to 16f
        // <figure>: `margin: 1em 40px` → 16px block (40px inline).
        "figure" -> 16f to 16f
        // Every flow container and unrecognised tag: no UA block margin.
        else -> 0f to 0f
    }

/**
 * wave-46 lane Y8 — the same UA margins resolved against the element's
 * OWN computed font-size, the em base css-values-4 §5.1.1 prescribes
 * (HTML §15.3 declares every UA block margin in em; inherit-computed-001's
 * `<p>` with `font-size: larger` gets 1em × 19.2 = 19.2px in the browser
 * -ref, where the fixed table above gave 16 and painted the box 3px high).
 *
 * @param ownFontSizePx the element's computed size
 *   ([UaBlockMarginFontBasis.ownFontSizePx]); NULL = "no own font signal"
 *   → the 16px-root table above VERBATIM, so every existing pin and every
 *   corpus element without a font-size declaration is byte-identical.
 */
fun uaVerticalBlockMargins(sourceTag: String?, ownFontSizePx: Float?): Pair<Float, Float> {
    // Identity rung: no basis → the Round-4 ref-calibrated table.
    if (ownFontSizePx == null) return uaVerticalBlockMargins(sourceTag)
    // The UA sheet's em factor for the tag × the element's own size; tags
    // with no UA block margin stay (0, 0) exactly as in the table.
    val px = UaBlockMarginFontBasis.uaBlockMarginEm(sourceTag)?.times(ownFontSizePx) ?: 0f
    return px to px
}

/**
 * The block-margin-TOP longhand names the wire can carry. The converter
 * fully EXPANDS the `margin` / `margin-block` shorthands (MarginExtractor
 * recognises only the eight longhands), so this two-name set is exactly
 * "the IR declares this edge" — mirrors the root-stack twin's
 * UABlockMargin.declaresBlockMarginTop so root and child agree.
 */
private val UA_CHILD_TOP_TYPES = setOf("MarginTop", "MarginBlockStart")

/** Block-margin-BOTTOM longhands — the bottom-edge twin of the above. */
private val UA_CHILD_BOTTOM_TYPES = setOf("MarginBottom", "MarginBlockEnd")

/** True when the IR declares a block-start margin (physical or logical). */
fun declaresUaChildTop(propertyTypes: Collection<String>): Boolean =
    propertyTypes.any { it in UA_CHILD_TOP_TYPES }

/** True when the IR declares a block-end margin (physical or logical). */
fun declaresUaChildBottom(propertyTypes: Collection<String>): Boolean =
    propertyTypes.any { it in UA_CHILD_BOTTOM_TYPES }

/**
 * Merge one in-flow block child's DECLARED static block margins with the
 * UA default for its source tag — the per-edge cascade the browser runs.
 *
 * Per edge: an IR-declared margin WINS (author origin beats the UA origin,
 * css-cascade-4 §6.1) and is passed through verbatim; an UNDECLARED edge
 * takes the UA default from [uaVerticalBlockMargins]. Measured against the
 * ref probe (case E): `<p style="margin-top:40px">` inside an unpadded
 * div renders a 40px top gap and keeps its 16px UA BOTTOM — the two edges
 * cascade independently, exactly as this merge does.
 *
 * @param sourceTag the child's `_tag` (v2 wire `meta.sourceTag`).
 * @param propertyTypes the child's declared IR property type names.
 * @param declared the child's static block margins as the §8.3.1
 *   classifier resolved them ([BlockMarginCollapse.blockMarginsOrNull]) —
 *   0 on an undeclared edge, which is why the declaration test above is
 *   type-name based rather than value based.
 * @param enabled false → return [declared] VERBATIM (the dark-stage 327
 *   identity branch; see the file header).
 * @param ownFontSizePx wave-46 lane Y8: the child's own computed font-size
 *   for the em basis of its UA margin (see the two-arg
 *   [uaVerticalBlockMargins]); null (every caller today — the renderer's
 *   plan builder has no inheritance channel into this fold yet) keeps the
 *   16px-root table, so no existing plan value moves.
 */
fun uaChildBlockEdges(
    sourceTag: String?,
    propertyTypes: Collection<String>,
    declared: CollapsedMargin,
    enabled: Boolean,
    ownFontSizePx: Float? = null,
): CollapsedMargin {
    // Identity branch: no UA emulation outside WPT capture — the property
    // -fixture pipeline's plans stay bit-for-bit what they were.
    if (!enabled) return declared
    // The tag's UA defaults (0,0 for divs and unknown tags — the common
    // case, which then also returns `declared` unchanged below), resolved
    // against the child's own font basis when the caller has one.
    val (uaTop, uaBottom) = uaVerticalBlockMargins(sourceTag, ownFontSizePx)
    return CollapsedMargin(
        // Top edge: declared wins, else the UA default.
        topPx = if (declaresUaChildTop(propertyTypes)) declared.topPx else uaTop,
        // Bottom edge: same cascade, resolved independently.
        bottomPx = if (declaresUaChildBottom(propertyTypes)) declared.bottomPx else uaBottom,
    )
}
