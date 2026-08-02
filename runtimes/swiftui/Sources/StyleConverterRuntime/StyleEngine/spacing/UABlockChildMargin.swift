//
//  UABlockChildMargin.swift
//  StyleEngine/spacing — wave 25, lane UAM (BD-RC3): the UA default block
//  margins of block-level DESCENDANTS, folded into the CSS 2.1 §8.3.1
//  collapse plan the block child stack already builds.
//
//  ## Why this exists
//  TITAN Round 4 taught the natives the UA block-margin table for composed
//  capture ROOTS only (UABlockMargin.swift + ComposedRootStack.swift; the
//  Kotlin twins in apps/android-harness + the runtime). A `<p>` that is a
//  CHILD of another block never went through that fold, so the natives
//  rendered prose FLUSH against its container where the Chromium
//  browser-ref (tools/titan/capture-browser-ref.mjs, UA sheet intact)
//  gives it 1em. Measured on a probe page that mirrors the ref methodology
//  (16px root, headless Chromium): `<div><p></p><p></p></div>` puts the
//  first bar 16px below the div's own top edge position and 16px between
//  the bars — the natives put both flush, i.e. every paragraph rendered
//  16px too high, compounding down the document.
//
//  ## What this module is
//  A PURE per-edge merge that MarginCollapse.containerPlan calls for each
//  child before folding: the UA default for the child's `meta.sourceTag`
//  on any block edge the IR leaves UNDECLARED, and the child's DECLARED px
//  on any edge it does declare (author > UA — the UA origin is the
//  lowest-priority cascade origin, css-cascade-4 §6.1). Everything
//  downstream is unchanged: the interior max() collapse, the parent-edge
//  hoist gates, and the per-child override channel already implement
//  §8.3.1 (MarginCollapse) and simply see richer edges.
//
//  ## Byte-parallel twin
//  runtimes/compose/.../runtime/spacing/UaBlockChildMargins.kt — the
//  shared U1-U12 pin table asserts IDENTICAL expected values on both
//  natives so the two emulations cannot drift.
//
//  ## Dark-stage 327 protection
//  Every entry point takes an explicit `enabled` flag and returns the
//  caller's declared edges VERBATIM when it is false. The renderers pass
//  their ambient WPT-capture flag (iOS `wptCaptureMode`, Compose
//  `LocalWptCaptureMode`), which is false on every property-fixture path —
//  so the 327 committed baselines route through the identity branch and
//  stay byte-identical. The fidelity fixtures declare their own margins,
//  which the merge below hands back unchanged on the declared edge anyway.
//

// CoreGraphics for the CGFloat edge tuples (same import as UABlockMargin).
import CoreGraphics

public extension UABlockMargin {

    /// The UA stylesheet's default VERTICAL (block-axis) margins for a
    /// CHILD's source tag — the very same table `vertical(forTag:)` pins
    /// for composed ROOTS, reused verbatim so a root and a descendant can
    /// never disagree about what a `<p>` margin is worth. (The Kotlin side
    /// needs an explicit re-export because its root table lives in the
    /// harness module; here one function serves both, and the shared U1-U8
    /// pin table asserts the two platforms produce identical numbers.)
    ///
    /// Unknown / flow-container tags (div, section, article, header,
    /// footer, main, nav, aside, li, …) get (0, 0): the UA sheet declares
    /// no block margin for them.
    static func childVertical(forTag tag: String?) -> (top: CGFloat, bottom: CGFloat) {
        // One table, one owner — see UABlockMargin.vertical(forTag:) for
        // the per-tag derivation (HTML §15.3.3-15.3.7 / CSS 2.1 App. D.2).
        vertical(forTag: tag)
    }

    /// Merge one in-flow block child's DECLARED static block margins with
    /// the UA default for its source tag — the per-edge cascade the
    /// browser runs.
    ///
    /// Per edge: an IR-declared margin WINS (author origin beats the UA
    /// origin, css-cascade-4 §6.1) and passes through verbatim; an
    /// UNDECLARED edge takes the UA default. Measured against the ref
    /// probe (case E): `<p style="margin-top:40px">` inside an unpadded
    /// div renders a 40px top gap and keeps its 16px UA BOTTOM — the two
    /// edges cascade independently, exactly as this merge does.
    ///
    /// - Parameters:
    ///   - tag: the child's `meta.sourceTag`.
    ///   - properties: the child's declared IR properties — the
    ///     declaration test is TYPE-NAME based (declaresBlockMarginTop /
    ///     Bottom) because MarginConfig stores a non-optional 0 for an
    ///     undeclared edge and so cannot answer "was this declared?".
    ///   - declared: the child's static block margins as
    ///     MarginCollapse.staticVerticalEdges resolved them (0 on an
    ///     undeclared edge).
    ///   - enabled: false → return `declared` VERBATIM (the dark-stage
    ///     327 identity branch; see the file header).
    static func childBlockEdges(tag: String?,
                                properties: [IRProperty],
                                declared: (top: CGFloat, bottom: CGFloat),
                                enabled: Bool) -> (top: CGFloat, bottom: CGFloat) {
        // Identity branch: no UA emulation outside WPT capture — the
        // property-fixture pipeline's plans stay bit-for-bit as they were.
        guard enabled else { return declared }
        // The tag's UA defaults ((0,0) for divs and unknown tags — the
        // common case, which then also returns `declared` unchanged).
        let ua = childVertical(forTag: tag)
        return (
            // Top edge: declared wins, else the UA default.
            top: declaresBlockMarginTop(properties) ? declared.top : ua.top,
            // Bottom edge: same cascade, resolved independently.
            bottom: declaresBlockMarginBottom(properties) ? declared.bottom : ua.bottom
        )
    }
}
