//
//  TableSeparatedTracksTests.swift
//  Wave-34 lane T (T1) — pins for the css-tables-3 §2.1 role table and
//  the CSS 2.1 §17.6.1 separated-borders track model.
//
//  Everything under test is pure over the IR, so it pins without a
//  render surface — the same discipline AbsposCbUsedHeightTests and
//  FloatRowPackingTests follow.
//
//  The shape under test is the exact wire from the frozen wave33-final
//  gate, css-tables/abspos-container-change-dynamic-001:
//    <table>  (Display TABLE, sourceTag "table", 31.5156 × 26)
//      └ tbody (Display TABLE_ROW_GROUP, 27.5156 × 22)
//         └ tr  (Display TABLE_ROW,      27.5156 × 22)
//            ├ td "A" (Display TABLE_CELL, 11.0469 + 1+1 padding)
//            └ td "B" (Display TABLE_CELL, RELATIVE, 10.4688 + 1+1)
//                 └ 100×100 lime ABSOLUTE at top:0 left:0
//  The reference paints the lime box at (33,18); iOS painted (16,38).
//

import XCTest
@testable import StyleConverterRuntime

final class TableSeparatedTracksTests: XCTestCase {

    // MARK: - Wire helpers

    /// One IRProperty carrying a decoded literal, the shape the reader emits.
    private func prop(_ type: String, _ value: IRValue) -> IRProperty {
        IRProperty(type: type, data: value)
    }

    /// A keyword-valued declaration (`"TABLE"`), the bare-string wire form.
    private func kw(_ type: String, _ keyword: String) -> IRProperty {
        prop(type, .string(keyword))
    }

    // MARK: - Role classification (css-tables-3 §2.1)

    func testUnderscoreAndHyphenDisplaySpellingsClassifyIdentically() {
        // The wire has carried both spellings across waves; neither is
        // worth a normalization pass, so roleOf accepts both — the same
        // contract the Kotlin twin's test pins.
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE_ROW_GROUP")]), .rowGroup)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "table-row-group")]), .rowGroup)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE_CELL")]), .cell)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "table-cell")]), .cell)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE_ROW")]), .row)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE")]), .table)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "INLINE_TABLE")]), .table)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE_CAPTION")]), .caption)
    }

    func testAllThreeRowGroupBoxesShareOneRole() {
        // §2.1 lists three group boxes; all three are transparent for row
        // ordering, so the runtime gives them one role.
        for kwName in ["TABLE_ROW_GROUP", "TABLE_HEADER_GROUP", "TABLE_FOOTER_GROUP"] {
            XCTAssertEqual(TableBoxTree.roleOf([kw("Display", kwName)]), .rowGroup, kwName)
        }
    }

    func testNonTableAndUndeclaredDisplayAreNotTableBoxes() {
        // The overwhelming majority of the corpus: no Display at all.
        XCTAssertEqual(TableBoxTree.roleOf([]), .none)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "BLOCK")]), .none)
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "FLEX")]), .none)
        // §2.1: a column / column-group generates NO cell boxes, so it is
        // deliberately not a table box for this runtime's purposes.
        XCTAssertEqual(TableBoxTree.roleOf([kw("Display", "TABLE_COLUMN")]), .none)
    }

    func testSourceTagIsNotASecondRoleChannel() {
        // Deliberate: reading meta.sourceTag here would re-classify every
        // <table>/<tr>/<td> in 17 frozen captures rather than the one this
        // was measured against. roleOf takes properties only — there is no
        // tag parameter to pass — and this test exists so a future widening
        // is a conscious edit, not a silent one.
        XCTAssertEqual(TableBoxTree.roleOf([kw("Position", "RELATIVE")]), .none)
    }

    // MARK: - Arrangement (CSS 2.1 §17.6.1)

    func testRowArrangesCellsInline() {
        // THE fix. Everything else in this file is scaffolding for it.
        XCTAssertEqual(TableSeparatedTracks.arrangement(.row), .inlineRow)
    }

    func testTableAndRowGroupStackInTheBlockDirection() {
        XCTAssertEqual(TableSeparatedTracks.arrangement(.table), .blockStack)
        XCTAssertEqual(TableSeparatedTracks.arrangement(.rowGroup), .blockStack)
    }

    func testCellAndCaptionFormNoTracks() {
        // §2.1 — both establish a BLOCK CONTAINER for their contents.
        XCTAssertEqual(TableSeparatedTracks.arrangement(.cell), .none)
        XCTAssertEqual(TableSeparatedTracks.arrangement(.caption), .none)
        XCTAssertEqual(TableSeparatedTracks.arrangement(.none), .none)
    }

    func testOnlyTheTableBoxCarriesTheOuterBand() {
        // A row group sits INSIDE the table's band; adding it again there
        // would double-count §17.6.1's edge spacing.
        XCTAssertTrue(TableSeparatedTracks.outerBandApplies(.table))
        XCTAssertFalse(TableSeparatedTracks.outerBandApplies(.rowGroup))
        XCTAssertFalse(TableSeparatedTracks.outerBandApplies(.row))
    }

    // MARK: - Used border-spacing

    func testBareTableElementGetsTheHtmlUaTwoPixels() {
        // abspos-container-change-dynamic-001's exact wire: Display TABLE,
        // meta.sourceTag "table", and NO BorderSpacing declaration — the
        // converter never serializes UA defaults, so the 2px has to come
        // from the HTML rendering UA sheet (§15.3.3 Tables).
        let used = TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE")], sourceTag: "table")
        XCTAssertEqual(used, TableSeparatedTracks.Spacing(horizontalPx: 2, verticalPx: 2))
        XCTAssertEqual(TableSeparatedTracks.htmlUaBorderSpacingPx, 2)
    }

    func testCssAuthoredDisplayTableOnADivGetsTheCssInitialZero() {
        // The UA rule targets the `<table>` ELEMENT, not the display type,
        // and `border-spacing`'s CSS initial value is 0 (§17.6.1). A
        // `display: table` div must NOT inherit the 2px.
        let used = TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE")], sourceTag: "div")
        XCTAssertEqual(used, TableSeparatedTracks.Spacing.zero)
        // …and the same when the wire carries no tag at all.
        XCTAssertEqual(
            TableSeparatedTracks.usedSpacing(properties: [kw("Display", "TABLE")],
                                             sourceTag: nil),
            TableSeparatedTracks.Spacing.zero)
    }

    func testDeclaredTwoValueSpacingBeatsTheUaDefault() {
        // Author beats user agent (CSS 2.1 §6.4.1). The wire shape is the
        // converter's, pinned live: border-spacing: 3px 5px →
        // {"type":"two-values","horizontal":{"px":3},"vertical":{"px":5}}.
        let declared = IRValue.object([
            "type": .string("two-values"),
            "horizontal": .object(["px": .double(3)]),
            "vertical": .object(["px": .double(5)]),
        ])
        let used = TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE"), prop("BorderSpacing", declared)],
            sourceTag: "table")
        XCTAssertEqual(used, TableSeparatedTracks.Spacing(horizontalPx: 3, verticalPx: 5))
    }

    func testDeclaredSingleSpacingAppliesToBothAxes() {
        // border-spacing: 4px → {"type":"single","px":4}.
        let declared = IRValue.object(["type": .string("single"), "px": .double(4)])
        let used = TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE"), prop("BorderSpacing", declared)],
            sourceTag: "table")
        XCTAssertEqual(used, TableSeparatedTracks.Spacing(horizontalPx: 4, verticalPx: 4))
    }

    func testCollapsedModelHasNoSpacingAtAll() {
        // §17.6.2 — "the border-spacing property is ignored". Nil, not
        // zero: the caller has to decide what a collapsed table does, and
        // a nil answer makes that decision explicit at the call site.
        XCTAssertNil(TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE"), kw("BorderCollapse", "COLLAPSE")],
            sourceTag: "table"))
        // `separate` (and an absent declaration) keep the separated model.
        XCTAssertNotNil(TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE"), kw("BorderCollapse", "SEPARATE")],
            sourceTag: "table"))
    }

    func testUnresolvableDeclaredSpacingFallsBackRatherThanInventingANumber() {
        // The wire's null-means-runtime-dependent contract: a calc()/em
        // border-spacing arrives with no px. Falling through to the UA
        // lane is honest; guessing a pixel count is not.
        let unresolved = IRValue.object(["type": .string("single")])
        let used = TableSeparatedTracks.usedSpacing(
            properties: [kw("Display", "TABLE"), prop("BorderSpacing", unresolved)],
            sourceTag: "table")
        XCTAssertEqual(used, TableSeparatedTracks.Spacing(horizontalPx: 2, verticalPx: 2))
    }

    // MARK: - Track arithmetic — the measured geometry

    func testRowCellOriginsReproduceTheReferenceLimeBoxX() {
        // The row's two cells, as border boxes: 11.0469 + 1 + 1 = 13.0469
        // and 10.4688 + 1 + 1 = 12.4688 (the wire's content widths plus the
        // HTML UA `td { padding: 1px }` the converter DOES serialize).
        let origins = TableSeparatedTracks.trackOrigins(
            extents: [13.0469, 12.4688], spacing: 2, band: 0)
        XCTAssertEqual(origins.count, 2)
        // Cell 1 flush at the row's content origin…
        XCTAssertEqual(origins[0], 0, accuracy: 0.0001)
        // …cell 2 one cell + one horizontal border-spacing along.
        XCTAssertEqual(origins[1], 15.0469, accuracy: 0.0001)
        // The table one level up adds its own 2px band, and the whole
        // table sits at the canvas's 16px pin: 16 + 2 + 15.0469 = 33.0469,
        // which is the reference's lime-box x of 33 after rounding. iOS
        // painted 16 — the row had stacked its cells vertically.
        XCTAssertEqual(16 + 2 + origins[1], 33.0469, accuracy: 0.0001)
    }

    func testTableBandPutsTheFirstRowAtTheReferenceY() {
        // One row inside the table box: band first, no inter-row gap.
        let origins = TableSeparatedTracks.trackOrigins(
            extents: [22], spacing: 2, band: 2)
        XCTAssertEqual(origins, [2])
        // 16 (canvas pin) + 2 (band) = 18 — the reference's lime-box y.
        // iOS painted 38: cell 1's full 22px border box below the row top.
        XCTAssertEqual(16 + origins[0], 18, accuracy: 0.0001)
    }

    func testTrackExtentReproducesTheDeclaredTableWidth() {
        // The converter serialized the table at 31.5156 wide and 26 tall.
        // Both fall straight out of §17.6.1's arithmetic, which is the
        // strongest available cross-check that the 2px UA default is the
        // number the reference browser actually used.
        XCTAssertEqual(
            TableSeparatedTracks.trackExtent(extents: [13.0469, 12.4688], spacing: 2, band: 2),
            31.5157, accuracy: 0.0002)
        XCTAssertEqual(
            TableSeparatedTracks.trackExtent(extents: [22], spacing: 2, band: 2),
            26, accuracy: 0.0001)
        // The ROW's own declared width, 27.5156, is the same tracks with
        // the table's band removed — the band belongs to the table only.
        XCTAssertEqual(
            TableSeparatedTracks.trackExtent(extents: [13.0469, 12.4688], spacing: 2, band: 0),
            27.5157, accuracy: 0.0002)
    }

    func testEmptyTrackListIsJustTheBand() {
        // An empty `<table>` is 2×2 in the reference, not 0×0.
        XCTAssertEqual(TableSeparatedTracks.trackOrigins(extents: [], spacing: 2, band: 2), [])
        XCTAssertEqual(
            TableSeparatedTracks.trackExtent(extents: [], spacing: 2, band: 2),
            4, accuracy: 0.0001)
    }

    func testSingleTrackTakesNoInterTrackSpacing() {
        // n−1 gaps: one track means zero gaps, whatever the spacing is.
        XCTAssertEqual(
            TableSeparatedTracks.trackExtent(extents: [50], spacing: 9, band: 0),
            50, accuracy: 0.0001)
    }
}
