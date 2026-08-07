import Foundation

// Wave 33 (lane C, C2) — the DECLARED `display: inline-block` atom, the
// second family of the wave-20 W3 inline-atom flow. Byte-parallel twin of
// Compose layout/InlineBlockAtom.kt — same names, same B1–B6 gate table.
//
// ## The measured defect
// CSS2/abspos/static-inside-inline-block is three 100×100 inline-block
// siblings that belong on ONE line box (CSS 2.1 §9.4.2), separated by the
// collapsed source white-space. Both natives block-STACKED them — the
// block child loop's VStack/Column has no inline formatting context — so
// the green square landed 100px+ too low and the frozen wave32-final
// scores were iOS 0.9088 FAIL / Android 0.8965 FAIL against a web that
// renders it natively at 0.9803 PASS.
//
// ## Why this file and not a wider inline engine
// Wave 20 already built the machinery: `InlineAtomFlow` is the pure greedy
// §9.4.2 packer (gap, width-exhaustion wrap, §10.8.1 baseline + strut) and
// each native has an adapter over it (`InlineAtomBlockLayout` here,
// InlineFlowLayout on Compose). What wave 20 lacked was a second ATOM
// PREDICATE: its `InlineAtomFlow.isAtom` recognizes only UA form-control
// widgets and text-only anchors, because those were the css-ui rows it was
// solving. An author-declared `display: inline-block` box with a definite
// size is the same KIND of thing — an opaque inline-level box that packs,
// wraps and baseline-aligns identically — it just carries its geometry on
// the wire instead of in the UA table. So this file is a SPEC PRODUCER,
// not a new layout.
//
// ## Blast radius, enumerated before the change
// Scanning all 324 frozen wave32-final per-test IRs for a parent whose
// children contain ≥2 CONSECUTIVE declared-INLINE_BLOCK siblings yields
// exactly 8 tests / 9 sites:
//   • CSS2/abspos/static-inside-inline-block          — the target
//   • filter-effects/backdrop-filter-clip-rect-2      — 3-box row
//   • filter-effects/backdrop-filter-boundary         — 6-box row
//   • css-anchor-position/anchor-center-overflow-005  — 4-box row
//   • css-ui/appearance-auto-non-html-namespace-001   — 6-box row
//   • css-tables/absolute-tables-013 / -014 / -015    — 2 spans in a
//     100px-wide <td>, i.e. the WRAP case (100 + 4.5 + 100 > 100)
// Nothing outside those parents can enter this lane: the gate needs a
// DECLARED `inline-block` keyword, and the converter never serializes UA
// defaults. The segmenter is composed-WPT-capture gated (P19), so all 363 committed dark-stage baselines are byte-identical
// by construction.
//
// ## What it ACTUALLY moves — measured on device, not predicted
// Of those 9 sites the gate table admits exactly ONE, and it is not the
// test that motivated the file. Android emulator-5566, fresh install,
// scored with diffComposedVsRef against the frozen wave32-final refs:
//
//   • filter-effects/backdrop-filter-boundary  0.6130 → 0.8962 (+0.2832).
//     Six 160×90 inline-blocks that belong in one wrapped row. THE WIN.
//   • css-tables/absolute-tables-013/-014/-015 refused by B7 — their
//     `<td>` declares `line-height: 0`, which the strut pins do not
//     model. First measured WITHOUT B7 at −0.0115 / −0.0163 / −0.0165;
//     B7 restores all three byte-for-byte. See B7.
//   • filter-effects/backdrop-filter-clip-rect-2 refused by B5 — its
//     boxes carry a 10px border under `box-sizing: border-box`, so the
//     wire px is not the plain border box this rule reads. Recovering it
//     needs a box-sizing-aware B5; deliberately NOT attempted here.
//   • css-anchor-position/anchor-center-overflow-005 and css-ui/
//     appearance-auto-non-html-namespace-001 measured byte-identical —
//     their boxes paint nothing the packing can move.
//   • CSS2/abspos/static-inside-inline-block — UNFIXED, and this file
//     cannot fix it. Its three inline-blocks are document ROOTS, and the
//     composed WPT canvas stacks roots in the HARNESS's own Column
//     (apps/android-harness ScreenshotCaptureScreen `roots.forEachIndexed`
//     / the iOS twin), which never reaches the runtime's block child
//     loop. Row-flowing document roots is a HARNESS change, in both
//     harnesses, and is left as measured, named future work rather than
//     claimed here.

enum InlineBlockAtom {

    /// The `UAWidgetIntrinsics.AtomSpec` a declared `display:inline-block`
    /// box contributes to an inline atom run — or nil when this box is not
    /// one, which keeps it on the frozen block path.
    ///
    /// The gate table (mirrored on Compose byte-for-byte):
    ///
    ///  - B1 (declared inline-block): `Display` must be the declared
    ///    keyword `INLINE_BLOCK`. A box that is inline-block only by UA
    ///    default (`<input>`, `<button>`, …) belongs to the wave-20 widget
    ///    lane, which owns its geometry table — routing it here would race
    ///    that table with a wire size it does not have.
    ///
    ///  - B2 (definite border-box size): both `Width`/`InlineSize` and
    ///    `Height`/`BlockSize` must read as EXACT px. §10.3.9's
    ///    shrink-to-fit and §10.6.6's content height are content-measure
    ///    questions this pure rule cannot answer; refusing keeps the
    ///    packer's wrap decision (and therefore absolute-tables-013's two
    ///    line boxes) deterministic.
    ///
    ///  - B3 (in flow): a declared `Position` of `absolute` / `fixed`
    ///    takes the box out of the flow (CSS 2.2 §9.3.1) — it is not on the
    ///    line box at all. `static` / `relative` / `sticky` stay.
    ///
    ///  - B4 (not floated): a declared `Float` other than `NONE` makes the
    ///    box block-level (§9.7) and belongs to the wave-19 float packer,
    ///    which runs FIRST and would already own the container.
    ///
    ///  - B5 (no bands, no margins): every padding, border and margin must
    ///    be absent or an explicit zero. Margins would need the margin-box
    ///    packing channel, and a non-zero band would make B2's border-box
    ///    reading depend on `box-sizing`.
    ///
    ///  - B6 (no own line boxes → baseline at the bottom margin edge):
    ///    CSS 2.1 §10.8.1 puts an inline-block's baseline at the baseline of
    ///    its LAST line box, and only at the bottom margin edge when it has
    ///    no in-flow line boxes. Own text / inline runs are the statically
    ///    visible line-box source, so they refuse; an element child is
    ///    allowed (the target test's red box holds an ABSPOS child, which
    ///    generates no line box in its parent). Descent is therefore always
    ///    0.0 — the box bottom IS the baseline, which is what makes the
    ///    ref's 4px strut descent appear below a row of tall inline-blocks.
    ///
    ///  - B7 (the container's line box is the harness default): the packer
    ///    floors every row at `UAWidgetIntrinsics.strutAscentPx` /
    ///    `strutDescentPx`, the 16/4 split of the composed-WPT 20px line box
    ///    (CSS 2.1 §10.8.1's strut). Those pins are solved for the harness's
    ///    DEFAULT line-height; a container that declares its own makes them
    ///    wrong, and this rule does not model the mapping. MEASURED on
    ///    device: css-tables/absolute-tables-013/-014/-015 put two 100×50
    ///    spans in a `<td>` with `line-height: 0`, where the browser's line
    ///    boxes are 50 tall (no strut) — feeding them the 16/4 strut moved
    ///    the second span 4px down and cost 0.0115/0.0163/0.0165 SSIM
    ///    against the frozen wave32-final Android baseline. Refusing
    ///    restores those three byte-for-byte. `containerDeclaresLineHeight`
    ///    is the caller's read of the PARENT's own declaration list.
    static func spec(
        properties: [IRProperty],
        hasOwnText: Bool,
        hasOwnRuns: Bool,
        containerDeclaresLineHeight: Bool
    ) -> UAWidgetIntrinsics.AtomSpec? {
        // B7 — a declared container line-height invalidates the strut pins.
        if containerDeclaresLineHeight { return nil }
        // B1 — the declared keyword is the whole admission ticket.
        guard lastKeyword(properties, "Display") == "INLINE_BLOCK" else { return nil }
        // B3 — out-of-flow boxes never join a line box.
        if let p = lastKeyword(properties, "Position"), p == "ABSOLUTE" || p == "FIXED" {
            return nil
        }
        // B4 — a float is block-level and owned by the wave-19 packer.
        if let f = lastKeyword(properties, "Float"), f != "NONE" { return nil }
        // B6 — own line boxes would move the baseline off the box bottom.
        if hasOwnText || hasOwnRuns { return nil }
        // B5 — every band must be absent or an explicit zero.
        for t in zeroBands {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]) else { return nil }
            if px != 0 { return nil }
        }
        // B2 — a definite border box on BOTH axes, or no atom.
        guard let w = exactPx(properties, inlineSize),
              let h = exactPx(properties, blockSize) else { return nil }
        // §10.8.1's no-line-box case: baseline == bottom margin edge, so
        // the descent is 0 and the row's own strut supplies the 4px that
        // sits below a row of tall inline-blocks in the ref.
        return UAWidgetIntrinsics.AtomSpec(fixedWpx: w, fixedHpx: h, descentPx: 0.0)
    }

    /// Physical + logical spellings of the inline axis (LTR horizontal-tb,
    /// the engine's normalization — css-logical-1 §4.1).
    private static let inlineSize = ["Width", "InlineSize"]

    /// Physical + logical spellings of the block axis.
    private static let blockSize = ["Height", "BlockSize"]

    /// B5's absent-or-zero bands: margins keep the packing margin-free,
    /// padding/border keep B2's px reading box-sizing-independent.
    private static let zeroBands = [
        "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
        "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
        "PaddingBlockStart", "PaddingBlockEnd",
        "PaddingInlineStart", "PaddingInlineEnd",
        "BorderTopWidth", "BorderRightWidth",
        "BorderBottomWidth", "BorderLeftWidth",
        "BorderBlockStartWidth", "BorderBlockEndWidth",
        "BorderInlineStartWidth", "BorderInlineEndWidth",
    ]

    /// The LAST declaration of any of `types`, read as EXACT px — the same
    /// object-with-numeric-`px` shape AbsposCbUsedHeight.exactPx accepts, so
    /// a bare number (the converter's percentage spacing wire) is NOT px.
    private static func exactPx(_ properties: [IRProperty], _ types: [String]) -> Double? {
        guard let data = properties.last(where: { types.contains($0.type) })?.data
        else { return nil }
        return data["px"]?.doubleValue
    }

    /// The LAST declaration of `type` as an UPPERCASE keyword string, or nil
    /// when absent / not a keyword. Handles both the bare-primitive wire
    /// (`"data": "INLINE_BLOCK"`) and the `{"keyword": …}` wrapper.
    private static func lastKeyword(_ properties: [IRProperty], _ type: String) -> String? {
        guard let data = properties.last(where: { $0.type == type })?.data else { return nil }
        let s = data.stringValue ?? data["keyword"]?.stringValue
        return s?.uppercased()
    }
}
