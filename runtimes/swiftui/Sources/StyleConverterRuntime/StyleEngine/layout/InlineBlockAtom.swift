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
//
// ## Wave 34 (lane H) — the two follow-ups wave 33 named, taken
//  H2 · B5 is now box-sizing-aware (`borderBoxBandsPx`): under
//      `box-sizing: border-box` the wire px IS the outer border box, so
//      the bands need no zero gate at all, and under `content-box` they
//      are ADDED (css-sizing-3 §3). backdrop-filter-clip-rect-2's two
//      3-box rows are admitted by that alone. Every site wave 33 already
//      admitted has all-zero bands, where both branches add 0 — so their
//      AtomSpecs are byte-identical (source-scan pin below).
//  H1 · The composed-canvas ROOT flow moved to the harnesses, driven by
//      the `rootBox`/`rootSegments`/`rootRowPlan` facade at the bottom of
//      this file. CSS2/abspos/static-inside-inline-block is the target;
//      the facade is scalar-only and PUBLIC so the iOS harness can reach
//      it across the SwiftPM module boundary without widening
//      InlineAtomFlow's or UAWidgetIntrinsics' access.
//
// ## Source-scan pin — the wave-34 blast radius, re-enumerated
// Replaying both gate tables over all 324 frozen wave33-final per-test
// IRs (every parent, plus the document-root pseudo-parent) moves exactly
// two tests and nothing else:
//   • filter-effects/backdrop-filter-clip-rect-2 — two NEW 3-box runs
//     under its two wrapper roots (H2, the runtime block child loop);
//   • css-anchor-position/anchor-center-overflow-005 — two NEW 4-box
//     runs at ROOT level (H2 admits them, H1 flows them).
// CSS2/abspos/static-inside-inline-block's root run existed under wave
// 33's gate too and only H1 consumes it; css-ui/appearance-auto-non-html-
// namespace-001 and filter-effects/backdrop-filter-boundary keep the
// exact runs and the exact per-member (w,h) they had.

// public: the composed WPT canvas in apps/ios-harness
// (Screenshot/ComposedRootInlineFlow.swift) drives the document-ROOT row
// flow through the scalar facade at the bottom of this enum, and that
// harness is a separate module from this SwiftPM package. Same precedent
// as FixedHoist, which wave 17 made public for the same canvas.
public enum InlineBlockAtom {

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
    ///  - B5 (no margins; bands read through `box-sizing`): every margin
    ///    must be absent or an explicit zero — a non-zero one would need
    ///    the margin-box packing channel (which exists, the UA checkbox
    ///    uses it) and no corpus site needs it. PADDING and BORDER are
    ///    read through `borderBoxBandsPx` instead of being forced to zero
    ///    (wave-34 lane H, H2): css-sizing-3 §3 says what the wire px
    ///    MEANS, so the atom's outer border box is derivable either way —
    ///    `border-box` puts the bands INSIDE the wire px (nothing to
    ///    add), `content-box` puts them outside (add them). Wave 33
    ///    forced every band to zero, which is the same answer on every
    ///    site it admitted (all-zero bands ⇒ both branches add 0) but
    ///    refused filter-effects/backdrop-filter-clip-rect-2's three
    ///    10px-bordered `border-box` boxes. See `borderBoxBandsPx` for
    ///    the refusals the content-box branch keeps.
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
        // B5 (margins half) — every margin must be absent or explicit zero.
        for t in zeroMargins {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]) else { return nil }
            if px != 0 { return nil }
        }
        // B2 — a definite size on BOTH axes, or no atom.
        guard let w = exactPx(properties, inlineSize),
              let h = exactPx(properties, blockSize) else { return nil }
        // B5 (bands half, wave-34 H2) — how much padding+border the wire
        // px does NOT already contain, per css-sizing-3 §3. nil refuses.
        guard let bands = borderBoxBandsPx(properties) else { return nil }
        // §10.8.1's no-line-box case: baseline == bottom margin edge, so
        // the descent is 0 and the row's own strut supplies the 4px that
        // sits below a row of tall inline-blocks in the ref.
        return UAWidgetIntrinsics.AtomSpec(fixedWpx: w + bands.x,
                                           fixedHpx: h + bands.y,
                                           descentPx: 0.0)
    }

    /// Wave-34 lane H (H2) — the (inline, block) px this box's padding +
    /// border ADD to the wire `Width`/`Height` to reach its OUTER BORDER
    /// BOX, or nil when the arithmetic is not statically knowable and the
    /// atom must refuse.
    ///
    /// css-sizing-3 §3 (`box-sizing`) is the whole rule:
    ///
    ///  - `border-box` — the declared size ALREADY is the border box; the
    ///    bands live inside it. Returns (0, 0) whatever the bands are,
    ///    including shapes this file cannot read (a `%` padding still
    ///    leaves the outer box at the declared px). This is what admits
    ///    filter-effects/backdrop-filter-clip-rect-2, whose six boxes are
    ///    `box-sizing: border-box; width: 100px; border: 10px`.
    ///  - `content-box` (declared, or UNSET — this predicate is reachable
    ///    only from the composed-WPT capture, where
    ///    `SizeApplier.effectiveBoxSizing(declared: nil, wptCaptureMode:
    ///    true)` resolves the unset slot to `.contentBox`, css-sizing-3
    ///    §3's initial value) — the outer box is the declared size PLUS
    ///    the bands, the same sum `StyleBuilder.contentBoxInflation`
    ///    feeds the renderer's frame, so the exact proposal the packer
    ///    hands a member equals the frame that member paints.
    ///  - anything else on the wire is drift → refuse rather than guess.
    ///
    /// The content-box branch keeps three deliberate refusals, each one a
    /// place where a wrong guess would silently mis-size a row:
    ///   1. a band whose value is not EXACT px (%, `em`, `calc`) — the
    ///      basis is a containing-block question this pure rule cannot
    ///      answer (css-sizing-3 §5.2.1 resolves it against ZERO in
    ///      intrinsic sizing, which is a measure-time decision);
    ///   2. any LOGICAL band (`Padding*Inline*`, `Border*Block*Width`)
    ///      that is not an explicit zero — mapping logical to physical is
    ///      the writing-mode-aware spacing extractor's job (css-logical-1
    ///      §4.1), and double-counting a side against its physical twin
    ///      would be worse than refusing;
    ///   3. a side that declares a VISIBLE `border-*-style` but no
    ///      width — CSS Backgrounds 3 §3.3 makes that the `medium`
    ///      initial, and the two natives disagree about it today (Compose
    ///      `SizingExtractor.contentBoxInflation` bands it at 0, iOS
    ///      `BorderSideConfig.effectiveWidth` at 3), so this rule declines
    ///      to pick a winner.
    /// A side whose `border-*-style` is `none`/`hidden` bands at ZERO
    /// (CSS 2.1 §8.5.3 — used width 0), matching `BorderSideConfig
    /// .hasBorder` on both natives; clip-rect-2's `border-style: none`
    /// box is exactly that case.
    private static func borderBoxBandsPx(
        _ properties: [IRProperty]
    ) -> (x: Double, y: Double)? {
        // The box-sizing tri-state, read from the wire like every other
        // keyword in this file.
        switch lastKeyword(properties, "BoxSizing") {
        // Bands are inside the declared px — nothing to add, and nothing
        // about them needs to be readable.
        case "BORDER_BOX": return (0, 0)
        // Declared content-box, or unset (⇒ content-box under P19's
        // composed-WPT gate — see the doc). Fall through.
        case "CONTENT_BOX", nil: break
        // Wire drift: never guess a sizing model.
        default: return nil
        }
        // Refusal 2 — logical bands must be absent or an explicit zero.
        for t in logicalBands {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]) else { return nil }
            if px != 0 { return nil }
        }
        // Inline-axis and block-axis accumulators (px).
        var x = 0.0
        var y = 0.0
        // Physical padding: refusal 1 on any non-exact shape. CSS 2.1
        // §8.4 forbids a negative padding, so the used value is floored
        // at 0 exactly like the extractors' `coerceAtLeast(0)`.
        for (t, horizontal) in physicalPadding {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]) else { return nil }
            if horizontal { x += max(0, px) } else { y += max(0, px) }
        }
        // Physical border: §8.5.3's style gate first, then the width.
        for (widthType, styleType, horizontal) in physicalBorders {
            // `none`/`hidden` ⇒ used width 0 whatever the declared width.
            let style = lastKeyword(properties, styleType)
            if style == "NONE" || style == "HIDDEN" { continue }
            guard properties.last(where: { $0.type == widthType })?.data != nil else {
                // No width declared: initial border-style is `none`, so an
                // undeclared style means no border at all (band 0). A
                // DECLARED visible style is refusal 3.
                if style == nil { continue } else { return nil }
            }
            guard let px = exactPx(properties, [widthType]) else { return nil }
            if horizontal { x += max(0, px) } else { y += max(0, px) }
        }
        return (x, y)
    }

    /// Physical + logical spellings of the inline axis (LTR horizontal-tb,
    /// the engine's normalization — css-logical-1 §4.1).
    private static let inlineSize = ["Width", "InlineSize"]

    /// Physical + logical spellings of the block axis.
    private static let blockSize = ["Height", "BlockSize"]

    /// B5's absent-or-zero MARGINS — the packing is margin-box-free, and
    /// §10.8.1's "baseline at the bottom MARGIN edge" only equals the
    /// border-box bottom while the bottom margin is zero.
    private static let zeroMargins = [
        "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
    ]

    /// Refusal 2's logical bands (see `borderBoxBandsPx`) — absent or an
    /// explicit zero, because their physical mapping is writing-mode
    /// dependent (css-logical-1 §4.1) and this rule does not model it.
    private static let logicalBands = [
        "PaddingBlockStart", "PaddingBlockEnd",
        "PaddingInlineStart", "PaddingInlineEnd",
        "BorderBlockStartWidth", "BorderBlockEndWidth",
        "BorderInlineStartWidth", "BorderInlineEndWidth",
    ]

    /// Physical padding sides as (IR type, is-inline-axis) — the
    /// content-box inflation terms of css-sizing-3 §3.
    private static let physicalPadding: [(String, Bool)] = [
        ("PaddingLeft", true), ("PaddingRight", true),
        ("PaddingTop", false), ("PaddingBottom", false),
    ]

    /// Physical border sides as (width type, style type, is-inline-axis).
    /// The style rides along because CSS 2.1 §8.5.3 zeroes the used width
    /// of a `none`/`hidden` side.
    private static let physicalBorders: [(String, String, Bool)] = [
        ("BorderLeftWidth", "BorderLeftStyle", true),
        ("BorderRightWidth", "BorderRightStyle", true),
        ("BorderTopWidth", "BorderTopStyle", false),
        ("BorderBottomWidth", "BorderBottomStyle", false),
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

    // ─────────────────── wave-34 lane H (H1): the ROOT flow facade ──────
    //
    // The composed WPT canvas stacks DOCUMENT ROOTS in the harness's own
    // Column/VStack (apps/android-harness ScreenshotCaptureScreen's
    // `roots.forEachIndexed`, apps/ios-harness CaptureCanvas's root
    // ForEach). That container is not a runtime block box, so the block
    // child loop's inline-atom lane above never sees it, and three
    // `display: inline-block` roots that belong on ONE line box
    // (CSS 2.1 §9.4.2) were block-STACKED — the measured
    // CSS2/abspos/static-inside-inline-block defect (iOS 0.9088 /
    // Android 0.8965 against a web that renders it natively at 0.9803).
    //
    // The fix is a harness change, but the RULES must not be re-derived
    // there: these three functions are the one decision both harnesses
    // consume. They deliberately speak in SCALARS only (Double / Int /
    // Bool), never in `UAWidgetIntrinsics.AtomSpec` or
    // `InlineAtomFlow.Segment`, because this twin has to cross the
    // SwiftPM module boundary into apps/ios-harness and those types are
    // module-internal — a scalar facade keeps the widening to this one
    // file instead of three.
    //
    // Everything below is a thin wrapper over machinery that already
    // exists: `spec` decides membership, `InlineAtomFlow.segment` finds
    // the runs, `InlineAtomFlow.layout` packs them. No new layout rule.

    /// One root's OUTER border box, in CSS px — the scalar projection of
    /// the `UAWidgetIntrinsics.AtomSpec` `spec` returns.
    public struct RootBox: Equatable {
        public let widthPx: Double
        public let heightPx: Double
        // public: the harness constructs these in its own unit pins.
        public init(widthPx: Double, heightPx: Double) {
            self.widthPx = widthPx
            self.heightPx = heightPx
        }
    }

    /// One segment of the composed root list: a packable inline RUN
    /// (`isRun`, always ≥2 root indices) or a single root that keeps the
    /// frozen block-stacked path. Mirrors `InlineAtomFlow.Segment`.
    public struct RootSegment: Equatable {
        public let indices: [Int]
        public let isRun: Bool
        // public: memberwise init for the harness + its XCTest pins.
        public init(indices: [Int], isRun: Bool) {
            self.indices = indices
            self.isRun = isRun
        }
    }

    /// A packed run's geometry, run-relative CSS px. Mirrors
    /// `InlineAtomFlow.FlowLayout`.
    public struct RootRowPlan: Equatable {
        public let xPx: [Double]
        public let yPx: [Double]
        public let widthPx: Double
        public let heightPx: Double
    }

    /// The collapsed source white-space between two inline-level roots —
    /// the Inter space advance the wave-20 packer pins. Re-exported so a
    /// harness adapter can reach it without `UAWidgetIntrinsics`.
    public static let rootAtomGapPx: Double = UAWidgetIntrinsics.atomGapPx

    /// H1 — this root's `RootBox`, or nil when it is not a declared
    /// inline-block atom and must keep the harness's block stack.
    /// Verbatim `spec` under the covers, so a root and a nested child are
    /// admitted by the IDENTICAL B1–B7 table (including the wave-34 H2
    /// box-sizing read) and can never drift.
    ///
    /// `containerDeclaresLineHeight` is B7 over the composed canvas's own
    /// container: the harness passes whether the document's `body-root`
    /// declares a `line-height`, because the 16/4 strut pins are solved
    /// for the ref's INJECTED body line box (the `-lh-` term in the
    /// frozen ref-corpus id) and an author override invalidates them —
    /// the same reason absolute-tables-013's `line-height: 0` `<td>`
    /// refuses in the nested lane.
    public static func rootBox(
        properties: [IRProperty],
        hasOwnText: Bool,
        hasOwnRuns: Bool,
        containerDeclaresLineHeight: Bool
    ) -> RootBox? {
        guard let s = spec(properties: properties,
                           hasOwnText: hasOwnText,
                           hasOwnRuns: hasOwnRuns,
                           containerDeclaresLineHeight: containerDeclaresLineHeight)
        else { return nil }
        // B2 guarantees both axes are definite; the guard keeps a future
        // spec variant from crashing a capture.
        guard let w = s.fixedWpx, let h = s.fixedHpx else { return nil }
        return RootBox(widthPx: w, heightPx: h)
    }

    /// H1 — split the composed root list into inline runs and singles, or
    /// nil when no run survives (the harness then executes its frozen
    /// stacking loop verbatim, which is what keeps every run-free
    /// document byte-identical).
    ///
    /// `boxes` is `rootBox` per IN-FLOW root, index-aligned with the
    /// harness's own root array. `blockGapsAbovePx` is that harness's
    /// per-root collapsed §8.3.1 gap (`spacing.leading[i]` on iOS,
    /// `rootGaps[i]` on Compose) — read for H3 only:
    ///
    ///  - H3 (no block gap may be swallowed): a run's INTERIOR gaps stop
    ///    existing, because inline-level siblings on one line box have no
    ///    block-flow gap between them (§9.4.2). That is right when those
    ///    gaps are zero, which they are at every corpus site — B5 already
    ///    forces the members' own margins to zero, and the members carry
    ///    no UA-margin tag. If a member ever DID arrive with a non-zero
    ///    gap above it, dropping it would silently lose layout, so the
    ///    whole run refuses and the frozen stack renders instead. The gap
    ///    above the run's FIRST member is kept by the caller and is never
    ///    part of this test.
    public static func rootSegments(
        boxes: [RootBox?],
        blockGapsAbovePx: [Double]
    ) -> [RootSegment]? {
        // Membership flags straight from the shared predicate's output.
        let flags = boxes.map { $0 != nil }
        // The wave-20 greedy segmenter — ≥2 consecutive atoms make a run.
        let segments = InlineAtomFlow.segment(flags)
            .map { RootSegment(indices: $0.indices, isRun: $0.isRun) }
        // H3 — a run whose interior carries a real block gap refuses; it
        // degrades to singles so the harness stacks it exactly as before.
        let gated = segments.map { seg -> RootSegment in
            guard seg.isRun else { return seg }
            let interiorGap = seg.indices.dropFirst().contains {
                ($0 < blockGapsAbovePx.count ? blockGapsAbovePx[$0] : 0) != 0
            }
            return interiorGap ? RootSegment(indices: seg.indices, isRun: false) : seg
        }
        // A refused run becomes ONE multi-index single; expand it so the
        // caller's per-root path sees the same one-index segments it
        // would have seen with no plan at all.
        let expanded = gated.flatMap { seg -> [RootSegment] in
            seg.isRun ? [seg] : seg.indices.map { RootSegment(indices: [$0], isRun: false) }
        }
        // No surviving run ⇒ no plan ⇒ the harness keeps its frozen loop.
        return expanded.contains(where: { $0.isRun }) ? expanded : nil
    }

    /// H1 — pack one run into §9.4.2 rows. Pure delegation to the wave-20
    /// packer with the inline-block family's fixed answers: descent 0
    /// (§10.8.1's no-line-box case, B6) and no margins (B5), plus the
    /// §10.8.1 strut pins every composed row is floored at.
    ///
    /// `widthsPx`/`heightsPx` are the members' measured border boxes in
    /// the caller's own pixel space, `availableWidthPx` the container's
    /// content width (the composed canvas's 358), `gapPx` the collapsed
    /// white-space advance in that same space (`rootAtomGapPx`, which iOS
    /// passes verbatim because CSS px == pt at the capture scale).
    public static func rootRowPlan(
        widthsPx: [Double],
        heightsPx: [Double],
        availableWidthPx: Double,
        gapPx: Double
    ) -> RootRowPlan {
        let plan = InlineAtomFlow.layout(
            widths: widthsPx,
            heights: heightsPx,
            // B6 — baseline at the bottom margin edge ⇒ descent 0, which
            // is what leaves the strut's 4px under a row of tall boxes.
            descents: widthsPx.map { _ in 0.0 },
            // B5 — admitted atoms have zero margins on both sides.
            marginStarts: widthsPx.map { _ in 0.0 },
            marginEnds: widthsPx.map { _ in 0.0 },
            availableWidth: availableWidthPx,
            gapPx: gapPx,
            // P17b — the §10.8.1 line-box strut of the composed canvas's
            // 20px line box (the ref's injected `line-height`).
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
        return RootRowPlan(xPx: plan.x, yPx: plan.y,
                           widthPx: plan.width, heightPx: plan.height)
    }
}
