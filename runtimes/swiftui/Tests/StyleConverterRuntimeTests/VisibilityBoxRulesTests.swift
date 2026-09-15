//
//  VisibilityBoxRulesTests.swift
//  Wave 50, lane B11 — pins for the CSS 2.2 §11.2 `visibility` box rules.
//
//  Every wire payload below is VERBATIM from the frozen corpus run
//  tools/titan/runs/wave49-final/sections/css-view-transitions/per-test-ir/
//  wpt__css-view-transitions__capture-with-visibility-mixed-descendants.json
//  (`Visibility` carries a bare uppercase keyword string; `Display` the
//  same). There is NO Compose twin for these pins: the Kotlin rule table
//  lane B11 wrote had zero production callers and is deleted in wave 50
//  (fix lane F1), so its test goes with it. These are therefore the ONLY
//  pins for §11.2's box rules, and the Compose port is tracked on the
//  BACKLOG behind the table renderer's missing track-removal path — see
//  VisibilityBoxRules.swift's TWIN STATUS banner.
//

import XCTest
@testable import StyleConverterRuntime

final class VisibilityBoxRulesTests: XCTestCase {

    // Live-wire helper: one IRProperty whose data is a bare keyword string.
    private func prop(_ type: String, _ keyword: String) -> IRProperty {
        IRProperty(type: type, data: .string(keyword))
    }

    func testMixedDescendantsWireParentHiddenChildVisible() {
        // Component …mixed-descendants__0__0-064 (the red 500x500 box).
        let parent = [prop("Visibility", "HIDDEN"), prop("Display", "BLOCK")]
        // Component …mixed-descendants__0__0__0-065 (the green 10x10 square).
        let child = [prop("Visibility", "VISIBLE"), prop("Display", "BLOCK")]
        XCTAssertEqual(VisibilityBoxRules.declaredVisibility(parent), .hidden)
        XCTAssertEqual(VisibilityBoxRules.declaredVisibility(child), .visible)
        // The parent suppresses its OWN ink only…
        let parentUsed = VisibilityBoxRules.resolve(
            declared: VisibilityBoxRules.declaredVisibility(parent), inherited: .visible)
        XCTAssertEqual(
            VisibilityBoxRules.treatment(parentUsed, isTableTrackBox: false), .inkSuppressed)
        // …and the child's own declaration beats the inherited HIDDEN, so
        // the green square paints — the pixel wave49-final's iOS and Android
        // captures are missing and the frozen ref has.
        let childUsed = VisibilityBoxRules.resolve(
            declared: VisibilityBoxRules.declaredVisibility(child),
            inherited: VisibilityBoxRules.childInherits(parentUsed))
        XCTAssertEqual(childUsed, .visible)
        XCTAssertEqual(
            VisibilityBoxRules.treatment(childUsed, isTableTrackBox: false), .painted)
        // So the cheap subtree-opacity shortcut is NOT equivalent here.
        XCTAssertFalse(VisibilityBoxRules.subtreeAlphaEquivalent(
            VisibilityBoxRules.treatment(parentUsed, isTableTrackBox: false),
            anyDescendantDeclaresVisible: true))
    }

    func testHiddenChildWireKeepsTheOpacityShortcut() {
        // Component capture-with-visibility-hidden-child__0__0-057 — a
        // `visibility: hidden` 500x500 with NO children on the wire. That
        // cell PASSES on all three today (wave49-final ios-ref/android-ref
        // ssim 1.0000), so the shortcut must stay available for it.
        let hidden = [prop("Visibility", "HIDDEN"), prop("Display", "BLOCK")]
        let used = VisibilityBoxRules.resolve(
            declared: VisibilityBoxRules.declaredVisibility(hidden), inherited: .visible)
        XCTAssertTrue(VisibilityBoxRules.subtreeAlphaEquivalent(
            VisibilityBoxRules.treatment(used, isTableTrackBox: false),
            anyDescendantDeclaresVisible: false))
    }

    func testUndeclaredDescendantInheritsHidden() {
        // The corpus wire does NOT dump a computed `visibility` on every
        // component — tools/titan/extract-fixture.mjs carries it only as a
        // root-inherited trigger prop — so a descendant of a hidden box
        // usually declares nothing at all. Undeclared must mean INHERIT.
        let undeclared = [prop("Display", "BLOCK")]
        XCTAssertNil(VisibilityBoxRules.declaredVisibility(undeclared))
        let used = VisibilityBoxRules.resolve(
            declared: VisibilityBoxRules.declaredVisibility(undeclared), inherited: .hidden)
        XCTAssertEqual(used, .hidden)
        XCTAssertEqual(
            VisibilityBoxRules.treatment(used, isTableTrackBox: false), .inkSuppressed)
    }

    func testCollapseOffATableTrackIsTreatedAsHidden() {
        // CSS 2.2 §11.2: "for other elements, `collapse` is treated the same
        // as `hidden`". THIS is the clause the old applier violated —
        // `.frame(width: 0, height: 0)` removed the box for every element.
        let block = [prop("Visibility", "COLLAPSE"), prop("Display", "BLOCK")]
        XCTAssertFalse(VisibilityBoxRules.isTableTrackBox(block))
        let cfg = VisibilityExtractor.extract(from: block)
        XCTAssertEqual(cfg?.visibility, .collapse)
        // The extractor must ALSO have classified the box, and a BLOCK is
        // not a track — this is what routes the applier to `.inkSuppressed`.
        XCTAssertEqual(cfg?.isTableTrackBox, false)
        let used = VisibilityBoxRules.resolve(
            declared: VisibilityBoxRules.declaredVisibility(block), inherited: .visible)
        XCTAssertEqual(
            VisibilityBoxRules.treatment(used, isTableTrackBox: false), .inkSuppressed)
    }

    func testCollapseOnATableTrackRemovesTheBox() {
        // The other half of §11.2: on a row / row group / column / column
        // group the track is REMOVED.
        for kw in ["TABLE_ROW", "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP",
                   "TABLE_FOOTER_GROUP", "TABLE_COLUMN", "TABLE_COLUMN_GROUP"] {
            let track = [prop("Visibility", "COLLAPSE"), prop("Display", kw)]
            XCTAssertTrue(VisibilityBoxRules.isTableTrackBox(track), kw)
            // End to end through the extractor, because the applier reads
            // the flag off the config rather than the property list.
            XCTAssertEqual(VisibilityExtractor.extract(from: track)?.isTableTrackBox, true, kw)
            let used = VisibilityBoxRules.resolve(
                declared: VisibilityBoxRules.declaredVisibility(track), inherited: .visible)
            XCTAssertEqual(
                VisibilityBoxRules.treatment(used, isTableTrackBox: true), .boxRemoved, kw)
        }
        // A CELL is not a track — §11.2 removes rows and columns, not cells.
        XCTAssertFalse(VisibilityBoxRules.isTableTrackBox([prop("Display", "TABLE_CELL")]))
    }

    func testHyphenWireSpellingClassifiesIdentically() {
        // TableBoxTree accepts both spellings because the wire has carried
        // both; this classifier must not disagree with it on the same input.
        XCTAssertTrue(VisibilityBoxRules.isTableTrackBox([prop("Display", "table-row")]))
        XCTAssertTrue(
            VisibilityBoxRules.isTableTrackBox([prop("Display", "table-column-group")]))
    }

    func testUnrecognisedKeywordInheritsInsteadOfUnHiding() {
        // Invalid declarations fall back to the inherited value in CSS; the
        // failure mode we must never take is "unknown ⇒ visible", which
        // would paint a box its ancestor hid.
        let junk = [prop("Visibility", "SOMETHING_ELSE")]
        XCTAssertNil(VisibilityBoxRules.declaredVisibility(junk))
        XCTAssertEqual(
            VisibilityBoxRules.resolve(
                declared: VisibilityBoxRules.declaredVisibility(junk), inherited: .hidden),
            .hidden)
    }

    func testDisplayAloneDoesNotMakeAVisibilityConfig() {
        // The `isTableTrackBox` read must never set `touched`: a component
        // that declares only `display: table-row` has no visibility and no
        // overflow, so the extractor must still return nil and the applier
        // stay the identity.
        XCTAssertNil(VisibilityExtractor.extract(from: [prop("Display", "TABLE_ROW")]))
    }
}
