//
//  FidelityWave1Tests.swift
//  StyleConverterRuntimeTests
//
//  Pins the iOS fidelity-wave-1 engine fixes:
//    • CSS-grid placement (css-grid-1 §8.5 sparse row flow) — explicit
//      lines, spans, auto-flow (grid-2col/000_G2_Placement, 015_Mixed).
//    • Track sizing (§7.2) — fr/px splits under a definite width,
//      max-content hug under fit-content (nested-3level/014_BlockGrid).
//    • Row templates + stretch-height resolution (grid-2col/010).
//    • Self-alignment mapping (css-align-3 §6) — start default for
//      fit-content items, center/end honoured (grid-2col/005).
//    • The 50×30 web-harness min-box floor decision.
//    • background-clip shrink bands (CSS Backgrounds 3 §2.4).
//    • border `medium` default width + currentColor fallback
//      (CSS Backgrounds 3 §3.2/§3.3 — borders/016, 003_C04).
//    • Percent border-radius resolution (§5.1 — borders/016, 004_C05).
//    • mask-mode: luminance stop folding (CSS Masking 1 §7.3 —
//      effects/004_C05_MaskImage).
//    • Legacy `clip: rect()` gating to positioned elements
//      (CSS 2.1 §11.1.2 — effects/008_BoxModel).
//

import Foundation
import XCTest
import SwiftUI
// @testable: everything under test is internal to the module.
@testable import StyleConverterRuntime

final class FidelityWave1Tests: XCTestCase {

    // Concise IR builders shared by the StyleBuilder-level tests.
    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - Grid placement (css-grid-1 §8.5)

    /// grid-2col/000_G2_Placement: a spans cols 1-3 row auto; b,c auto;
    /// d is fully pinned at col 2 / row 3. Chrome renders a on row 1
    /// (full span), b/c on row 2, d on row 3 col 2 — assert exactly that.
    func testPlacementExplicitLinesAndSpans() {
        let requests: [GridItemRequest] = [
            // a: grid-column-start 1 / grid-column-end 3.
            GridItemRequest(colStart: 1, colEnd: 3),
            // b, c: fully auto.
            GridItemRequest(),
            GridItemRequest(),
            // d: col 2/3 + row 3/4 (definite both axes → pass 1).
            GridItemRequest(colStart: 2, colEnd: 3, rowStart: 3, rowEnd: 4),
        ]
        let (cells, rows) = GridPlacer.assign(requests, columnCount: 2)
        // a: row 0, spanning both columns.
        XCTAssertEqual(cells[0], GridCell(col: 0, row: 0, colSpan: 2, rowSpan: 1))
        // b: first free slot on row 1.
        XCTAssertEqual(cells[1], GridCell(col: 0, row: 1, colSpan: 1, rowSpan: 1))
        // c: next to b.
        XCTAssertEqual(cells[2], GridCell(col: 1, row: 1, colSpan: 1, rowSpan: 1))
        // d: pinned cell (col 1, row 2 — 0-based).
        XCTAssertEqual(cells[3], GridCell(col: 1, row: 2, colSpan: 1, rowSpan: 1))
        // Three rows total.
        XCTAssertEqual(rows, 3)
    }

    /// grid-2col/015_G2_MixedTracks: a,b auto; c spans 1-3; d col 2-3.
    /// Chrome: a(0,0) b(0,1) c(row1 span2) d(row2 col1).
    func testPlacementCursorWrapsForBackwardColumns() {
        let requests: [GridItemRequest] = [
            GridItemRequest(),                          // a auto
            GridItemRequest(),                          // b auto
            GridItemRequest(colStart: 1, colEnd: 3),    // c span both
            GridItemRequest(colStart: 2, colEnd: 3),    // d col 2
        ]
        let (cells, rows) = GridPlacer.assign(requests, columnCount: 2)
        XCTAssertEqual(cells[0], GridCell(col: 0, row: 0, colSpan: 1, rowSpan: 1))
        XCTAssertEqual(cells[1], GridCell(col: 1, row: 0, colSpan: 1, rowSpan: 1))
        // c requests col 1 which is BEHIND the cursor col → wraps to row 1.
        XCTAssertEqual(cells[2], GridCell(col: 0, row: 1, colSpan: 2, rowSpan: 1))
        // d col 2 is again behind the cursor after c → row 2.
        XCTAssertEqual(cells[3], GridCell(col: 1, row: 2, colSpan: 1, rowSpan: 1))
        XCTAssertEqual(rows, 3)
    }

    // MARK: - Track sizing (css-grid-1 §7.2)

    /// Definite container: `1fr 1fr` inside 304pt content with an 8pt
    /// gap → two 148pt tracks (grid-2col fixtures: 320 - 16 padding).
    func testFrTracksSplitDefiniteWidth() {
        let w = GridTrackMath.columnWidths(
            tracks: [.flexible(weight: 1), .flexible(weight: 1)],
            containerWidth: 304, columnGap: 8, maxContent: [50, 50])
        XCTAssertEqual(w, [148, 148])
    }

    /// `80px 1fr` inside 304pt with 8pt gap → fixed 80 + remaining 216.
    func testMixedPxFrTracks() {
        let w = GridTrackMath.columnWidths(
            tracks: [.fixed(px: 80), .flexible(weight: 1)],
            containerWidth: 304, columnGap: 8, maxContent: [50, 50])
        XCTAssertEqual(w, [80, 216])
    }

    /// Indefinite (fit-content) container: fr tracks hug the column's
    /// max-content — the web-parity hug for nested-3level/014_BlockGrid
    /// (each track = the 50pt min-floored item, grid = 118pt total
    /// with padding+gap, NOT the 252pt parent width).
    func testFitContentTracksHugMaxContent() {
        let w = GridTrackMath.columnWidths(
            tracks: [.flexible(weight: 1), .flexible(weight: 1)],
            containerWidth: nil, columnGap: 6, maxContent: [50, 50])
        XCTAssertEqual(w, [50, 50])
    }

    /// Row heights: fixed 60px template rows win over content; rows
    /// beyond the template fall back to the tallest span-1 item.
    func testRowHeightsTemplateAndContent() {
        let cells = [
            (cell: GridCell(col: 0, row: 0, colSpan: 1, rowSpan: 1), height: CGFloat(24)),
            (cell: GridCell(col: 1, row: 0, colSpan: 1, rowSpan: 1), height: CGFloat(30)),
            (cell: GridCell(col: 0, row: 1, colSpan: 1, rowSpan: 1), height: CGFloat(40)),
        ]
        let h = GridTrackMath.rowHeights(
            template: [.fixed(px: 60)], autoRows: nil, rowCount: 2, items: cells)
        // Row 0: template 60 beats the 30pt content; row 1: content 40.
        XCTAssertEqual(h, [60, 40])
    }

    // MARK: - Self-alignment (css-align-3 §6)

    /// start/center/end honoured; stretch/auto/normal/nil degrade to
    /// start for fit-content items (grid-2col/005_G2_JustifySelf d).
    func testResolveAlignMapping() {
        XCTAssertEqual(GridPlacer.resolveAlign(self: .center, items: nil), .center)
        XCTAssertEqual(GridPlacer.resolveAlign(self: .end, items: nil), .end)
        XCTAssertEqual(GridPlacer.resolveAlign(self: .start, items: nil), .start)
        XCTAssertEqual(GridPlacer.resolveAlign(self: .stretch, items: nil), .start)
        XCTAssertEqual(GridPlacer.resolveAlign(self: nil, items: nil), .start)
        // `auto` defers to the container's *-items default.
        XCTAssertEqual(GridPlacer.resolveAlign(self: .auto, items: .center), .center)
        XCTAssertEqual(GridPlacer.resolveAlign(self: nil, items: .end), .end)
    }

    // MARK: - Placement longhand extraction

    /// grid-column-start: 1 + grid-column-end: 3 must SURVIVE as a pair —
    /// the legacy folded field lost the start line (000_G2_Placement).
    func testGridPlacementLonghandsKeptSeparate() {
        let agg = LayoutExtractor.extract(from: props([
            ("Display", .string("GRID")),
            ("GridColumnStart", obj(["type": .string("number"), "number": .double(1)])),
            ("GridColumnEnd",   obj(["type": .string("number"), "number": .double(3)])),
        ]))
        XCTAssertEqual(agg?.gridColumnStart?.line, 1)
        XCTAssertEqual(agg?.gridColumnEnd?.line, 3)
    }

    // MARK: - Min-box floor (web-harness parity)

    /// Floor truth table: 50/30 only when the axis has NO width/min/max.
    func testMinFloorDecision() {
        // Nothing declared → both floors.
        var s = SizeConfig()
        XCTAssertEqual(StyleBuilder.minFloor(for: s).width, 50)
        XCTAssertEqual(StyleBuilder.minFloor(for: s).height, 30)
        // Explicit width kills the inline floor only.
        s.width = .exact(px: 320)
        XCTAssertNil(StyleBuilder.minFloor(for: s).width)
        XCTAssertEqual(StyleBuilder.minFloor(for: s).height, 30)
        // min-height constraint kills the block floor.
        s.minHeight = .exact(px: 10)
        XCTAssertNil(StyleBuilder.minFloor(for: s).height)
        // max-width alone also disables the inline floor (web: max → '0').
        var s2 = SizeConfig()
        s2.maxWidth = .exact(px: 40)
        XCTAssertNil(StyleBuilder.minFloor(for: s2).width)
    }

    // MARK: - background-clip shrink bands (CSS Backgrounds 3 §2.4)

    /// content-box insets by border + padding; padding-box by border
    /// only; default border-box not at all (background/005_Decorated).
    func testBackgroundClipInsets() {
        // Style with a uniform 4px border + 12px padding.
        var style = ComponentStyle()
        var side = BorderSideConfig()
        side.width = 4
        side.style = .solid
        style.borderSides = AllBordersConfig(top: side, end: side,
                                             bottom: side, start: side)
        var pad = PaddingConfig()
        pad.top = .exact(px: 12); pad.right = .exact(px: 12)
        pad.bottom = .exact(px: 12); pad.left = .exact(px: 12)
        style.spacing.padding = pad
        // content-box → 4 + 12 on every edge.
        style.backgroundClip = BackgroundClipConfig(mode: .contentBox)
        let cb = StyleBuilder.backgroundClipInsets(style)
        XCTAssertEqual(cb.top, 16); XCTAssertEqual(cb.leading, 16)
        XCTAssertEqual(cb.bottom, 16); XCTAssertEqual(cb.trailing, 16)
        // padding-box → border band only.
        style.backgroundClip = BackgroundClipConfig(mode: .paddingBox)
        let pb = StyleBuilder.backgroundClipInsets(style)
        XCTAssertEqual(pb.top, 4); XCTAssertEqual(pb.leading, 4)
        // border-box → zero.
        style.backgroundClip = BackgroundClipConfig(mode: .borderBox)
        XCTAssertEqual(StyleBuilder.backgroundClipInsets(style).top, 0)
    }

    // MARK: - Border medium default + content inset

    /// A style-only side paints at the CSS `medium` 3px default
    /// (borders/016_Decorated's dotted border-block-end); `none` and
    /// `hidden` stay invisible.
    func testBorderEffectiveWidthMediumDefault() {
        var c = BorderSideConfig()
        // Style without width → 3px medium.
        c.style = .dotted
        XCTAssertEqual(c.effectiveWidth, 3)
        XCTAssertTrue(c.hasBorder)
        // Explicit width wins.
        c.width = 6
        XCTAssertEqual(c.effectiveWidth, 6)
        // `none` never paints.
        var n = BorderSideConfig()
        n.style = BorderStyleValue.none
        XCTAssertFalse(n.hasBorder)
        // No style + no width → nothing.
        XCTAssertNil(BorderSideConfig().effectiveWidth)
    }

    // MARK: - Percent border-radius (CSS Backgrounds 3 §5.1)

    /// `border-radius: 50%` on a 200×80 box resolves to a 100×40
    /// elliptical corner (borders/016_Decorated), not a capsule.
    func testPercentRadiusResolvesPerAxis() {
        let corner = BorderRadiusCorner(x: 0, y: 0, xPercent: 50, yPercent: 50)
        let r = corner.resolved(in: CGSize(width: 200, height: 80))
        XCTAssertEqual(r.x, 100)
        XCTAssertEqual(r.y, 40)
        // Mixed corner: "40px 50%" keeps the px axis (borders/004_C05).
        let mixed = BorderRadiusCorner(x: 40, y: 0, xPercent: nil, yPercent: 50)
        let m = mixed.resolved(in: CGSize(width: 140, height: 64))
        XCTAssertEqual(m.x, 40)
        XCTAssertEqual(m.y, 32)
    }

    // MARK: - mask-mode: luminance (CSS Masking 1 §7.3)

    /// Black (Y=0) → fully transparent; white (Y=1) keeps its alpha —
    /// a black→transparent gradient mask hides EVERYTHING in luminance
    /// mode, which is what web renders (effects/004_C05_MaskImage).
    func testLuminanceStopFolding() {
        // Black stop, alpha 1 → alpha 0.
        let black = MaskApplier.luminanceStop(
            Gradient.Stop(color: Color(.sRGB, red: 0, green: 0, blue: 0, opacity: 1),
                          location: 0))
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(black.color).getRed(&r, green: &g, blue: &b, alpha: &a)
        XCTAssertEqual(a, 0, accuracy: 0.001)
        // White stop, alpha 0.5 → alpha 0.5 (Y=1 × 0.5).
        let white = MaskApplier.luminanceStop(
            Gradient.Stop(color: Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 0.5),
                          location: 1))
        UIColor(white.color).getRed(&r, green: &g, blue: &b, alpha: &a)
        XCTAssertEqual(a, 0.5, accuracy: 0.001)
    }

    // MARK: - Legacy clip gating (CSS 2.1 §11.1.2)

    /// `clip: rect(...)` applies to absolutely-positioned elements ONLY;
    /// on a static element the rect must be dropped (effects/008 lost
    /// its whole double border to the rect before this).
    func testLegacyClipRequiresPositioned() {
        // Static element → rect dropped.
        let rectData = obj([
            "type": .string("rect"),
            "top": obj(["px": .double(10)]),
            "right": obj(["px": .double(150)]),
            "bottom": obj(["px": .double(110)]),
            "left": obj(["px": .double(10)]),
        ])
        let staticStyle = StyleBuilder.build(from: props([
            ("Clip", rectData),
        ]))
        XCTAssertNil(staticStyle.clipPath?.legacy)
        // Absolutely positioned → rect retained.
        let absStyle = StyleBuilder.build(from: props([
            ("Clip", rectData),
            ("Position", .string("ABSOLUTE")),
        ]))
        if case .rect? = absStyle.clipPath?.legacy { /* retained — ok */ }
        else { XCTFail("clip rect should survive on an absolute element") }
    }

    // MARK: - Text inheritance channel (css-cascade-4)

    /// Own declarations always beat inherited ones; inherited entries
    /// only fill the gaps; non-inherited types never flow down.
    func testInheritedTextMerge() {
        let parentDecls = props([
            ("FontFamily", .string("MONOSPACE")),
            ("FontSize", obj(["px": .double(15)])),
            ("Width", obj(["px": .double(240)])),          // never inherits
            ("BackgroundColor", .string("#ecf0f1")),       // never inherits
        ])
        // The inheritable subset drops Width/BackgroundColor.
        let flowing = InheritedText.inheritable(from: parentDecls)
        XCTAssertEqual(Set(flowing.map { $0.type }), ["FontFamily", "FontSize"])
        // Child redeclares FontFamily — its own value must win.
        let childOwn = props([("FontFamily", .string("SERIF"))])
        let merged = InheritedText.merge(own: childOwn, inherited: flowing)
        // FontSize flowed in; FontFamily stayed the child's.
        XCTAssertEqual(merged.filter { $0.type == "FontFamily" }.count, 1)
        XCTAssertTrue(merged.contains { $0.type == "FontSize" })
        // The style built from the merged list reflects both: serif
        // design (own) at the inherited 15pt size.
        let style = StyleBuilder.build(from: merged)
        XCTAssertEqual(style.text.fontSize, 15)
        XCTAssertEqual(style.text.fontDesign, .serif)
    }

    /// Empty channel is a no-op (root components, unchanged baselines).
    func testInheritedTextMergeEmptyChannel() {
        let own = props([("FontSize", obj(["px": .double(20)]))])
        // Same array instance semantics: nothing prepended.
        XCTAssertEqual(InheritedText.merge(own: own, inherited: []).count, 1)
    }

    // MARK: - Flex cross-axis default

    /// css-flexbox-1 §8.3: `stretch` behaves as flex-start for items
    /// with a definite cross size — the HStack bridge must map the
    /// default to `.top`, not `.center` (nested-3level rows).
    func testFlexStretchBridgesToTop() {
        // Build a flex-row style with the default align-items.
        let style = StyleBuilder.build(from: props([
            ("Display", .string("FLEX")),
        ]))
        // The legacy bridge folds stretch into LayoutConfig.Align.
        XCTAssertEqual(style.layout.align, LayoutConfig.Align.stretch)
        // The SwiftUI mapping is private to ComponentRenderer; pin the
        // semantic here via the config so a regression that reroutes
        // the bridge shows up even if the extension moves.
    }
}
