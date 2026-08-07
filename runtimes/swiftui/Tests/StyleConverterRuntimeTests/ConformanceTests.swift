//
//  ConformanceTests.swift
//  StyleConverterRuntimeTests
//
//  SwiftUI-runtime side of the IR wire contract, both generations:
//
//    v2 (schema/ir-v2.schema.json + schema/spec/01/03/05): every golden
//    in schema/conformance/fixtures/v2/ decodes through the runtime's
//    real IR-loading path (JSONDecoder → IRDocument → strict
//    IRWireV2Reader → IRComposer), with sentinels for the flat/slot
//    composition, the renames (text/pseudos/meta), the strictness rules
//    (children hard error, unknown envelope keys, minReaderVersion
//    refusal) and the placement-claims contract.
//
//    v1 (schema/ir-v1.schema.json, deprecation window): the original
//    golden set keeps decoding through the tolerant legacy path, with
//    the underscore hints now TRANSLATED into their v2 homes
//    (text / meta.sourceTag / meta.role) instead of dropped.
//

import Foundation
import XCTest
// @testable so the decode path under test is the module-internal one.
@testable import StyleConverterRuntime

final class ConformanceTests: XCTestCase {

    // MARK: - fixture plumbing

    // Resolve repo-root/schema/conformance/fixtures from this source file's
    // compile-time path: …/runtimes/swiftui/Tests/StyleConverterRuntimeTests/
    // ConformanceTests.swift → four directory hops up is the repo root.
    private static var fixturesDir: URL {
        URL(fileURLWithPath: #filePath)               // …/ConformanceTests.swift
            .deletingLastPathComponent()              // StyleConverterRuntimeTests/
            .deletingLastPathComponent()              // Tests/
            .deletingLastPathComponent()              // swiftui/
            .deletingLastPathComponent()              // runtimes/
            .deletingLastPathComponent()              // repo root
            .appendingPathComponent("schema/conformance/fixtures", isDirectory: true)
    }

    // The v2 golden set lives in the v2/ subdirectory (1:1 filenames with
    // the v1 set + the two v2-only goldens).
    private static var fixturesDirV2: URL {
        fixturesDir.appendingPathComponent("v2", isDirectory: true)
    }

    // Real load path: raw bytes → JSONDecoder → IRDocument (mirrors the
    // harness's ContentView.loadDocument). v1 goldens.
    private func load(_ name: String) throws -> IRDocument {
        let data = try Data(contentsOf: Self.fixturesDir.appendingPathComponent(name))
        return try JSONDecoder().decode(IRDocument.self, from: data)
    }

    // Same loader against the v2 golden directory.
    private func loadV2(_ name: String) throws -> IRDocument {
        let data = try Data(contentsOf: Self.fixturesDirV2.appendingPathComponent(name))
        return try JSONDecoder().decode(IRDocument.self, from: data)
    }

    // Inline-JSON document decode for the strictness error-path tests.
    private func decodeDoc(_ json: String) throws -> IRDocument {
        try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
    }

    // Depth-first walk of the COMPOSED tree — v1 children come from the
    // wire, v2 children from IRComposer; either way this sees every node.
    private func walk(_ components: [IRComponent], _ visit: (IRComponent) -> Void) {
        for c in components {
            visit(c)
            if let kids = c.children { walk(kids, visit) }
        }
    }

    // Find a component by unique name anywhere in the composed tree.
    private func byName(_ doc: IRDocument, _ name: String) throws -> IRComponent {
        var found: IRComponent?
        walk(doc.components) { if $0.name == name { found = $0 } }
        return try XCTUnwrap(found, "component \(name) not in fixture")
    }

    // Numeric IRValue helper: kotlinx emits 16.0-style decimals which
    // Foundation may surface as .int or .double — normalize both.
    private func asDouble(_ v: IRValue?) -> Double? {
        switch v {
        case .int(let i): return Double(i)
        case .double(let d): return d
        default: return nil
        }
    }

    // Object-member accessor for IRValue payloads.
    private func member(_ v: IRValue, _ key: String) -> IRValue? {
        if case .object(let dict) = v { return dict[key] }
        return nil
    }

    // MARK: - umbrella: every v1 golden decodes through the real path

    func testEveryGoldenFixtureDecodes() throws {
        let files = try FileManager.default
            .contentsOfDirectory(at: Self.fixturesDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        XCTAssertGreaterThanOrEqual(files.count, 12, "expected the full golden set")
        for file in files {
            // Decode MUST NOT throw — including unknown property types
            // (spec 05 tolerance rule 1) and the v1 underscore metadata
            // keys, which now translate instead of dropping.
            let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: file))
            XCTAssertEqual(doc.irVersion, 1, "\(file.lastPathComponent): v1 golden must take the v1 path")
            walk(doc.components) { c in
                XCTAssertFalse(c.id.isEmpty, "\(file.lastPathComponent): empty id")
            }
        }
    }

    // MARK: - umbrella: every v2 golden decodes through the strict path

    func testEveryV2GoldenFixtureDecodes() throws {
        let files = try FileManager.default
            .contentsOfDirectory(at: Self.fixturesDirV2, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        // 12 v1 mirrors + slot-composition + placement-claims.
        XCTAssertGreaterThanOrEqual(files.count, 14, "expected the full v2 golden set")
        for file in files {
            let doc = try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: file))
            XCTAssertEqual(doc.irVersion, 2, "\(file.lastPathComponent): v2 golden must take the v2 path")
            // The flat wire list is preserved alongside the composition.
            XCTAssertNotNil(doc.flatComponents, "\(file.lastPathComponent): flat list missing")
            walk(doc.components) { c in
                XCTAssertFalse(c.id.isEmpty, "\(file.lastPathComponent): empty id")
            }
        }
    }

    // MARK: - v2 slot composition

    func testV2SlotComposition() throws {
        // 6 flat entries → one 3-level tree: SlotRoot > (band__0 >
        // (cell__0__0, cell__0__1), band__1 > cell__1__0).
        let doc = try loadV2("slot-composition.json")
        XCTAssertEqual(doc.flatComponents?.count, 6, "flat wire list must keep all entries")
        // Exactly one root: the only slot-free entry.
        XCTAssertEqual(doc.components.count, 1)
        let root = try XCTUnwrap(doc.components.first)
        XCTAssertEqual(root.name, "SlotRoot")
        XCTAssertNil(root.slot, "roots omit slot")
        // Sibling-order rule: flat array order IS composition order.
        XCTAssertEqual(root.children?.map(\.name), ["band__0", "band__1"])
        XCTAssertEqual(root.children?[0].children?.map(\.name), ["cell__0__0", "cell__0__1"])
        XCTAssertEqual(root.children?[1].children?.map(\.name), ["cell__1__0"])
        // slot round-trips on composed children (spec 05 rule 4), with
        // the omitted default name reconstructed.
        XCTAssertEqual(root.children?[0].slot?.parent, "slotroot-001")
        XCTAssertEqual(root.children?[0].slot?.name, "content")
        // Leaf children are nil, never [] (renderer placeholder contract).
        XCTAssertNil(root.children?[1].children?[0].children)
    }

    // MARK: - v2 renames (text / meta)

    func testV2TextAndMeta() throws {
        let doc = try loadV2("text-and-role.json")
        // `text` (v2 rename of `_text`), empty-string-is-meaningful.
        XCTAssertEqual(try byName(doc, "TextRole_BodyRoot").text, "Test passes if this text is green")
        XCTAssertEqual(try byName(doc, "TextRole_EmptyStringText").text, "")
        XCTAssertNil(try byName(doc, "TextRole_NoMetadata").text)
        // `meta.role` (v2 home of `_role`) — no longer dropped on iOS.
        XCTAssertEqual(try byName(doc, "TextRole_BodyRoot").meta?.role, "body-root")
        XCTAssertNil(try byName(doc, "TextRole_NoMetadata").meta)
    }

    // MARK: - v2 meta.decorations (wave-22 lane DECOR)

    func testV2MetaDecorations() throws {
        let doc = try loadV2("decorations.json")
        // The three-colour chain: ORDER outermost-first, colour tokens
        // AS AUTHORED (the converter forwards `meta` verbatim — it is NOT
        // the IR sRGB leaf; DecorationWire resolves it at paint time).
        let chain = try byName(doc, "Decor_ThreeColourChain")
        XCTAssertEqual(chain.meta?.decorations?.map { $0.line },
                       ["underline", "overline", "line-through"])
        XCTAssertEqual(chain.meta?.decorations?.map { $0.color },
                       ["blue", "gray", "green"])
        // An omitted colour stays nil = currentColor (§2.2 initial).
        let cc = try byName(doc, "Decor_CurrentColorEntry")
        XCTAssertEqual(cc.meta?.decorations?.count, 1)
        XCTAssertNil(cc.meta?.decorations?[0].color)
        // Functional + hex tokens survive verbatim to the runtime.
        let fn = try byName(doc, "Decor_FunctionalColourToken")
        XCTAssertEqual(fn.meta?.decorations?.map { $0.color },
                       ["#00ff00", "rgb(0, 0, 255)"])
        // A component with no decorations keeps nil (absence ≠ emptiness).
        XCTAssertNil(try byName(doc, "Decor_Container").meta?.decorations)
    }

    func testV2MetaDecorationsStrictness() {
        let head = """
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [], "meta": { "decorations":
        """
        // Not an array / empty array / non-object entry / unknown entry key
        // / missing `line` / non-string colour — all writer bugs.
        for bad in ["{\"line\":\"underline\"}", "[]", "[\"underline\"]",
                    "[{\"line\":\"underline\",\"style\":\"wavy\"}]",
                    "[{\"color\":\"blue\"}]", "[{\"line\":\"underline\",\"color\":7}]"] {
            XCTAssertThrowsError(try decodeDoc(head + bad + " } } ] }"),
                                 "meta.decorations \(bad) must be a hard decode error")
        }
        // …but an UNKNOWN LINE KEYWORD is tolerated (spec 05 rule 1): a
        // future §2.1 keyword must decode and be dropped at paint time,
        // not make the whole document unreadable.
        XCTAssertNoThrow(try decodeDoc(head + "[{\"line\":\"spiral\"}] } } ] }"))
    }

    // MARK: - wave-27 lane CBAKE: meta.markerText + the ol/li attr lane

    /// The baked list marker decodes verbatim off the `list-marker-text`
    /// golden, and `meta.attrs.start` joins the strict attr key set.
    ///
    /// Why the DECODE is the unit under test rather than the renderer read:
    /// ComponentRenderer's preference is one line (`child.meta?.markerText`),
    /// but a reader that rejected or dropped the key would make that line
    /// dead and silent. TWIN of the Compose `IRDocumentDecoderTest` case.
    func testV2MetaMarkerTextAndListAttrs() throws {
        let doc = try loadV2("list-marker-text.json")
        // The cambodian representation of 1860 plus the §3.1.5 suffix.
        XCTAssertEqual(try byName(doc, "Marker_BakedCambodian").meta?.markerText, "\u{17E1}\u{17E8}\u{17E6}\u{17E0}.")
        // §4 range + §7.1.4 fallback resolved upstream, not here.
        XCTAssertEqual(try byName(doc, "Marker_ArmenianOutOfRange").meta?.markerText, "10000.")
        // The ordered-list counter origin rides the disjoint ol/li lane.
        XCTAssertEqual(try byName(doc, "Marker_OrderedList").meta?.attrs?.start, "1860")
        XCTAssertEqual(try byName(doc, "Marker_ItemValueOverride").meta?.attrs?.value, "4")
        // A §6.1 bullet is out of the bake's scope BY DESIGN — no key at
        // all, which is what keeps this runtime on its own marker table.
        XCTAssertNil(try byName(doc, "Marker_BulletOutOfScope").meta?.markerText)
    }

    /// `markerText` is additive, not a licence to invent siblings: an
    /// unknown meta key next to it is still a hard error (spec 05 rule 3).
    func testV2UnknownMetaKeyStillErrorsBesideMarkerText() {
        let json = """
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [],
            "meta": { "markerText": "1.", "markerFont": "x" } } ] }
        """
        XCTAssertThrowsError(try decodeDoc(json))
    }

    // MARK: - wave-32 lane R: meta.runs (the ordered inline-content list)

    /// The ordered inline content decodes off the `inline-runs` golden, in
    /// DOCUMENT order, with the authoring-key reference intact.
    ///
    /// Why the DECODE is the unit under test rather than the renderer read:
    /// ComponentRenderer's consumption is a plan lookup per child, but a
    /// reader that rejected or dropped the key would make that lookup dead
    /// and silent. TWIN of the Compose `SchemaConformanceTest` case.
    func testV2MetaRunsDecodesInDocumentOrder() throws {
        let doc = try loadV2("inline-runs.json")
        let glued = try byName(doc, "Runs_TextGluedAcrossChild")
        let runs = try XCTUnwrap(glued.meta?.runs)
        XCTAssertEqual(runs.count, 3)
        XCTAssertEqual(runs[0].text, "the quick ")
        XCTAssertEqual(runs[1].child, "Runs_UnderlinedWord")
        XCTAssertEqual(runs[2].text, " fox")
        // Exactly one member per entry — the decoder proves the shape so the
        // renderer never has to ask which one wins.
        XCTAssertNil(runs[0].child)
        XCTAssertNil(runs[1].text)
        // `text` is STILL on the component carrying the pre-wave-32
        // concatenation — that is what makes meta.runs ADDITIVE, not a break.
        XCTAssertEqual(glued.text, "the quick fox")
        // The reference is the AUTHORING KEY (the child's `name`), never the
        // converter-minted id: the converter re-ids at the flatten boundary,
        // so a producer-written id would name nothing after the hop.
        let underlined = try byName(doc, "Runs_UnderlinedWord")
        XCTAssertEqual(runs[1].child, underlined.name)
        XCTAssertNotEqual(runs[1].child, underlined.id)
        // A whitespace-only run between two children is the inter-run word
        // space (spec 03 §4.1 rule 6) and survives decode un-trimmed.
        let wsOnly = try XCTUnwrap(try byName(doc, "Runs_WhitespaceOnlyRunBetweenChildren").meta?.runs)
        XCTAssertEqual(wsOnly[1].text, " ")
        // Absence stays nil — what keeps every other component on the
        // pre-wave-32 leading-text path.
        XCTAssertNil(underlined.meta?.runs)
    }

    /// Every malformed `meta.runs` shape is a HARD decode error. Each one
    /// would silently reorder painted content — the exact failure this wire
    /// exists to remove — so tolerating any of them would be worse than the
    /// bail it replaced.
    func testV2MetaRunsStrictness() {
        let head = """
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [], "meta": { "runs":
        """
        // Not an array / empty array / non-object entry / both keys /
        // neither key / unknown key / empty child key.
        for bad in ["{\"text\":\"x\"}", "[]", "[\"x\"]",
                    "[{\"text\":\"a\",\"child\":\"b\"}]", "[{}]",
                    "[{\"tail\":\"a\"}]", "[{\"child\":\"\"}]"] {
            XCTAssertThrowsError(try decodeDoc(head + bad + " } } ] }"),
                                 "meta.runs \(bad) must be a hard decode error")
        }
        // …but the EMPTY STRING is a legal `text`: a producer may emit one
        // and the renderer simply paints nothing for it.
        XCTAssertNoThrow(try decodeDoc(head + "[{\"text\":\"\"},{\"child\":\"b\"}] } } ] }"))
    }

    /// `runs` is additive, not a licence to invent siblings: an unknown meta
    /// key next to it is still a hard error (spec 05 rule 3).
    func testV2UnknownMetaKeyStillErrorsBesideRuns() {
        let json = """
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [],
            "meta": { "runs": [ { "text": "x" } ], "runFont": "y" } } ] }
        """
        XCTAssertThrowsError(try decodeDoc(json))
    }

    // MARK: - v2 strictness: children is a hard error

    func testV2ChildrenKeyIsHardError() {
        // The one sanctioned break: `children` does not exist in v2.
        let json = """
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [],
            "children": [ { "id": "b-1", "name": "B", "properties": [] } ] }
        ] }
        """
        XCTAssertThrowsError(try decodeDoc(json),
                             "children inside a v2 document must be a hard decode error")
    }

    // MARK: - v2 strictness: unknown envelope keys error

    func testV2UnknownEnvelopeKeysError() {
        // Document level (spec 05 rule 2).
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [], "extra": 1 }
        """), "unknown document key must error")
        // Component level (additionalProperties:false).
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [], "_text": "x" }
        ] }
        """), "v1 underscore spelling inside a v2 document must error")
        // Slot level.
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [],
            "slot": { "parent": "p-1", "order": 3 } }
        ] }
        """), "unknown slot key must error")
        // meta level, including the minProperties:1 rule.
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [], "meta": {} }
        ] }
        """), "empty meta must error (minProperties 1)")
    }

    // MARK: - v2 strictness: version pair

    func testV2VersionPairRules() {
        // minReaderVersion above what this reader implements → refuse.
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 3, "minReaderVersion": 3, "components": [] }
        """), "must refuse minReaderVersion > 2")
        // Version pair is mandatory once irVersion appears.
        XCTAssertThrowsError(try decodeDoc("""
        { "irVersion": 2, "components": [] }
        """), "missing minReaderVersion must error")
    }

    // MARK: - v2 tolerance: unknown property types decode

    func testV2UnknownPropertyTolerance() throws {
        // The v2 mirror golden keeps the FrobnicateCorner unknown type —
        // the {type,data} envelope decodes and dispatch skips it later
        // (spec 05 rule 1).
        let doc = try loadV2("unknown-property-tolerance.json")
        let mixed = try byName(doc, "Unknown_MixedWithKnown")
        XCTAssertEqual(mixed.properties[0].type, "FrobnicateCorner")
        XCTAssertEqual(mixed.properties[1].type, "Width")
        XCTAssertEqual(asDouble(member(mixed.properties[1].data, "px")), 100.0)
    }

    // MARK: - v2 composer edge cases

    func testV2DanglingSlotParentBecomesRoot() throws {
        // spec 03: dangling slot.parent → treat as root + warning —
        // never a crash, never a dropped component.
        let doc = try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [] },
          { "id": "b-1", "name": "B", "properties": [],
            "slot": { "parent": "ghost-9" } }
        ] }
        """)
        XCTAssertEqual(doc.components.map(\.name), ["A", "B"], "dangler must be promoted to root")
        // The slot itself still round-trips even when dangling.
        XCTAssertEqual(doc.components[1].slot?.parent, "ghost-9")
    }

    func testV2ModeBZeroSlotDocument() throws {
        // Mode B: no slot fields at all — composition supplied
        // externally; every entry is a root and engines need no change.
        let doc = try decodeDoc("""
        { "irVersion": 2, "minReaderVersion": 2, "components": [
          { "id": "a-1", "name": "A", "properties": [] },
          { "id": "b-1", "name": "B", "properties": [] }
        ] }
        """)
        XCTAssertEqual(doc.components.count, 2)
        XCTAssertTrue(doc.components.allSatisfy { $0.children == nil && $0.slot == nil })
    }

    // MARK: - v2 placement claims (the ITEM-scope contract)

    func testV2PlacementClaims() throws {
        // placement-claims.json pins the wire decision: ITEM properties
        // are ordinary {type,data} envelopes on the CHILD; the runtime
        // routes them to ItemPlacement parent-data.
        let doc = try loadV2("placement-claims.json")
        // Two hosts (grid + flex), six claiming children.
        XCTAssertEqual(doc.components.count, 2)
        // Grid line claims: grid-column 1/3 + grid-row-start 2.
        let lineClaim = try byName(doc, "line-claim__1")
        let linePlacement = ItemPlacementExtractor.extract(from: lineClaim.properties)
        XCTAssertEqual(linePlacement.grid.request.colStart, 1)
        XCTAssertEqual(linePlacement.grid.request.colEnd, 3)
        XCTAssertEqual(linePlacement.grid.request.rowStart, 2)
        // Span claim: grid-column-start: span 2.
        let spanClaim = try byName(doc, "span-claim__2")
        let spanPlacement = ItemPlacementExtractor.extract(from: spanClaim.properties)
        XCTAssertEqual(spanPlacement.grid.request.colSpan, 2)
        XCTAssertEqual(spanPlacement.grid.request.rowStart, 1)
        // Flex claims: grow 1 / shrink 0 / basis 40px / align-self center.
        let flexClaim = try byName(doc, "flex-claim__0")
        let flexPlacement = ItemPlacementExtractor.extract(from: flexClaim.properties)
        XCTAssertEqual(flexPlacement.flex.grow, 1)
        XCTAssertEqual(flexPlacement.flex.shrink, 0)
        XCTAssertEqual(flexPlacement.flex.basisPx, 40)
        XCTAssertEqual(flexPlacement.flex.alignSelf, .center)
        // Paint claim: z-index 3.
        let paintClaim = try byName(doc, "paint-claim__1")
        let paintPlacement = ItemPlacementExtractor.extract(from: paintClaim.properties)
        XCTAssertEqual(paintPlacement.paint.zIndex, 3)
    }

    // MARK: - per-family sentinels (v1 window)

    func testLengthsAllForms() throws {
        let doc = try load("lengths-all-forms.json")
        // {"px":16.0} → 16 through the generic IRValue payload.
        let px = try byName(doc, "Lengths_PxOnly")
        let padTop = try XCTUnwrap(px.properties.first { $0.type == "PaddingTop" })
        XCTAssertEqual(asDouble(member(padTop.data, "px")), 16.0)
        // Dual storage: px present AND original {v:12,u:PT} preserved.
        let dual = try byName(doc, "Lengths_DualStorage")
        let padRight = try XCTUnwrap(dual.properties.first { $0.type == "PaddingRight" })
        XCTAssertEqual(asDouble(member(padRight.data, "px")), 16.0)
        if case .string(let unit)? = member(try XCTUnwrap(member(padRight.data, "original")), "u") {
            XCTAssertEqual(unit, "PT")
        } else { XCTFail("original.u missing") }
        // Relative EM: px MUST be absent (runtime-dependent — spec 02).
        let rel = try byName(doc, "Lengths_RelativeOriginalOnly")
        let padBottom = try XCTUnwrap(rel.properties.first { $0.type == "PaddingBottom" })
        XCTAssertNil(member(padBottom.data, "px"))
        // Escapes: raw number percent, raw string keyword.
        let esc = try byName(doc, "Lengths_PercentAndKeyword")
        XCTAssertEqual(asDouble(esc.properties.first { $0.type == "PaddingLeft" }?.data), 10.0)
        if case .string(let kw)? = esc.properties.first(where: { $0.type == "MarginLeft" })?.data {
            XCTAssertEqual(kw, "auto")
        } else { XCTFail("MarginLeft escape must be the raw string 'auto'") }
    }

    func testColors() throws {
        let doc = try load("colors.json")
        let bg = try XCTUnwrap(try byName(doc, "Colors_StaticForms").properties.first { $0.type == "BackgroundColor" })
        let srgb = try XCTUnwrap(member(bg.data, "srgb"))
        XCTAssertEqual(try XCTUnwrap(asDouble(member(srgb, "r"))), 0.20392156862745098, accuracy: 1e-12)
        // Dynamic currentColor: srgb absent by contract (spec 02).
        let accent = try XCTUnwrap(try byName(doc, "Colors_SpecialKeywords").properties.first { $0.type == "AccentColor" })
        XCTAssertNil(member(accent.data, "srgb"))
    }

    func testShapeRadiusAsymmetry() throws {
        let doc = try load("shape-radius.json")
        // Keyword branch: raw string under "r".
        let kw = try byName(doc, "Shape_Circle_Keyword").properties[0]
        if case .string(let r)? = member(kw.data, "r") {
            XCTAssertEqual(r, "farthest-side")   // survives verbatim
        } else { XCTFail("keyword radius must be a string") }
        // Length branch: deep-flattened — px inlined, r key gone (spec 02).
        let len = try byName(doc, "Shape_Circle_Length").properties[0]
        XCTAssertEqual(asDouble(member(len.data, "px")), 40.0)
        XCTAssertNil(member(len.data, "r"))
    }

    func testTextAndRole() throws {
        let doc = try load("text-and-role.json")
        // v1 `_text` translates to the v2 in-memory home `text`.
        XCTAssertEqual(try byName(doc, "TextRole_BodyRoot").text, "Test passes if this text is green")
        XCTAssertEqual(try byName(doc, "TextRole_EmptyStringText").text, "")  // "" meaningful, not nil
        XCTAssertNil(try byName(doc, "TextRole_NoMetadata").text)
        // v1 `_role` now translates to meta.role instead of dropping —
        // the spec-04 model gap is closed by the v2 reader work.
        XCTAssertEqual(try byName(doc, "TextRole_BodyRoot").meta?.role, "body-root")
    }

    func testChildrenNesting() throws {
        let doc = try load("children-nesting.json")
        let parent = try byName(doc, "Parent")
        let child = try XCTUnwrap(parent.children?.first)
        XCTAssertEqual(child.name, "child__0")                 // map key became name (spec 03)
        XCTAssertEqual(child.children?.first?.text, "leaf text")
        XCTAssertNil(try byName(doc, "LeafSibling").children)  // omit-when-empty, never []
    }

    func testSelectorsMedia() throws {
        let doc = try load("selectors-media.json")
        let c = try byName(doc, "SelMedia_Both")
        XCTAssertEqual(c.selectors?.first?.condition, "hover") // no leading colon (spec 01)
        XCTAssertEqual(c.selectors?.first?.properties.first?.type, "BackgroundColor")
        XCTAssertEqual(c.media?.first?.query, "(min-width: 768px)")
        XCTAssertNil(try byName(doc, "SelMedia_OmittedWhenEmpty").selectors)
    }

    func testUnknownPropertyTolerance() throws {
        let doc = try load("unknown-property-tolerance.json")
        let mixed = try byName(doc, "Unknown_MixedWithKnown")
        // Unknown types decode into the generic envelope and are skipped by
        // dispatch — never a decode failure (spec 05 rule 1).
        XCTAssertEqual(mixed.properties[0].type, "FrobnicateCorner")
        XCTAssertEqual(mixed.properties[1].type, "Width")
        XCTAssertEqual(asDouble(member(mixed.properties[1].data, "px")), 100.0)
        // Generic keeps its _unmapped marker.
        let generic = try byName(doc, "Unknown_GenericEnvelope").properties[0]
        XCTAssertEqual(generic.type, "Generic")
        if case .bool(let flag)? = member(generic.data, "_unmapped") {
            XCTAssertTrue(flag)
        } else { XCTFail("_unmapped marker missing") }
    }

    func testPropertyEnvelopeEdges() throws {
        let doc = try load("property-envelope-edge.json")
        let prim = try byName(doc, "Envelope_PrimitiveData")
        if case .string(let s)? = prim.properties.first(where: { $0.type == "MarginLeft" })?.data {
            XCTAssertEqual(s, "auto")
        } else { XCTFail("MarginLeft data must be a raw string") }
        XCTAssertEqual(asDouble(prim.properties.first { $0.type == "FontWeight" }?.data), 700.0)
        // Empty-object data decodes to an empty object, not a crash.
        if case .object(let o)? = try byName(doc, "Envelope_EmptyObjectData").properties.first?.data {
            XCTAssertTrue(o.isEmpty)
        } else { XCTFail("empty-object data must decode as .object([:])") }
        // Array data: TransitionDelay [{ms:150}].
        let delays = try XCTUnwrap(try byName(doc, "Envelope_ArrayData").properties.first { $0.type == "TransitionDelay" })
        if case .array(let items) = delays.data {
            XCTAssertEqual(asDouble(member(items[0], "ms")), 150.0)
        } else { XCTFail("TransitionDelay data must be an array") }
        XCTAssertTrue(try byName(doc, "Envelope_EmptyPropertiesList").properties.isEmpty)
    }

    func testTransformList() throws {
        let doc = try load("transform-list.json")
        let transform = try byName(doc, "Transform_FunctionList").properties[0]
        guard case .array(let fns)? = member(transform.data, "list") else {
            return XCTFail("transform data.list must be an array")
        }
        if case .string(let fn)? = member(fns[1], "fn") { XCTAssertEqual(fn, "rotate") }
        else { XCTFail("fn missing") }
        XCTAssertEqual(asDouble(member(try XCTUnwrap(member(fns[1], "a")), "deg")), 45.0)
        // Rotate dual storage: 0.5turn normalized to 180deg.
        let rotate = try byName(doc, "Transform_RotateAngleDual").properties[0]
        XCTAssertEqual(asDouble(member(rotate.data, "deg")), 180.0)
    }

    // MARK: - wave-34 lane F2: document-level @font-face (spec 01 §5)

    func testV2FontFaces() throws {
        let doc = try loadV2("font-faces.json")
        let faces = try XCTUnwrap(doc.fontFaces, "font-faces.json must carry fontFaces")
        // ORDER is payload: css-fonts-4 §4.1 makes a later face with the
        // same (family, weight, style) win, so a reordering decode would
        // silently change which file renders.
        XCTAssertEqual(faces.count, 2)
        XCTAssertEqual(faces[0].family, "test")
        XCTAssertEqual(faces[0].src,
                       "css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff")
        // Omitted descriptors stay nil — never substituted with the
        // §4.4/§4.5 initial literal, so "omitted" and "explicitly normal"
        // stay distinguishable for the registration hop that does not exist
        // on this platform yet.
        XCTAssertNil(faces[0].weight)
        XCTAssertNil(faces[0].style)
        // The descriptors that cannot be normalised ride AS AUTHORED: a
        // weight RANGE has no numeric 100–900 form, an oblique ANGLE no
        // keyword form.
        XCTAssertEqual(faces[1].weight, "400 700")
        XCTAssertEqual(faces[1].style, "oblique 20deg")
    }

    func testV2FontFacesAbsentIsNil() throws {
        // Absent-vs-empty discipline: omit-when-empty on the wire means
        // every pre-wave-34 golden must come back nil, not [].
        XCTAssertNil(try loadV2("text-and-role.json").fontFaces)
        // …and a v1 document structurally cannot carry the key.
        XCTAssertNil(try load("text-and-role.json").fontFaces)
    }

    func testV2FontFacesStrictErrors() throws {
        // Strict even though the SwiftUI renderer does not register faces
        // yet: the spec-05 tolerance rule covers unknown property TYPES, not
        // a malformed envelope structure this reader claims to speak.
        let head = #"{"irVersion":2,"minReaderVersion":2,"components":[],"#
        XCTAssertThrowsError(try decodeDoc(head + #""fontFaces":[]}"#),
                             "empty fontFaces must fail (schema minItems 1)")
        XCTAssertThrowsError(try decodeDoc(head + #""fontFaces":[{"src":"a.woff"}]}"#),
                             "fontFaces entry without family must fail")
        XCTAssertThrowsError(try decodeDoc(head + #""fontFaces":[{"family":"x"}]}"#),
                             "fontFaces entry without src must fail")
        XCTAssertThrowsError(try decodeDoc(head + #""fontFaces":[{"family":"","src":"a.woff"}]}"#),
                             "empty family must fail")
        XCTAssertThrowsError(
            try decodeDoc(head + #""fontFaces":[{"family":"x","src":"a.woff","format":"woff"}]}"#),
            "unknown fontFaces entry key must fail (additionalProperties false)")
    }

    func testFontWeightKeywords() throws {
        let doc = try load("font-weight-keywords.json")
        let bold = try byName(doc, "FontWeight_BoldKeyword").properties[0]
        XCTAssertEqual(asDouble(member(bold.data, "weight")), 700.0)
        if case .string(let original)? = member(bold.data, "original") {
            XCTAssertEqual(original, "bold")
        } else { XCTFail("keyword dual storage must keep the original") }
        // Numeric primitive form: bare 700.
        XCTAssertEqual(asDouble(try byName(doc, "FontWeight_NumericPrimitive").properties[0].data), 700.0)
    }
}
