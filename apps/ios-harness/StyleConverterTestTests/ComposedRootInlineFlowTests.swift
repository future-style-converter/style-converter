//
//  ComposedRootInlineFlowTests.swift
//  StyleConverterTestTests
//
//  Retro R10 (finding A4#5) — the pins ComposedRootInlineFlow.swift's
//  header claimed but the tree never had: no file or symbol named
//  `ComposedRootInlineFlowTests` existed anywhere, and nothing referenced
//  `composedRootInlineBoxes`, so the resolver that decides the composed
//  root row for EVERY WPT capture on iOS had zero coverage while its
//  doc-comment advertised a unit pin.
//
//  WHAT THIS FILE PINS: the harness's half only — which wire facts each
//  root contributes (B6's own text, B7's body-root line-height) and that
//  the two wave-33/34 victim documents keep the exact atoms and the exact
//  run the wave-34 lane measured. The gate table itself and the §9.4.2
//  packing belong to the runtime and are pinned in the runtime package's
//  InlineBlockAtomTests.swift / the Compose InlineBlockAtomTest.kt — this
//  file must never re-derive them. Byte-parallel twin of
//  apps/android-harness .../screenshot/ComposedRootInlineFlowTest.kt.
//
//  PAYLOADS: the verbatim per-test IR of the two documents
//  InlineBlockAtom.swift's blast-radius enumeration names, vendored under
//  tools/titan/fixtures/ (its README carries the provenance rules; the
//  wave49-final copies are byte-identical to wave47/48-final). Read by
//  #filePath hops — the pattern ConformanceTests and the runtime's own
//  live-wire pins use — so no bundle-resource wiring is needed.
//
//  HOW TO RUN — this bundle is NOT in any documented sweep (it needs a
//  booted simulator; tools/visual/doc-staleness-check.sh warns about the
//  gap on every run):
//
//    cd apps/ios-harness && xcodebuild test -scheme StyleConverterTestTests \
//      -destination 'platform=iOS Simulator,name=iPhone 16'
//
//  Device-free in substance like ComposedCanvasPaddingTests:
//  `composedRootInlineBoxes` is a pure function over the decoded IR.
//

import XCTest
// IRDocument decodes through the runtime's public Decodable witness — the
// memberwise inits are package-internal, so tests build documents from JSON
// exactly as the harness does.
import StyleConverterRuntime
@testable import StyleConverterTest

final class ComposedRootInlineFlowTests: XCTestCase {

    // ── vendored corpus payloads ─────────────────────────────────────────

    /// Repo root, reached from this file: drop the file name, then the
    /// three directories …/apps/ios-harness/StyleConverterTestTests/.
    private static var repoRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterTestTests/
            .deletingLastPathComponent()   // ios-harness/
            .deletingLastPathComponent()   // apps/
            .deletingLastPathComponent()   // repo root
    }

    /// Decode one vendored per-test document. The v2 decode slot-COMPOSES
    /// the flat wire, so `doc.components` are the composed ROOTS — exactly
    /// what ComposedCaptureCanvas hands `composedRootInlineBoxes`.
    private func composedRoots(_ section: String, _ stem: String) throws -> [IRComponent] {
        let url = Self.repoRoot
            .appendingPathComponent("tools/titan/fixtures/per-test-ir/wave49-final")
            .appendingPathComponent(section)
            .appendingPathComponent("\(stem).json")
        // No skip guard: the fixture is committed, so an absent or renamed
        // payload is a real failure (finding A8#3 — skip-guarded pins over
        // pruned run dirs skipped forever instead of hermetically).
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: url))
        return doc.components
    }

    // ── the wave-34 target document ──────────────────────────────────────

    func testTargetDocumentsThreeInlineBlockRootsAreAtomsAndPackAsOneRun() throws {
        // CSS2/abspos/static-inside-inline-block, verbatim: a prose <p>
        // root, then three 100×100 declared inline-blocks (the middle one
        // holding the abspos green square as a CHILD, which generates no
        // line box of its own — CSS 2.1 §9.3.1, so B6 does not refuse it).
        let roots = try composedRoots("CSS2", "wpt__CSS2__abspos__static-inside-inline-block")
        XCTAssertEqual(4, roots.count, "the document is one <p> plus three inline-blocks")
        let boxes = composedRootInlineBoxes(roots)
        // B6 — the <p> carries its own text, so it is NOT an atom: its own
        // line box would move the baseline off its bottom margin edge
        // (CSS 2.1 §10.8.1).
        XCTAssertNil(boxes[0])
        // B2/B5 — no bands and no margins on the wire, so each atom is the
        // declared content box exactly (the `box-sizing: content-box`
        // default, the rule InlineBlockAtom's H2 header cites).
        for i in 1...3 {
            XCTAssertEqual(InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100), boxes[i], "root \(i)")
        }
        // The §9.4.2 packing the wave-34 lane exists to produce: ONE run
        // over the three atoms (indices 1…3), the prose root left alone.
        let segs = try XCTUnwrap(InlineBlockAtom.rootSegments(
            boxes: boxes, blockGapsAbovePx: [Double](repeating: 0, count: roots.count)))
        XCTAssertEqual([InlineBlockAtom.RootSegment(indices: [0], isRun: false),
                        InlineBlockAtom.RootSegment(indices: [1, 2, 3], isRun: true)], segs)
    }

    // ── B8 pass-preservation (the Compose twin's adversarial pin) ────────

    func testScopePseudoElementKeepsItsPassingRootRun() throws {
        // css-cascade/scope-pseudo-element is the currently-PASSING composed
        // document (Android 0.9746 android-ref, wave48-final/wave49-final)
        // whose three roots declare BOTH `display: inline-block` AND
        // `vertical-align: top`. The wave-44 B8 gate is SCOPED to the new
        // margin/replaced channels precisely so these three keep the frozen
        // wave-34 admission; an unscoped gate would evict them and revert
        // the canvas to the block stack — an unmeasured regression of a
        // pass. Twin of the Kotlin ComposedRootInlineFlowTest pin.
        let roots = try composedRoots("css-cascade", "wpt__css-cascade__scope-pseudo-element")
        XCTAssertEqual(3, roots.count)
        // Guard the premise: every root really carries the B8-adverse
        // `vertical-align: top` off the wire — if a converter change ever
        // drops it, this test must say so rather than vacuously pass.
        for root in roots {
            let va = try XCTUnwrap(root.properties.last { $0.type == "VerticalAlign" })
            // The keyword wire shape: `{"type":"keyword","value":"TOP"}` —
            // read through IRValue's public subscript, the same accessor the
            // engine's own keyword readers use.
            XCTAssertEqual("keyword", va.data["type"]?.stringValue)
            XCTAssertEqual("TOP", va.data["value"]?.stringValue)
        }
        let boxes = composedRootInlineBoxes(roots)
        // 100px content + 1px border per side, margin-free: the exact
        // pre-wave-44 box, so the committed capture cannot move.
        for (i, box) in boxes.enumerated() {
            XCTAssertEqual(InlineBlockAtom.RootBox(widthPx: 102, heightPx: 102), box, "root \(i)")
        }
        let segs = try XCTUnwrap(InlineBlockAtom.rootSegments(
            boxes: boxes, blockGapsAbovePx: [0, 0, 0]))
        XCTAssertEqual([InlineBlockAtom.RootSegment(indices: [0, 1, 2], isRun: true)], segs)
    }

    // ── B7: the document body's own line box ─────────────────────────────

    func testBodyRootLineHeightRefusesEveryRootAtom() throws {
        // The packer's 16/4 strut pins are solved for the ref's INJECTED
        // body line box; an author override invalidates them. The payload
        // is the LIVE converter shape for `line-height: 0` — the nested
        // `{"original":{"type":"length","px":0.0}}` (the only length
        // spelling the wave49-final corpus carries for LineHeight).
        let withBodyLh = try Self.document(bodyLineHeight: true)
        let refused = composedRootInlineBoxes(withBodyLh)
        XCTAssertNil(refused[1])
        XCTAssertNil(refused[2])
        // Control: the same two roots ARE atoms without the body
        // declaration — proving the gate is the line-height, not the extra
        // leading root.
        let control = composedRootInlineBoxes(try Self.document(bodyLineHeight: false))
        XCTAssertEqual(InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100), control[1])
        XCTAssertEqual(InlineBlockAtom.RootBox(widthPx: 100, heightPx: 100), control[2])
    }

    /// A body-root plus two 100×100 declared inline-blocks; the body
    /// optionally declares its own `line-height: 0`.
    private static func document(bodyLineHeight: Bool) throws -> [IRComponent] {
        let lh = bodyLineHeight
            ? #"{"type": "LineHeight", "data": {"original": {"type": "length", "px": 0.0}}}"#
            : ""
        let json = """
        {"irVersion": 2, "minReaderVersion": 2, "components": [
          {"id": "b", "name": "t__body", "properties": [\(lh)],
           "meta": {"role": "body-root"}},
          {"id": "r1", "name": "t__0", "properties": [
            {"type": "Display", "data": "INLINE_BLOCK"},
            {"type": "Width", "data": {"type": "length", "px": 100}},
            {"type": "Height", "data": {"type": "length", "px": 100}}]},
          {"id": "r2", "name": "t__1", "properties": [
            {"type": "Display", "data": "INLINE_BLOCK"},
            {"type": "Width", "data": {"type": "length", "px": 100}},
            {"type": "Height", "data": {"type": "length", "px": 100}}]}
        ]}
        """
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8)).components
    }
}
