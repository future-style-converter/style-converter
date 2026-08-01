//
//  InlineAtomFlowTests.swift
//  Wave-20 lane W3 — XCTest pins for the pure inline-atom-flow twins
//  (InlineAtomFlow.swift ↔ Compose InlineAtomFlow.kt) and the shared UA
//  widget geometry table (UAWidgetIntrinsics.swift ↔ .kt). Every pinned
//  value mirrors InlineAtomFlowTest.kt VERBATIM; the shared table is
//  the lane pin table (P15-P18). Corpus geometry comes from css-ui
//  appearance-auto-001 (and its alias siblings, all sharing
//  appearance-auto-ref.html): sixteen UA widgets inline-wrapped in a
//  500px container — ref rows (container-relative tops):
//    row 1 y0:  a, button, input-text, input-search        (pitch 21)
//    row 2 y21: textarea, input-button, input-submit, input-reset
//    row 3 y63: range, checkbox, radio, color, select,
//               select-multiple, meter                     (pitch 42)
//    row 4 y135: progress                                  (pitch 72)
//  (ref bands y17/38/80/152 with the container's own y17 origin; fix 4
//  re-solved rows 3/4 against the white-black-ink ref: baseline y143
//  from checkbox top 133, row-4 top 152 from color bottom 152.)
//

import XCTest
@testable import StyleConverterRuntime

final class InlineAtomFlowTests: XCTestCase {

    // ── P15 — the atom predicate ──

    func testWidgetTagsAreAtomsRegardlessOfChildren() {
        // The widget-identity set: opaque atoms even with children
        // (a select's options are its CONTENT, not flow siblings).
        for tag in ["button", "input", "textarea", "select", "meter", "progress"] {
            XCTAssertTrue(InlineAtomFlow.isAtom(tag: tag, displayKeyword: nil,
                                                hasElementChildren: true, hasText: false), tag)
            XCTAssertTrue(InlineAtomFlow.isAtom(tag: tag, displayKeyword: nil,
                                                hasElementChildren: false, hasText: true), tag)
        }
    }

    func testAnchorsAreAtomsOnlyInTheTextOnlyShape() {
        // <a>text</a> — the appearance-auto-001 first row member.
        XCTAssertTrue(InlineAtomFlow.isAtom(tag: "a", displayKeyword: nil,
                                            hasElementChildren: false, hasText: true))
        // An anchor wrapping elements is a real inline subtree (out of
        // scope), and an empty anchor renders nothing to pack.
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "a", displayKeyword: nil,
                                             hasElementChildren: true, hasText: true))
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "a", displayKeyword: nil,
                                             hasElementChildren: false, hasText: false))
    }

    func testNonWidgetsAndDisplayOverriddenWidgetsAreNotAtoms() {
        // Generic containers stay on the frozen block path.
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "div", displayKeyword: nil,
                                             hasElementChildren: false, hasText: true))
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: nil, displayKeyword: nil,
                                             hasElementChildren: false, hasText: true))
        // css-display-3 §2: a declared block-level display leaves the
        // inline flow; INLINE-family keywords keep the atom.
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "button", displayKeyword: "BLOCK",
                                             hasElementChildren: false, hasText: false))
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "input", displayKeyword: "FLEX",
                                             hasElementChildren: false, hasText: false))
        XCTAssertFalse(InlineAtomFlow.isAtom(tag: "input", displayKeyword: "NONE",
                                             hasElementChildren: false, hasText: false))
        XCTAssertTrue(InlineAtomFlow.isAtom(tag: "input", displayKeyword: "INLINE_BLOCK",
                                            hasElementChildren: false, hasText: false))
        XCTAssertTrue(InlineAtomFlow.isAtom(tag: "input", displayKeyword: "INLINE",
                                            hasElementChildren: false, hasText: false))
    }

    // ── P15 — segmentation ──

    func testAppearanceAutoChildrenSegmentIntoOne16AtomRun() {
        // All sixteen appearance-auto-001 children are atoms.
        let segs = InlineAtomFlow.segment([Bool](repeating: true, count: 16))
        XCTAssertEqual(1, segs.count)
        XCTAssertTrue(segs[0].isRun)
        XCTAssertEqual(Array(0...15), segs[0].indices)
    }

    func testNonAtomsSplitRunsAndLoneAtomsStaySingles() {
        // A block sibling splits the streak; ≥2 rule holds per streak.
        let segs = InlineAtomFlow.segment([true, true, false, true])
        XCTAssertEqual(3, segs.count)
        XCTAssertTrue(segs[0].isRun)
        XCTAssertEqual([0, 1], segs[0].indices)
        XCTAssertFalse(segs[1].isRun)
        // The trailing lone atom keeps the frozen block path.
        XCTAssertFalse(segs[2].isRun)
    }

    // ── P18 — widget-kind resolution (the wave-20 identity contract) ──

    func testKindsResolveFromTagPlusTheAttrsChannel() {
        // Buttons: the element and the input aliases.
        XCTAssertEqual(.buttonLike, UAWidgetIntrinsics.kind(tag: "button", typeAttr: nil, multiple: false))
        XCTAssertEqual(.buttonLike, UAWidgetIntrinsics.kind(tag: "input", typeAttr: "submit", multiple: false))
        // Inputs fold unknown/absent types to the text field (HTML
        // §4.10.5 invalid-value default — also the pre-attrs-decode
        // degradation the renderer seam documents).
        XCTAssertEqual(.textField, UAWidgetIntrinsics.kind(tag: "input", typeAttr: nil, multiple: false))
        XCTAssertEqual(.textField, UAWidgetIntrinsics.kind(tag: "input", typeAttr: "search", multiple: false))
        XCTAssertEqual(.range, UAWidgetIntrinsics.kind(tag: "input", typeAttr: "range", multiple: false))
        XCTAssertEqual(.checkbox, UAWidgetIntrinsics.kind(tag: "input", typeAttr: "checkbox", multiple: false))
        XCTAssertEqual(.color, UAWidgetIntrinsics.kind(tag: "input", typeAttr: "color", multiple: false))
        // multiple flips the select family to the listbox.
        XCTAssertEqual(.select, UAWidgetIntrinsics.kind(tag: "select", typeAttr: nil, multiple: false))
        XCTAssertEqual(.listbox, UAWidgetIntrinsics.kind(tag: "select", typeAttr: nil, multiple: true))
        XCTAssertEqual(.meter, UAWidgetIntrinsics.kind(tag: "meter", typeAttr: nil, multiple: false))
        XCTAssertEqual(.progress, UAWidgetIntrinsics.kind(tag: "progress", typeAttr: nil, multiple: false))
    }

    // ── P16/P17 — the appearance-auto-001 flow pin ──

    // The sixteen atoms in wire order with their resolved geometry:
    // fixed sizes from the shared table; label-driven widths (a,
    // button, input-button/submit/reset, select, select-multiple) use
    // the ref-measured values the replicas converge on.
    private struct Atom {
        let w: Double; let h: Double; let d: Double
        let mS: Double; let mE: Double
        init(_ w: Double, _ h: Double, _ d: Double,
             _ mS: Double = 0.0, _ mE: Double = 0.0) {
            self.w = w; self.h = h; self.d = d; self.mS = mS; self.mE = mE
        }
    }
    // A-RC2 (wave 22): the label-driven widths are no longer eyeballed
    // off the ref — they are computed from the SAME Arial-metric pin the
    // replica painter measures with, so this flow test and the widget
    // pin table can never disagree about how wide a control is.
    private func ctrl(_ label: String) -> Double {
        Double(UAControlFontMetrics.advance(label, UAWidgetsGeometry.FONT))
    }
    private var atoms: [Atom] {
        [
            // 'a' in the block's INTER 16px face (hmtx 1150/2048 × 16):
            // the anchor is prose, not a control, so it keeps Inter.
            Atom(8.984375, 20.0, 4.0),                      //  0 a
            Atom(ctrl("button") + 16.0, 21.0, 6.0),         //  1 button
            Atom(153.0, 21.0, 6.0),                         //  2 input-text
            Atom(153.0, 21.0, 6.0),                         //  3 input-search
            Atom(183.0, 36.0, 0.0),                         //  4 textarea
            Atom(ctrl("input-button") + 16.0, 21.0, 6.0),   //  5 input-button
            Atom(ctrl("input-submit") + 16.0, 21.0, 6.0),   //  6 input-submit
            Atom(ctrl("input-reset") + 16.0, 21.0, 6.0),    //  7 input-reset
            Atom(129.0, 21.0, 6.0, 2.0, 2.0),               //  8 range
            Atom(13.0, 13.0, 3.0, 4.5, 3.0),                //  9 checkbox
            Atom(13.0, 13.0, 3.0, 4.5, 3.0),                // 10 radio
            Atom(50.0, 27.0, 9.0),                          // 11 color
            Atom(ctrl("select") + 5.0 + 17.42, 19.0, 8.0),  // 12 select
            Atom(ctrl("select-multiple") + 6.0, 70.0, 7.0), // 13 select-multiple
            Atom(80.0, 16.0, 3.0),                          // 14 meter
            Atom(160.0, 16.0, 3.0),                         // 15 progress
        ]
    }

    private func plan() -> InlineAtomFlow.FlowLayout {
        InlineAtomFlow.layout(
            widths: atoms.map(\.w),
            heights: atoms.map(\.h),
            descents: atoms.map(\.d),
            marginStarts: atoms.map(\.mS),
            marginEnds: atoms.map(\.mE),
            // The refs' `#container { width: 500px }` content width.
            availableWidth: 500.0,
            gapPx: UAWidgetIntrinsics.atomGapPx,
            // P17b (fix 4) — the §10.8.1 strut, exactly as the adapter
            // (InlineAtomBlockLayout) passes it.
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
    }

    func testAppearanceAutoRowsSplit4471LikeTheRef() {
        let p = plan()
        // Wave-22 vertical solve. Row tops are 0 / 22 / 64 / 136 (rel to
        // the container content origin abs y16), i.e. ABS 16 / 38 / 80 /
        // 152 — every one of those is a measured ref band. Pitches come
        // from the corrected strut (ascent 16, descent 4).
        // Row 1: a+button+input-text+input-search share the first line;
        // the ANCHOR keeps the row top (ascent 16 = the strut) while the
        // 21px controls hang 1px lower — abs y17, the ref band.
        XCTAssertEqual(0.0, p.y[0], accuracy: 1e-6)         // a (abs 16)
        XCTAssertEqual(1.0, p.y[1], accuracy: 1e-6)         // button (abs 17)
        XCTAssertEqual(1.0, p.y[2], accuracy: 1e-6)         // input-text
        XCTAssertEqual(1.0, p.y[3], accuracy: 1e-6)         // input-search
        // Row 2 top = 16A + 6D = 22 → abs 38, the ref textarea border row.
        XCTAssertEqual(22.0, p.y[4], accuracy: 1e-6)        // textarea
        // Buttons hang from the shared baseline: 22 + 36A − 15a = 43
        // (abs 59 — the ref input-button border row).
        XCTAssertEqual(43.0, p.y[5], accuracy: 1e-6)        // input-button
        XCTAssertEqual(43.0, p.y[7], accuracy: 1e-6)        // input-reset
        // Row 3 top = 22 + (36+6) = 64 → abs 80: the listbox is the
        // ascent giant (70 − 7 descent = 63) and tops the row (ref y80).
        XCTAssertEqual(64.0, p.y[13], accuracy: 1e-6)       // select-multiple
        // Row-3 baseline = 64 + 63A = 127 → abs 143. Each atom below is
        // baseline − (h − d) and matches its ref band EXACTLY:
        XCTAssertEqual(112.0, p.y[8], accuracy: 1e-6)       // range   (abs 128)
        XCTAssertEqual(117.0, p.y[9], accuracy: 1e-6)       // checkbox(abs 133)
        XCTAssertEqual(117.0, p.y[10], accuracy: 1e-6)      // radio   (abs 133)
        XCTAssertEqual(109.0, p.y[11], accuracy: 1e-6)      // color   (abs 125)
        XCTAssertEqual(116.0, p.y[12], accuracy: 1e-6)      // select  (abs 132)
        // meter rides the same baseline: 127 − 13a = 114 (off-canvas).
        XCTAssertEqual(114.0, p.y[14], accuracy: 1e-6)      // meter
        // Row 4: progress alone at 64 + (63A + 9D) = 136 top (abs 152);
        // the strut holds the baseline at 136 + 16, so the 16px bar sits
        // at 152 − 13a = 139 → abs 155, whose 4px ink inset is the ref's
        // measured bar band y159..167.
        XCTAssertEqual(139.0, p.y[15], accuracy: 1e-6)      // progress
        XCTAssertEqual(0.0, p.x[15], accuracy: 1e-6)
    }

    func testAppearanceAutoXPositionsMatchTheRefBands() {
        let p = plan()
        // Wave-22 horizontal solve (gap 4.5 = Inter's 16px space
        // advance). Every expectation below is quoted with the ABS
        // column (+16) and the ref's PAINTED border column, which
        // Chromium pixel-snaps — so a residual under 0.5 is exact.
        // Row 1: button abs 29.48 (ref 29), field abs 87.05 (ref 87),
        // search abs 244.55 (ref 245).
        XCTAssertEqual(0.0, p.x[0], accuracy: 1e-6)
        XCTAssertEqual(13.4844, p.x[1], accuracy: 1e-3)
        XCTAssertEqual(71.0546, p.x[2], accuracy: 1e-3)
        XCTAssertEqual(228.5546, p.x[3], accuracy: 1e-3)
        // Row 2: textarea flush left; input-button abs 203.5 (ref 204),
        // input-submit abs 294.42 (ref 294), input-reset abs 387.55
        // (ref 388).
        XCTAssertEqual(0.0, p.x[4], accuracy: 1e-6)
        XCTAssertEqual(187.5, p.x[5], accuracy: 1e-3)
        XCTAssertEqual(278.423, p.x[6], accuracy: 1e-3)
        XCTAssertEqual(371.5465, p.x[7], accuracy: 1e-3)
        // Row 3, the band wave 21 missed by 5-7px. Range carries its UA
        // 2px margin (abs 18 → track ink abs 19, the ref probe); the
        // checkbox and radio land on their ref columns EXACTLY.
        XCTAssertEqual(2.0, p.x[8], accuracy: 1e-6)         // range   (abs 18)
        XCTAssertEqual(142.0, p.x[9], accuracy: 1e-6)       // checkbox(abs 158 = ref)
        XCTAssertEqual(167.0, p.x[10], accuracy: 1e-6)      // radio   (abs 183 = ref)
        XCTAssertEqual(187.5, p.x[11], accuracy: 1e-3)      // color   (abs 203.5, ref 204)
        XCTAssertEqual(242.0, p.x[12], accuracy: 1e-6)      // select  (abs 258 = ref)
        XCTAssertEqual(303.7506, p.x[13], accuracy: 1e-3)   // listbox (abs 319.75, ref 320)
        // Meter still fits row 3 (ends 479.47 ≤ 500) — the ref clips it
        // past the 390px viewport but it shares the row.
        XCTAssertEqual(399.4653, p.x[14], accuracy: 1e-3)
        // Flow extent: widest row (row 3); total height = row-4 top 136
        // + the strut line (16 ascent + 4 descent) = 156.
        XCTAssertEqual(479.4653, p.width, accuracy: 1e-3)
        XCTAssertEqual(156.0, p.height, accuracy: 1e-6)
    }

    func testFirstAtomInARowNeverWrapsAndUnboundedNeverWraps() {
        // A 600-wide atom in the 500 container holds row 1 (the loose
        // first-never-wraps rule, mirroring the float packer's rule 7).
        let p = InlineAtomFlow.layout(
            widths: [600.0, 50.0],
            heights: [10.0, 10.0],
            descents: [0.0, 0.0],
            marginStarts: [0.0, 0.0],
            marginEnds: [0.0, 0.0],
            availableWidth: 500.0,
            gapPx: 4.0
        )
        XCTAssertEqual([0.0, 0.0], p.x)
        XCTAssertEqual([0.0, 10.0], p.y)
        // Unbounded width → a single row, gaps between atoms.
        let q = InlineAtomFlow.layout(
            widths: [600.0, 50.0],
            heights: [10.0, 10.0],
            descents: [0.0, 0.0],
            marginStarts: [0.0, 0.0],
            marginEnds: [0.0, 0.0],
            availableWidth: .infinity,
            gapPx: 4.0
        )
        XCTAssertEqual([0.0, 604.0], q.x)
        XCTAssertEqual([0.0, 0.0], q.y)
    }

    // ── Wave-20 fix 3 — the fill-width fold guard, pinned on the LIVE
    // appearance-auto-001 wire ──

    /// Repo-root anchored path to the REAL wave20-final per-test IR (the
    /// ConformanceTests #filePath-hop pattern; skip-guarded below).
    private static var appearanceAuto001IR: URL {
        URL(fileURLWithPath: #filePath)               // …/InlineAtomFlowTests.swift
            .deletingLastPathComponent()              // StyleConverterRuntimeTests/
            .deletingLastPathComponent()              // Tests/
            .deletingLastPathComponent()              // swiftui/
            .deletingLastPathComponent()              // runtimes/
            .deletingLastPathComponent()              // repo root
            .appendingPathComponent("tools/titan/runs/wave20-final/sections/css-ui/per-test-ir/"
                + "wpt__css-ui__appearance-auto-001.json")
    }

    func testFix3FillWidthFoldSkipsEveryLiveAtomAndRowPartitionHoldsRef() throws {
        // Live-wire pin: hermetic checkouts without the run dir skip.
        let url = Self.appearanceAuto001IR
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("wave20-final run directory absent")
        }
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: url))
        // The v2 decode slot-COMPOSES the flat wire (doc.components are
        // the composed roots), so the container's sixteen widget children
        // arrive via container.children, in wire order.
        let container = try XCTUnwrap(doc.components.first { $0.id.hasSuffix("__0-007") })
        let children = container.children ?? []
        XCTAssertEqual(16, children.count)
        // fix 3 — the fold guard: EVERY widget child is an inline atom
        // (no Display property on the wire — the UA inline-block default
        // is unmodeled), so wptBlockFlowFillWidth must never stretch it
        // to the 500px containing block (CSS 2.1 §10.3.9 shrink-to-fit;
        // the 500px stretch made InlineAtomBlockLayout wrap every atom
        // onto its own row — the 0.818→0.729 css-ui regression)…
        for child in children {
            XCTAssertTrue(ComponentRenderer.isInlineAtom(child), child.id)
        }
        // …while the CONTAINER itself (no sourceTag) keeps the fold.
        XCTAssertFalse(ComponentRenderer.isInlineAtom(container))
        // Kind resolution off the REAL attrs channel (spot pins).
        XCTAssertEqual(.textAnchor, ComponentRenderer.atomKindOf(children[0]))   // <a>
        XCTAssertEqual(.buttonLike, ComponentRenderer.atomKindOf(children[6]))   // input-submit
        XCTAssertEqual(.range, ComponentRenderer.atomKindOf(children[8]))
        XCTAssertEqual(.listbox, ComponentRenderer.atomKindOf(children[13]))     // select multiple
        XCTAssertEqual(.progress, ComponentRenderer.atomKindOf(children[15]))
        // Row partition against the ref (Compose is the structural
        // oracle — its InlineFlowLayout measured unbounded and produced
        // the near-ref rows): resolve each live child through the SAME
        // kind → AtomSpec chain the renderer uses, fill the measured
        // (label-driven) widths with the ref-measured constants of the
        // shared flow pin above, and re-run the pure layout at the
        // container's 500px. The partition must be the ref's 4/4/7/1.
        let specs = children.map { UAWidgetIntrinsics.spec(ComponentRenderer.atomKindOf($0)) }
        let measuredW: [Int: Double] = [0: 8.0, 1: 54.0, 5: 86.0, 6: 89.0,
                                        7: 82.0, 12: 55.0, 13: 87.0]
        let p = InlineAtomFlow.layout(
            widths: specs.indices.map { specs[$0].fixedWpx ?? measuredW[$0] ?? 0.0 },
            heights: specs.indices.map { specs[$0].fixedHpx ?? UAWidgetIntrinsics.textLineBoxPx },
            descents: specs.map(\.descentPx),
            marginStarts: specs.map(\.marginStartPx),
            marginEnds: specs.map(\.marginEndPx),
            availableWidth: 500.0,
            gapPx: UAWidgetIntrinsics.atomGapPx,
            strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
            strutDescentPx: UAWidgetIntrinsics.strutDescentPx
        )
        // Ref row tops (container-relative): 0 / 21 / 63 / 135.
        let rowOf: (Double) -> Int = { y in
            y >= 135.0 ? 3 : y >= 63.0 ? 2 : y >= 21.0 ? 1 : 0
        }
        XCTAssertEqual([0, 0, 0, 0,            // a, button, text, search
                        1, 1, 1, 1,            // textarea + 3 input buttons
                        2, 2, 2, 2, 2, 2, 2,   // range…meter (7 atoms)
                        3],                    // progress alone
                       p.y.map(rowOf))
    }
}
