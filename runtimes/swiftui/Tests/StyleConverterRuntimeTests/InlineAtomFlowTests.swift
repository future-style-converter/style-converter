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
    private let atoms: [Atom] = [
        Atom(8.0, 20.0, 5.0),        //  0 a         (measured text)
        Atom(54.0, 21.0, 6.0),       //  1 button    (measured label)
        Atom(153.0, 21.0, 6.0),      //  2 input-text     (table)
        Atom(153.0, 21.0, 6.0),      //  3 input-search   (table)
        Atom(184.0, 36.0, 0.0),      //  4 textarea       (table)
        Atom(86.0, 21.0, 6.0),       //  5 input-button  (measured)
        Atom(89.0, 21.0, 6.0),       //  6 input-submit  (measured)
        Atom(82.0, 21.0, 6.0),       //  7 input-reset   (measured)
        Atom(129.0, 21.0, 6.0),      //  8 range          (table)
        Atom(13.0, 13.0, 3.0, 4.0, 3.0), // 9 checkbox    (table+margins)
        Atom(13.0, 13.0, 3.0, 4.0, 3.0), // 10 radio      (table+margins)
        Atom(50.0, 27.0, 9.0),       // 11 color          (table, fix 4)
        Atom(55.0, 19.0, 8.0),       // 12 select        (measured w, fix 4)
        Atom(87.0, 70.0, 7.0),       // 13 select-multiple (measured w, fix 4)
        Atom(80.0, 16.0, 4.5),       // 14 meter          (table, fix 4)
        Atom(160.0, 16.0, 4.5),      // 15 progress       (table, fix 4)
    ]

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
        // Row membership read off the y coordinates: row tops are 0 /
        // 21 / 63 / 135 (P17 pitches 21, 42, 72 — see the pitch pin).
        // Row 1: a+button+input-text+input-search share the first line
        // (the lane's y17-37 ref band, container-relative y0).
        XCTAssertEqual(0.0, p.y[0], accuracy: 1e-9)         // a
        XCTAssertEqual(0.0, p.y[1], accuracy: 1e-9)         // button
        XCTAssertEqual(0.0, p.y[2], accuracy: 1e-9)         // input-text
        XCTAssertEqual(0.0, p.y[3], accuracy: 1e-9)         // input-search
        // Row 2: the textarea tops the second line (ref y38 → rel 21).
        XCTAssertEqual(21.0, p.y[4], accuracy: 1e-9)        // textarea
        // Buttons hang from the shared baseline: 21 + 36A − 15a = 42.
        XCTAssertEqual(42.0, p.y[5], accuracy: 1e-9)        // input-button
        XCTAssertEqual(42.0, p.y[7], accuracy: 1e-9)        // input-reset
        // Row 3 top = 21 + (36+6) = 63 (ref y80 → rel 63): the listbox
        // is the ascent giant (70 − 7 descent = 63) and tops the row.
        XCTAssertEqual(63.0, p.y[13], accuracy: 1e-9)       // select-multiple
        // Row-3 baseline = 63 + 63A = 126 (abs y143 — checkbox top 133):
        // range 126 − 15a = 111 (ref box y127.5 → rel 110.5, ±0.5).
        XCTAssertEqual(111.0, p.y[8], accuracy: 1e-9)       // range
        // checkbox/radio: 126 − 10a = 116 (ref y133 → rel 116, exact).
        XCTAssertEqual(116.0, p.y[9], accuracy: 1e-9)       // checkbox
        XCTAssertEqual(116.0, p.y[10], accuracy: 1e-9)      // radio
        // color: 126 − 18a = 108 (ref y125 → rel 108, exact).
        XCTAssertEqual(108.0, p.y[11], accuracy: 1e-9)      // color
        // select: 126 − 11a = 115 (ref slab y132 → rel 115, exact).
        XCTAssertEqual(115.0, p.y[12], accuracy: 1e-9)      // select
        // meter rides the same baseline: 126 − 11.5a = 114.5.
        XCTAssertEqual(114.5, p.y[14], accuracy: 1e-9)      // meter
        // Row 4: progress alone at 63 + (63A + 9D) = 135 top; the P17b
        // strut holds the baseline at 135 + 15, so the 16px bar sits at
        // 150 − 11.5a = 138.5 (ref box y155.5 → rel 138.5, exact — the
        // ink band y159.5-166.5 the fix-4 probe measured).
        XCTAssertEqual(138.5, p.y[15], accuracy: 1e-9)      // progress
        XCTAssertEqual(0.0, p.x[15], accuracy: 1e-9)
    }

    func testAppearanceAutoXPositionsMatchTheRefBands() {
        let p = plan()
        // Row 1 cursor walk (gap 4.16): ref input-text at x87−16=71,
        // input-search at 245−16=229 — the pure walk lands within a px.
        XCTAssertEqual(0.0, p.x[0], accuracy: 1e-9)
        XCTAssertEqual(12.16, p.x[1], accuracy: 1e-9)
        XCTAssertEqual(70.32, p.x[2], accuracy: 1e-9)
        XCTAssertEqual(227.48, p.x[3], accuracy: 1e-9)
        // Row 2: textarea flush left; input-button at 188.16 (ref
        // x204−16=188 — exact to the sub-px).
        XCTAssertEqual(0.0, p.x[4], accuracy: 1e-9)
        XCTAssertEqual(188.16, p.x[5], accuracy: 1e-9)
        XCTAssertEqual(278.32, p.x[6], accuracy: 1e-9)
        XCTAssertEqual(371.48, p.x[7], accuracy: 1e-9)
        // Row 3: checkbox origin includes its 4px UA margin-left
        // (129 range + 4.16 gap + 4 margin).
        XCTAssertEqual(0.0, p.x[8], accuracy: 1e-9)
        XCTAssertEqual(137.16, p.x[9], accuracy: 1e-9)
        // Meter still fits row 3 (ends 465.96 ≤ 500) — the ref clips it
        // past the 390px viewport but it shares the row.
        XCTAssertEqual(385.96, p.x[14], accuracy: 1e-9)
        // Flow extent: widest row (row 3); total height = row-4 top 135
        // + the P17b strut line (15 ascent + 5 descent) = 155.
        XCTAssertEqual(465.96, p.width, accuracy: 1e-9)
        XCTAssertEqual(155.0, p.height, accuracy: 1e-9)
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
