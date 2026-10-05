//
//  ListMarkerEmptyItemTests.swift
//  Wave 52, lane L6 (T7's native half) — pins for ListMarkerEmptyItem and
//  the hang's `itemIsEmpty` height. TWIN of runtimes/compose/src/test/java/
//  com/styleconverter/runtime/lists/ListMarkerEmptyItemTest.kt.
//
//  Every item is a VERBATIM wave51-fix per-test IR `<li>`: cssom-pad-setter-
//  invalid `__0__0` after L5's F-E (empty bag — EMPTY) and before it
//  (Width/Height 100 — NOT empty); counter-list-item-2 `__0` (baked ::before
//  "1" — NOT empty); an abspos-only counter-styles item (Height 31.25 — NOT
//  empty); name-case-sensitivity `__0__0` (Float + list-style only — EMPTY).
//
//  MUTATION PROOF (EXECUTED 2026-10-05 by tools/titan/results/
//  wave52-counters-and-lists/mutate.py, entry `empty-pseudos-swift` in
//  mutations.log): dropping the `pseudos` clause turned
//  testABakedBeforeCounterIsContent red; the others stayed green.
//

import XCTest
@testable import StyleConverterRuntime

final class ListMarkerEmptyItemTests: XCTestCase {

    private func li(_ props: String = "[]", pseudos: String? = nil, text: String? = nil) throws -> IRComponent {
        let extra = (pseudos.map { ",\"pseudos\":\($0)" } ?? "") + (text.map { ",\"text\":\"\($0)\"" } ?? "")
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"li","name":"li","properties":\(props),"meta":{"sourceTag":"li"}\(extra)}
        """.utf8))
    }
    private let placeholder = """
    [{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}]
    """

    func testThePostFEcssomItemIsEmptyAndTakesTheRow() throws {
        let item = try li()
        XCTAssertTrue(ListMarkerEmptyItem.isEmpty(item))
        // The plain overlay rule WOULD overlay it (inside, no text)…
        XCTAssertTrue(ListMarkerRow.rendersInsideOverlay(
            position: .inside, itemExposesTextBaseline: ListMarkerRow.itemExposesTextBaseline(item)))
        // …the emptiness-aware rule does not: the marker must size the line.
        XCTAssertFalse(ListMarkerEmptyItem.rendersInsideOverlay(position: .inside, item: item))
    }

    func testADeclaredBlockSizeKeepsTheOverlay() throws {
        XCTAssertFalse(ListMarkerEmptyItem.isEmpty(try li(placeholder)))
        XCTAssertTrue(ListMarkerEmptyItem.rendersInsideOverlay(position: .inside, item: try li(placeholder)))
        XCTAssertFalse(ListMarkerEmptyItem.isEmpty(try li("[{\"type\":\"Height\",\"data\":{\"type\":\"length\",\"px\":31.25}}]")))
    }

    func testABakedBeforeCounterIsContent() throws {
        // counter-list-item-2's `<li>`: `pseudos.before._text = "1"`.
        XCTAssertFalse(ListMarkerEmptyItem.isEmpty(
            try li(pseudos: "{\"before\":{\"properties\":{\"content\":\"\\\"1\\\"\"},\"_text\":\"1\"}}")))
    }

    func testTextIsContentListStyleIsNot() throws {
        XCTAssertFalse(ListMarkerEmptyItem.isEmpty(try li(text: "foo")))
        // name-case-sensitivity: Float + ListStyleType + ListStylePosition only.
        XCTAssertTrue(ListMarkerEmptyItem.isEmpty(try li("""
        [{"type":"Float","data":"LEFT"},{"type":"ListStyleType","data":"Hiragana"},{"type":"ListStylePosition","data":"INSIDE"}]
        """)))
    }

    func testAnOutsideHangOnAnEmptyItemIsTheMarkersLineTall() {
        // Marker 30×20 (a "001." at 16px), empty item 0 tall.
        let empty = ListMarkerOutsideHang.place(markerSize: CGSize(width: 30, height: 20), markerBaseline: nil,
                                                itemSize: CGSize(width: 158, height: 0), itemBaseline: nil,
                                                gap: 4, alignsByBaseline: false, itemIsEmpty: true)
        XCTAssertEqual(empty.height, 20)
        // Not empty → the item's size only (the T5 identity).
        let full = ListMarkerOutsideHang.place(markerSize: CGSize(width: 30, height: 20), markerBaseline: nil,
                                               itemSize: CGSize(width: 158, height: 0), itemBaseline: nil,
                                               gap: 4, alignsByBaseline: false)
        XCTAssertEqual(full.height, 0)
    }
}
