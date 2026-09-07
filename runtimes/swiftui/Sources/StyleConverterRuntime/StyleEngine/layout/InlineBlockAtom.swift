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
//
// ## Wave 44 (lane U3) — the ROOT facade grows two channels the nested
// lane does not have (and `spec` therefore does NOT gain):
//  U3a · a MARGIN channel: `rootBox` tolerates exact-px physical margins
//        (negative included) and carries them on `RootBox`; `rootRowPlan`
//        folds them into the §10.8.1 baseline math by packing MARGIN
//        boxes (baseline = bottom margin edge for both admitted families,
//        CSS 2.1 §10.8.1). This is what admits css-ui box-sizing-010/-014/
//        -016/-020/-024's `#ref` div (`margin-bottom: 30px "for
//        alignement"`) and box-sizing-007..009's twenty margined imgs.
//        The nested lane keeps refusing margins because its AtomSpec has
//        no margin channel and its adapters pack border boxes.
//  U3b · the REPLACED family: a root whose `meta.sourceTag` is a replaced
//        element with a DELIVERED raster (the same family+fact contract as
//        the wave-43 V1 `InlineAtomFlow.isAtom` predicate — candidate tag
//        + src, attested registry decode) is an inline-level atom
//        (CSS 2.1 §9.2.2), sized by §10.3.2/§10.6.2/§10.4 through the
//        wave-42 `ReplacedBoxSizing` table. Undelivered assets refuse, so
//        every pre-raster-OFF arm is byte-identical.
//  B8  · both NEW channels require the declared `vertical-align` to be
//        absent or `baseline` — the packer models §10.8.1 baseline rows
//        only. Scoped to the new admissions: the frozen wave-34
//        zero-margin declared-inline-block family keeps its VA-ignorant
//        admission (css-cascade/scope-pseudo-element's three `vertical-
//        align: top` roots are admitted TODAY and pass; evicting them
//        would be an unmeasured regression).
// Blast radius, re-enumerated over all 1435 frozen wave43-final per-test
// IRs (30 sections under tools/titan/runs/wave43-final/): with the
// pre-raster OFF — now the TITAN_SVG_PRERASTER=0 ESCAPE HATCH, not the
// default — ZERO documents change; with it ON (the wave-44 default, which
// the re-run A/B in tools/titan/svg-preraster.mjs earned: +7 Android /
// +8 iOS passes) exactly 17 css-ui box-sizing tests gain root runs
// (007..011, 013..022, 024, 025 — 012/023's div is not inline-block, so
// their lone img stays a single and keeps the frozen stack). The ONE
// impure seam is `replacedRootFacts(of:)` (DocumentImageRegistry); tests
// pin `rootBox` by constructing the facts directly.

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
        // composed-WPT gate — see the doc): the real bands, strictly read
        // (wave-44 factored the accumulation into `strictBandsPx` so the
        // replaced gate can read the SAME bands byte-for-byte).
        case "CONTENT_BOX", nil: return strictBandsPx(properties)
        // Wire drift: never guess a sizing model.
        default: return nil
        }
    }

    /// The STRICT (inline, block) padding + used-border band read — the
    /// exact wave-34 content-box accumulation, factored out (wave-44
    /// U3b) because the replaced gate needs the REAL bands under either
    /// box-sizing (its content box is declared − band). All three
    /// refusals in `borderBoxBandsPx`'s doc live here unchanged.
    private static func strictBandsPx(
        _ properties: [IRProperty]
    ) -> (x: Double, y: Double)? {
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
    /// the `UAWidgetIntrinsics.AtomSpec` `spec` returns — plus (wave-44
    /// U3a) the DECLARED physical margins the packer now owns. The margin
    /// defaults keep every pre-wave-44 construction site (and pin)
    /// byte-identical: a margin-free box packs exactly as before.
    public struct RootBox: Equatable {
        public let widthPx: Double
        public let heightPx: Double
        // The §8.3 physical margins, exact px off the wire (negative
        // legal — box-sizing-007's `margin-left: -10px`). They live HERE,
        // not in the render: the harness strips them from the member's
        // render (`packedMarginStripped`) and `rootRowPlan` re-expresses
        // them as §10.8.1 margin-box packing, so no margin is ever painted
        // twice or dropped.
        public let marginTopPx: Double
        public let marginRightPx: Double
        public let marginBottomPx: Double
        public let marginLeftPx: Double
        // public: the harness constructs these in its own unit pins.
        public init(widthPx: Double, heightPx: Double,
                    marginTopPx: Double = 0, marginRightPx: Double = 0,
                    marginBottomPx: Double = 0, marginLeftPx: Double = 0) {
            self.widthPx = widthPx
            self.heightPx = heightPx
            self.marginTopPx = marginTopPx
            self.marginRightPx = marginRightPx
            self.marginBottomPx = marginBottomPx
            self.marginLeftPx = marginLeftPx
        }
    }

    /// Wave-44 U3b — the caller's attestation that a root is a DELIVERED
    /// replaced element, mirroring the wave-43 V1 `InlineAtomFlow.isAtom`
    /// family+fact contract: non-nil means "replaced candidate
    /// (`ReplacedImageContent.isCandidate`: replaced `meta.sourceTag` +
    /// non-blank `meta.attrs.src`) AND the platform's
    /// `DocumentImageRegistry` decoded that src to a raster". The facade
    /// still re-checks the TAG family itself — a facts object handed to a
    /// `<div>` is a harness bug the gate refuses rather than packs, the
    /// same discipline `isAtom` pins for its boolean.
    ///
    /// The intrinsic fields are the raster's pixel dimensions, which ARE
    /// the CSS intrinsic size at the capture scale (the
    /// `DocumentImageRegistry.DecodedImage` contract), and the ratio is
    /// css-images-3 §4.1's width ÷ height (nil for a degenerate raster).
    public struct ReplacedRootFacts: Equatable {
        public let sourceTag: String?
        public let intrinsicWidthPx: Double
        public let intrinsicHeightPx: Double
        public let aspectRatio: Double?
        // public: the harness's pins (and the tests) construct these.
        public init(sourceTag: String?, intrinsicWidthPx: Double,
                    intrinsicHeightPx: Double, aspectRatio: Double?) {
            self.sourceTag = sourceTag
            self.intrinsicWidthPx = intrinsicWidthPx
            self.intrinsicHeightPx = intrinsicHeightPx
            self.aspectRatio = aspectRatio
        }
    }

    /// The ONE impure seam of this file (wave-44 U3b): read a root
    /// component's `ReplacedRootFacts` from the live document state —
    /// candidacy off the wire, delivery off `DocumentImageRegistry`. Nil
    /// when the root is not a replaced candidate OR its asset was not
    /// delivered (the undelivered arm then keeps the frozen block stack,
    /// which is what makes every pre-raster-OFF capture byte-identical).
    /// Lives in the runtime rather than the harnesses so the two capture
    /// canvases read the SAME attestation and cannot drift; unit tests
    /// pin `rootBox` by constructing facts directly and never touch this.
    public static func replacedRootFacts(of component: IRComponent) -> ReplacedRootFacts? {
        // Half 1 — the wire candidacy (replaced tag + non-blank src).
        guard ReplacedImageContent.isCandidate(component) else { return nil }
        // Half 2 — the platform actually decoded the named asset.
        guard let decoded = DocumentImageRegistry.shared
            .resolve(component.meta?.attrs?.src) else { return nil }
        return ReplacedRootFacts(
            sourceTag: component.meta?.sourceTag,
            intrinsicWidthPx: decoded.intrinsicWidthPx,
            intrinsicHeightPx: decoded.intrinsicHeightPx,
            aspectRatio: decoded.aspectRatio)
    }

    /// The margin property types the ROOT margin channel owns (wave-44
    /// U3a): the physical sides `rootBox` reads onto `RootBox` plus the
    /// logical sides it forces to zero. A harness renders a RUN member
    /// with these stripped (`packedMarginStripped`) because the packer
    /// now owns them — leaving them on the render would paint each margin
    /// twice (the runtime renders margins as outer padding, which would
    /// fight the row's exact member proposals).
    public static let packedMarginTypes: Set<String> =
        Set(physicalMargins + logicalMargins)

    /// A copy of `component` with every `packedMarginTypes` declaration
    /// removed — the RUN-member render shape (wave-44 U3a). Identity (the
    /// same value) for margin-free components, so the wave-34 sites
    /// render through the exact wire they always did. Kept in the facade
    /// (not the harnesses) because the harness module cannot reach
    /// IRComponent's internal memberwise init — and so both platforms
    /// strip the identical type set `rootBox` read.
    public static func packedMarginStripped(_ component: IRComponent) -> IRComponent {
        // Identity fast path — nothing to strip, return the same value.
        guard component.properties.contains(where: { packedMarginTypes.contains($0.type) })
        else { return component }
        // Rebuild with the margin declarations filtered out; every other
        // field rides through verbatim (the id keeps hoist-band
        // suppression and friends keyed exactly as before).
        return IRComponent(
            id: component.id, name: component.name,
            properties: component.properties.filter { !packedMarginTypes.contains($0.type) },
            selectors: component.selectors, media: component.media,
            children: component.children, slot: component.slot,
            text: component.text, pseudos: component.pseudos,
            meta: component.meta, variables: component.variables)
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

    /// H1 — this root's `RootBox`, or nil when it is not an inline atom
    /// and must keep the harness's block stack.
    ///
    /// Wave 44 (U3): no longer a verbatim `spec` delegate — the ROOT
    /// table is now a documented SUPERSET of the nested one (see the
    /// header's U3a/U3b/B8 notes). On the shapes both admit — a
    /// margin-free declared inline-block — the two answers are IDENTICAL
    /// (unit-pinned on both platforms so the shared rules cannot drift);
    /// the deltas are: the margin channel (U3a), the delivered-replaced
    /// family (`replaced` non-nil, U3b), and the B8 vertical-align gate
    /// over exactly those two additions.
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
        containerDeclaresLineHeight: Bool,
        replaced: ReplacedRootFacts? = nil
    ) -> RootBox? {
        // B7 — a declared container line-height invalidates the strut pins
        // (identical to the nested lane's first refusal).
        if containerDeclaresLineHeight { return nil }
        // B3 — out-of-flow boxes never join a line box (CSS 2.2 §9.3.1).
        if let p = lastKeyword(properties, "Position"), p == "ABSOLUTE" || p == "FIXED" {
            return nil
        }
        // B4 — a float is block-level (§9.7) and owned by the wave-19 packer.
        if let f = lastKeyword(properties, "Float"), f != "NONE" { return nil }
        // B5m (U3a) — the margin channel: exact-px physical margins ride
        // the RootBox; any unreadable shape (auto/%/em/calc) refuses.
        guard let m = rootMarginChannelPx(properties) else { return nil }
        // Does this box USE the new margin channel at all? B8 is scoped to
        // exactly the wave-44 additions (see the header's B8 note).
        let carriesMargins = m.contains { $0 != 0 }
        if let replaced {
            // ── U3b: the replaced family ─────────────────────────────────
            // The tag re-check half of the family+fact contract: the facts
            // alone admit nothing (mirrors InlineAtomFlow.isAtom's refusal
            // of a delivered-flag on a <div>).
            guard replacedRootTags.contains((replaced.sourceTag ?? "").lowercased())
            else { return nil }
            // css-display-3 §2 — a declared non-INLINE display takes even a
            // delivered img out of the inline flow; absent keeps the UA
            // inline default (CSS 2.1 §9.2.2). Same rule as isAtom.
            if let d = lastKeyword(properties, "Display"), !d.hasPrefix("INLINE") {
                return nil
            }
            // B8 — the packer models §10.8.1 baseline rows only; a replaced
            // element's declared vertical-align must be absent or baseline.
            guard verticalAlignIsBaseline(properties) else { return nil }
            // NOTE: hasOwnText/hasOwnRuns are deliberately NOT consulted
            // here — an <img>'s wire text is its alt fallback, which never
            // paints once the raster is delivered (ReplacedImageContent's
            // contract), so it generates no line box. Mirrors isAtom, which
            // ignores the text facts for the replaced family.
            guard let box = replacedBorderBoxPx(properties, replaced) else { return nil }
            return RootBox(widthPx: box.w, heightPx: box.h,
                           marginTopPx: m[0], marginRightPx: m[1],
                           marginBottomPx: m[2], marginLeftPx: m[3])
        }
        // ── the declared inline-block family (B1/B6/B2/H2, wave-33/34) ──
        // B1 — the declared keyword is the whole admission ticket.
        guard lastKeyword(properties, "Display") == "INLINE_BLOCK" else { return nil }
        // B6 — own line boxes would move the baseline off the box bottom.
        if hasOwnText || hasOwnRuns { return nil }
        // B8 — scoped to the NEW margin channel: a margin-free inline-block
        // keeps the frozen wave-34 (VA-ignorant) admission, because
        // css-cascade/scope-pseudo-element's `vertical-align: top` run is
        // admitted today and passes.
        if carriesMargins && !verticalAlignIsBaseline(properties) { return nil }
        // B2 — a definite size on BOTH axes, or no atom.
        guard let w = exactPx(properties, inlineSize),
              let h = exactPx(properties, blockSize) else { return nil }
        // B5 (bands half, H2) — the box-sizing-aware outer-box read.
        guard let bands = borderBoxBandsPx(properties) else { return nil }
        return RootBox(widthPx: w + bands.x, heightPx: h + bands.y,
                       marginTopPx: m[0], marginRightPx: m[1],
                       marginBottomPx: m[2], marginLeftPx: m[3])
    }

    /// Wave-43 V1's replaced tag family, re-mirrored for the ROOT gate —
    /// the same table as `InlineAtomFlow`'s replacedTags and
    /// `ReplacedImageContent`'s (both non-public, so this is the third
    /// pinned copy of the one contract; skeptic probes diff them).
    private static let replacedRootTags: Set<String> = ["img", "embed", "object", "video"]

    /// The four physical §8.3 margin sides, in (top, right, bottom, left)
    /// order — the wave-44 U3a margin channel's read set. Separate from
    /// `zeroMargins` (the nested `spec`'s all-eight absent-or-zero list)
    /// because the ROOT channel tolerates these four and still forces the
    /// logical four to zero.
    private static let physicalMargins =
        ["MarginTop", "MarginRight", "MarginBottom", "MarginLeft"]

    /// The logical margin sides — absent or an explicit zero even under
    /// the tolerant root channel, because their physical mapping is
    /// writing-mode dependent (css-logical-1 §4.1) and this rule does not
    /// model it — the same refusal the band reader keeps.
    private static let logicalMargins = [
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
    ]

    /// U3a — the (top, right, bottom, left) physical margins in exact px,
    /// or nil when the channel cannot own them and the atom must refuse:
    ///  - a physical side with any non-exact shape (`auto`, %, `em`,
    ///    `calc`) — resolving it needs bases this pure rule does not
    ///    have, and §10.3.3's auto-margin resolution is a line-layout
    ///    question;
    ///  - a LOGICAL side that is not an explicit zero (css-logical-1
    ///    §4.1, the same writing-mode refusal the band reader keeps).
    /// Absent sides are 0 (the §8.3 initial). Negative px pass through:
    /// the §10.8.1 margin-box fold in `rootRowPlan` is signed arithmetic
    /// (box-sizing-007's `margin-left: -10px` / `margin-bottom: -10px`).
    private static func rootMarginChannelPx(_ properties: [IRProperty]) -> [Double]? {
        // Logical sides: absent or an explicit zero, never a real value.
        for t in logicalMargins {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]), px == 0 else { return nil }
        }
        // Physical sides: exact px (any sign) or absent.
        var out = [0.0, 0.0, 0.0, 0.0]
        for (i, t) in physicalMargins.enumerated() {
            guard properties.last(where: { $0.type == t })?.data != nil else { continue }
            guard let px = exactPx(properties, [t]) else { return nil }
            out[i] = px
        }
        return out
    }

    /// B8 — is the declared `vertical-align` compatible with the packer's
    /// §10.8.1 baseline rows? True when ABSENT (the `baseline` initial,
    /// css-inline-3 §3.1 / CSS 2.1 §10.8) or when it reads as the
    /// `baseline` keyword; false for every other readable keyword AND for
    /// any unreadable shape (a length or % shifts the baseline by an
    /// amount this rule does not model — refuse rather than guess).
    /// MEASURED need: css-position/position-absolute-semi-replaced-
    /// stretch-input/-other declare `vertical-align: top` on margined
    /// inline-blocks — without B8 they were the only two documents the
    /// margin channel changed with the pre-raster OFF; with it the
    /// OFF-arm blast radius is exactly zero.
    private static func verticalAlignIsBaseline(_ properties: [IRProperty]) -> Bool {
        guard let data = properties.last(where: { $0.type == "VerticalAlign" })?.data
        else { return true }
        // Three wire spellings: bare string, {"keyword": …}, and the
        // parser's {"type":"keyword","value":…} wrapper (the shape the
        // semi-replaced-stretch corpus carries).
        let s = data.stringValue
            ?? data["keyword"]?.stringValue
            ?? (data["type"]?.stringValue == "keyword" ? data["value"]?.stringValue : nil)
        return s?.uppercased() == "BASELINE"
    }

    /// One sizing axis as the replaced gate reads it: a definite exact
    /// px, `auto` (declared or absent — the §10.3.2 initial), or nil for
    /// any other shape (%, intrinsic keyword, calc), which refuses.
    private enum AxisRead: Equatable {
        case definite(px: Double)
        case auto
    }

    /// Read the LAST declaration of `types` as an `AxisRead`, or nil to
    /// refuse. Absent ⇒ auto (the initial value never reaches the wire —
    /// the converter only serializes declarations).
    private static func axisRead(_ properties: [IRProperty], _ types: [String]) -> AxisRead? {
        guard let data = properties.last(where: { types.contains($0.type) })?.data
        else { return .auto }
        // The exact-px shape first (the same read `exactPx` pins)…
        if let px = data["px"]?.doubleValue { return .definite(px: px) }
        // …then the bare `auto` keyword; anything else refuses.
        return data.stringValue == "auto" ? .auto : nil
    }

    /// U3b — the replaced root's OUTER border box (w, h) px, or nil when
    /// it is not statically computable and the atom must refuse.
    ///
    /// The sizing is CSS 2.1 §10.3.2/§10.6.2 with §10.4's constraint
    /// table, computed over CONTENT-box px and converted through
    /// css-sizing-3 §3 exactly like the paint path does
    /// (`ReplacedImageContent.constrainedAutoContentSize`'s bound
    /// conversion), so the box this facade promises equals the content
    /// the member renders inside it:
    ///
    ///  - the padding+border BANDS must be statically readable
    ///    (`strictBandsPx`) under EITHER box-sizing — unlike the
    ///    inline-block H2 read, a `border-box` replaced element still
    ///    needs the real band to derive its content box;
    ///  - each axis is an exact px (definite) or `auto`/absent — any
    ///    other shape (%, intrinsic keywords, calc) refuses;
    ///  - both auto → the wave-42 `ReplacedBoxSizing.constrainAutoSize`
    ///    §10.4 table over the intrinsic size and the (band-converted)
    ///    min/max bounds;
    ///  - one definite → the free axis follows the intrinsic ratio
    ///    (§10.6.2 rule 2 / §10.3.2 rule 2; the intrinsic dimension when
    ///    the ratio is degenerate), then clamps to its own bounds;
    ///  - both definite → the declared box, band-converted.
    /// A declared min/max bound that is not exact px (and not `none`)
    /// refuses — the paint path would ignore it where the browser
    /// clamps, and promising that box would bake the disagreement into a
    /// row.
    private static func replacedBorderBoxPx(
        _ properties: [IRProperty],
        _ facts: ReplacedRootFacts
    ) -> (w: Double, h: Double)? {
        // The REAL bands — required readable whatever the sizing model.
        guard let bands = strictBandsPx(properties) else { return nil }
        // css-sizing-3 §3 tri-state, same read as the H2 band rule.
        let borderBox: Bool
        switch lastKeyword(properties, "BoxSizing") {
        case "BORDER_BOX": borderBox = true
        // Declared content-box, or unset (⇒ content-box under P19's
        // composed-WPT gate — SizeApplier.effectiveBoxSizing).
        case "CONTENT_BOX", nil: borderBox = false
        // Wire drift: never guess a sizing model.
        default: return nil
        }
        // Declared px → CONTENT px on each axis (border-box subtracts its
        // band, floored at 0 per css-ui-3 §3.1's empty-content floor).
        func contentX(_ px: Double) -> Double { borderBox ? max(0, px - bands.x) : px }
        func contentY(_ px: Double) -> Double { borderBox ? max(0, px - bands.y) : px }
        // Axis reads: definite px, auto, or refuse.
        guard let wRead = axisRead(properties, inlineSize),
              let hRead = axisRead(properties, blockSize) else { return nil }
        // Min/max bounds in CONTENT px; `refused` latches any unreadable
        // declared bound (the nested func cannot return from the outer).
        var refused = false
        func bound(_ type: String, _ toContent: (Double) -> Double) -> Double? {
            guard let data = properties.last(where: { $0.type == type })?.data
            else { return nil }
            // `max-*: none` is the initial — unbounded, not a refusal.
            if data["type"]?.stringValue == "none" { return nil }
            guard let px = data["px"]?.doubleValue else { refused = true; return nil }
            return max(0, toContent(px))
        }
        let minW = bound("MinWidth", contentX)
        let maxW = bound("MaxWidth", contentX)
        let minH = bound("MinHeight", contentY)
        let maxH = bound("MaxHeight", contentY)
        if refused { return nil }
        // §10.4 preamble clamp for the single-definite rows: lo floors at
        // the min (0 when undeclared), hi at max(max, lo) — max-below-min
        // resolves to the min, the same rule constrainAutoSize pins.
        func clamp(_ v: Double, _ lo: Double?, _ hi: Double?) -> Double {
            let lo2 = max(lo ?? 0, 0)
            let hi2 = max(hi ?? .infinity, lo2)
            return min(max(v, lo2), hi2)
        }
        // The usable-ratio gate, same as ReplacedBoxSizing.mode's.
        let ratio = facts.aspectRatio.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
        let cw: Double
        let ch: Double
        switch (wRead, hRead) {
        // §10.3.2 rule 4 — both auto: the intrinsic size through the
        // §10.4 constraint table (wave-42 W6, the box-sizing-010 family's
        // 70×70).
        case (.auto, .auto):
            let used = ReplacedBoxSizing.constrainAutoSize(
                intrinsicWidthPx: facts.intrinsicWidthPx,
                intrinsicHeightPx: facts.intrinsicHeightPx,
                aspectRatio: ratio,
                minWidthPx: minW, maxWidthPx: maxW,
                minHeightPx: minH, maxHeightPx: maxH)
            cw = used.widthPx
            ch = used.heightPx
        // §10.6.2 rule 2 — width definite, height from the ratio (the
        // intrinsic height when the ratio is degenerate), then the
        // height's own bounds clamp the derived axis.
        case (.definite(let wpx), .auto):
            cw = contentX(wpx)
            ch = clamp(ratio.map { cw / $0 } ?? facts.intrinsicHeightPx, minH, maxH)
        // §10.3.2 rule 2 — the mirror: height definite, width derived.
        case (.auto, .definite(let hpx)):
            ch = contentY(hpx)
            cw = clamp(ratio.map { ch * $0 } ?? facts.intrinsicWidthPx, minW, maxW)
        // §10.3.2 rule 1 — both definite: the declared box, clamped by
        // its own bounds (identity across the corpus, which declares no
        // bound alongside a definite axis).
        case (.definite(let wpx), .definite(let hpx)):
            cw = clamp(contentX(wpx), minW, maxW)
            ch = clamp(contentY(hpx), minH, maxH)
        }
        // Content → OUTER border box: the real bands go back on.
        return (w: cw + bands.x, h: ch + bands.y)
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
    ///    block-flow gap between them (§9.4.2). Wave 44 (U3a): now that
    ///    members may CARRY declared margins (owned by the packer), the
    ///    harness passes the DECLARED-NEUTRAL gaps — each atom root's
    ///    plan entry recomputed with its declared block margins
    ///    contributing 0 (they are re-expressed in `rootRowPlan`'s
    ///    margin-box fold, never dropped) while UA margins and hoist
    ///    bands keep their real values. An interior gap that survives
    ///    that neutralization is a layout this lane does not model (a
    ///    UA-margined member, a hoist band), so the run refuses and the
    ///    frozen stack renders instead. The gap above the run's FIRST
    ///    member is kept by the caller and is never part of this test.
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
    /// packer, wave-44 U3a: in MARGIN-BOX space. CSS 2.1 §10.8.1 puts the
    /// admitted families' baseline at the bottom MARGIN edge (replaced
    /// elements; inline-blocks with no line boxes), and §10.8's line-box
    /// arithmetic runs over the inline box's MARGIN box — so the packer
    /// is fed margin boxes (mL+w+mR × mT+h+mB, descent 0 = baseline at
    /// the margin-box bottom) and the returned origins are converted back
    /// to BORDER boxes (+mL, +mT) for the caller's placement. With
    /// all-zero margins (every wave-34 site, and the defaults) the two
    /// spaces coincide and the plan is byte-identical to the pre-wave-44
    /// one.
    ///
    /// Worked example, box-sizing-010 (ref-verified): div 70×70 with
    /// `margin-bottom: 30` (margin box 70×100, baseline 30 BELOW its
    /// border-box bottom) beside an img whose border box is 70×100 (70px
    /// green content + 30px `padding-bottom`). Both margin boxes are 100
    /// tall ⇒ one row, both border-box TOPS at y 0 — the ref's two
    /// baseline-aligned squares at image (16,88)/(91,88).
    ///
    /// `widthsPx`/`heightsPx` are the members' measured BORDER boxes in
    /// the caller's own pixel space; the margin arrays are index-aligned
    /// signed px in that same space (negative legal — box-sizing-007),
    /// defaulting to all-zero so every pre-wave-44 caller and pin is
    /// unchanged; `availableWidthPx` is the container's content width
    /// (the composed canvas's 358), `gapPx` the collapsed white-space
    /// advance (`rootAtomGapPx`, which iOS passes verbatim because CSS px
    /// == pt at the capture scale).
    public static func rootRowPlan(
        widthsPx: [Double],
        heightsPx: [Double],
        availableWidthPx: Double,
        gapPx: Double,
        marginTopsPx: [Double] = [],
        marginRightsPx: [Double] = [],
        marginBottomsPx: [Double] = [],
        marginLeftsPx: [Double] = []
    ) -> RootRowPlan {
        // Index-tolerant margin reads: a short (or empty, the default)
        // array means zero — the margin-free wave-34 shape.
        func mT(_ i: Int) -> Double { i < marginTopsPx.count ? marginTopsPx[i] : 0 }
        func mR(_ i: Int) -> Double { i < marginRightsPx.count ? marginRightsPx[i] : 0 }
        func mB(_ i: Int) -> Double { i < marginBottomsPx.count ? marginBottomsPx[i] : 0 }
        func mL(_ i: Int) -> Double { i < marginLeftsPx.count ? marginLeftsPx[i] : 0 }
        let plan = InlineAtomFlow.layout(
            // §10.8 — the inline box the row packs is the MARGIN box on
            // both axes. Signed sums: a negative side legitimately shrinks
            // the advance (t01's -10 left margin overlaps its neighbor's
            // gap exactly like the browser's cursor math).
            widths: widthsPx.indices.map { mL($0) + widthsPx[$0] + mR($0) },
            heights: widthsPx.indices.map { mT($0) + heightsPx[$0] + mB($0) },
            // §10.8.1 — baseline at the bottom MARGIN edge ⇒ descent 0
            // from the margin-box bottom, which leaves the strut's 4px
            // under the row exactly as the ref paints it.
            descents: widthsPx.map { _ in 0.0 },
            // The margin channel is already folded into `widths` above, so
            // the packer's own margin inputs stay zero (its marginStarts
            // channel offsets x by +mS, which would double-count).
            marginStarts: widthsPx.map { _ in 0.0 },
            marginEnds: widthsPx.map { _ in 0.0 },
            availableWidth: availableWidthPx,
            gapPx: gapPx,
            // P17b — the §10.8.1 line-box strut of the composed canvas's
            // 20px line box (the ref's injected `line-height`).
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
        return RootRowPlan(
            // Margin-box origins → BORDER-box origins: +mL/+mT per member.
            // For a negative left margin this moves the border box LEFT of
            // its slot (t01's border starts 10px into the previous
            // margin), which is precisely CSS's negative-margin overlap.
            xPx: plan.x.enumerated().map { $1 + mL($0) },
            yPx: plan.y.enumerated().map { $1 + mT($0) },
            widthPx: plan.width, heightPx: plan.height)
    }
}
