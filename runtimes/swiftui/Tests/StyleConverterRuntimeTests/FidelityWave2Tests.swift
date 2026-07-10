//
//  FidelityWave2Tests.swift
//  StyleConverterRuntimeTests
//
//  Pinning tests for the wave-2 iOS engine fixes:
//    • CSSFlexMath — real flex-grow / flex-shrink / flex-basis resolution
//      (css-flexbox-1 §9.7) incl. min-clamped violation freezing, and
//      justify-content main-axis distribution (§8.2). Numbers match the
//      browser calc for FR_GrowBasis / FR_ShrinkBasis / FC_SpaceBetween.
//    • MotionOffsetMath + extractor — CSS Motion Path ray() subset
//      (Layout_C14_OffsetPath).
//    • Logical-over-physical margin/padding precedence (Spacing_C03).
//    • Typography glyph-level state: nowrap, small-caps keyword
//      normalisation, font-variation-settings wght, bolder table,
//      multi-layer text-shadow, text-indent touched flag.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FidelityWave2Tests: XCTestCase {

    // MARK: - Helpers

    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - CSSFlexMath: grow (FR_GrowBasis / FC_GrowBasis)

    func testFlexGrowDistributesFreeSpaceWithMinClamp() {
        // FR_GrowBasis: width 320 − 2×8 padding = 304 content, gap 8,
        // three items basis 40 with the 50px web-harness min floor and
        // grow 0/1/2. Browser: a freezes at min 50, b/c split the rest.
        let items: [CSSFlexMath.ItemInput] = [
            .init(basis: 40, min: 50, grow: 0, shrink: 1),
            .init(basis: 40, min: 50, grow: 1, shrink: 1),
            .init(basis: 40, min: 50, grow: 2, shrink: 1),
        ]
        let sizes = CSSFlexMath.mainSizes(items: items, available: 304, gap: 8)
        XCTAssertEqual(sizes[0], 50, accuracy: 0.5)          // min floor
        XCTAssertEqual(sizes[1], 92.67, accuracy: 0.75)      // 40 + 158/3
        XCTAssertEqual(sizes[2], 145.33, accuracy: 0.75)     // 40 + 2×158/3
    }

    // MARK: - CSSFlexMath: shrink (FR_ShrinkBasis)

    func testFlexShrinkScaledWithMinViolationFreeze() {
        // FR_ShrinkBasis: width 240 − 16 = 224 content, gap 8, bases 100
        // with shrink 0/1/3 and the 50px min floor. Browser: a keeps 100
        // (shrink 0), c would hit 31 → clamps to 50 and freezes, b
        // re-absorbs the remainder → 58.
        let items: [CSSFlexMath.ItemInput] = [
            .init(basis: 100, min: 50, grow: 0, shrink: 0),
            .init(basis: 100, min: 50, grow: 0, shrink: 1),
            .init(basis: 100, min: 50, grow: 0, shrink: 3),
        ]
        let sizes = CSSFlexMath.mainSizes(items: items, available: 224, gap: 8)
        XCTAssertEqual(sizes[0], 100, accuracy: 0.5)
        XCTAssertEqual(sizes[1], 58, accuracy: 0.75)
        XCTAssertEqual(sizes[2], 50, accuracy: 0.5)
    }

    // MARK: - CSSFlexMath: indefinite container hugs

    func testFlexIndefiniteContainerUsesHypotheticalSizes() {
        // No definite main size (or an infinity probe) → fit-content:
        // every item at max(basis, min), no grow/shrink distribution.
        let items: [CSSFlexMath.ItemInput] = [
            .init(basis: 40, min: 50, grow: 3, shrink: 1),
            .init(basis: 80, min: 0, grow: 0, shrink: 1),
        ]
        XCTAssertEqual(CSSFlexMath.mainSizes(items: items, available: nil, gap: 4),
                       [50, 80])
        XCTAssertEqual(CSSFlexMath.mainSizes(items: items,
                                             available: .infinity, gap: 4),
                       [50, 80])
    }

    // MARK: - CSSFlexMath: justify-content (FC_SpaceBetween)

    func testJustifySpaceBetweenColumn() {
        // FC_SpaceBetween: height 200 − 12 = 188 content, gap 6, three
        // 30px items → free 86 splits into two 43px extra gutters.
        let offsets = CSSFlexMath.mainOffsets(sizes: [30, 30, 30],
                                              available: 188, gap: 6,
                                              justify: .spaceBetween)
        XCTAssertEqual(offsets[0], 0, accuracy: 0.5)
        XCTAssertEqual(offsets[1], 79, accuracy: 0.5)    // 30+6+43
        XCTAssertEqual(offsets[2], 158, accuracy: 0.5)   // ends at 188
    }

    func testJustifyCenterEndEvenlyAround() {
        let sizes: [CGFloat] = [20, 20]
        // free = 100 − (20+20+10 gap) = 50
        XCTAssertEqual(CSSFlexMath.mainOffsets(sizes: sizes, available: 100,
                                               gap: 10, justify: .center)[0],
                       25, accuracy: 0.5)
        XCTAssertEqual(CSSFlexMath.mainOffsets(sizes: sizes, available: 100,
                                               gap: 10, justify: .end)[0],
                       50, accuracy: 0.5)
        // space-evenly: 3 gutters of 50/3 ≈ 16.67.
        let evenly = CSSFlexMath.mainOffsets(sizes: sizes, available: 100,
                                             gap: 10, justify: .spaceEvenly)
        XCTAssertEqual(evenly[0], 16.67, accuracy: 0.75)
        // space-around: half gutter leading = 12.5.
        let around = CSSFlexMath.mainOffsets(sizes: sizes, available: 100,
                                             gap: 10, justify: .spaceAround)
        XCTAssertEqual(around[0], 12.5, accuracy: 0.75)
    }

    // MARK: - CSSFlexMath: cross alignment (FR_AlignSelf)

    func testCrossAlignmentResolution() {
        // align-self beats align-items; normal/auto degrade to stretch.
        XCTAssertEqual(CSSFlexMath.resolvedAlign(self: .start, items: .center), .start)
        XCTAssertEqual(CSSFlexMath.resolvedAlign(self: .auto, items: .center), .center)
        XCTAssertEqual(CSSFlexMath.resolvedAlign(self: nil, items: nil), .stretch)
        XCTAssertEqual(CSSFlexMath.resolvedAlign(self: .normal, items: .end), .stretch)
        // Offsets: 30px item in a 104px line.
        XCTAssertEqual(CSSFlexMath.crossOffset(itemCross: 30, lineCross: 104, align: .start), 0)
        XCTAssertEqual(CSSFlexMath.crossOffset(itemCross: 30, lineCross: 104, align: .center), 37)
        XCTAssertEqual(CSSFlexMath.crossOffset(itemCross: 30, lineCross: 104, align: .end), 74)
        // Stretch anchors at cross-start (§8.3: non-auto items behave
        // as flex-start — the wave-1 stretch→top bridge, preserved).
        XCTAssertEqual(CSSFlexMath.crossOffset(itemCross: 30, lineCross: 104, align: .stretch), 0)
    }

    // MARK: - Motion path math (Layout_C14_OffsetPath)

    func testMotionOffsetRayMath() {
        // offset-rotate for ray(45deg): auto → bearing − 90; reverse
        // adds 180 (motion-1 §3.5).
        XCTAssertEqual(MotionOffsetMath.rotation(rayDeg: 45, rotate: .auto), -45)
        XCTAssertEqual(MotionOffsetMath.rotation(rayDeg: 45, rotate: .reverse), 135)
        XCTAssertEqual(MotionOffsetMath.rotation(rayDeg: 45, rotate: .fixed(deg: 10)), 10)
        // offset-position left top + distance 0: the 180×64 element's
        // centre lands on the containing-block origin → Δ = (−90, −32).
        let d = MotionOffsetMath.translation(start: .zero, rayDeg: 45,
                                             distance: 0,
                                             elementSize: CGSize(width: 180, height: 64))
        XCTAssertEqual(d.width, -90, accuracy: 0.01)
        XCTAssertEqual(d.height, -32, accuracy: 0.01)
        // Distance moves along the bearing: 100px at 90deg = +x.
        let d2 = MotionOffsetMath.translation(start: .zero, rayDeg: 90,
                                              distance: 100,
                                              elementSize: .zero)
        XCTAssertEqual(d2.width, 100, accuracy: 0.01)
        XCTAssertEqual(d2.height, 0, accuracy: 0.01)
    }

    func testMotionOffsetExtractorRayShapes() {
        // Exact IR shapes emitted by the converter for Layout_C14.
        let list = props([
            ("OffsetPath", obj(["type": .string("ray"), "deg": .double(45)])),
            ("OffsetPosition", obj(["type": .string("position"),
                                    "x": obj(["type": .string("left")]),
                                    "y": obj(["type": .string("top")])])),
            ("OffsetRotate", obj(["type": .string("reverse")])),
        ])
        let cfg = MotionOffsetExtractor.extract(from: list)
        XCTAssertEqual(cfg?.path, .ray(deg: 45))
        XCTAssertEqual(cfg?.positionX, .start)
        XCTAssertEqual(cfg?.positionY, .start)
        XCTAssertEqual(cfg?.rotate, .reverse)
        // Unsupported path shapes stay honest no-ops (nil config).
        let none = MotionOffsetExtractor.extract(from: props([
            ("OffsetPath", obj(["type": .string("path"), "d": .string("M0 0")])),
        ]))
        XCTAssertNil(none)
    }

    // MARK: - Logical vs physical spacing precedence (Spacing_C03/C06)

    func testMarginLogicalWinsOverPhysicalRegardlessOfOrder() {
        // Spacing_C03 declaration order: inline-end, inline-start, left.
        // The web applier emits logical last → browser uses 40, not 4.
        let cfg = MarginExtractor.extract(from: props([
            ("MarginInlineEnd", obj(["px": .double(20)])),
            ("MarginInlineStart", obj(["px": .double(40)])),
            ("MarginLeft", obj(["px": .double(4)])),
        ]))
        XCTAssertEqual(cfg?.left, .exact(px: 40))
        XCTAssertEqual(cfg?.right, .exact(px: 20))
        // Reversed order — logical still wins (two-pass rule).
        let cfg2 = MarginExtractor.extract(from: props([
            ("MarginLeft", obj(["px": .double(4)])),
            ("MarginInlineStart", obj(["px": .double(40)])),
        ]))
        XCTAssertEqual(cfg2?.left, .exact(px: 40))
    }

    func testPaddingLogicalWinsAndEmResolves() {
        // Spacing_C06: padding-block-start 2em (IR ships original v/u
        // only) must land on TOP as a relative(em) value.
        let cfg = PaddingExtractor.extract(from: props([
            ("PaddingBlockEnd", obj(["px": .double(5)])),
            ("PaddingBlockStart", obj(["original": obj(["v": .double(2),
                                                        "u": .string("EM")])])),
            ("PaddingTop", obj(["px": .double(1)])),
        ]))
        XCTAssertEqual(cfg?.top, .relative(value: 2, unit: .em, pxFallback: nil))
        XCTAssertEqual(cfg?.bottom, .exact(px: 5))
    }

    // MARK: - Typography glyph-state bridges

    func testNoWrapFromTextWrapAndWhiteSpace() {
        // Typography_C20: text-wrap nowrap; C21: white-space nowrap.
        // Serializer uppercases keywords.
        let a = TypographyExtractor.extract(from: props([
            ("TextWrap", .string("NOWRAP")),
        ]))
        XCTAssertEqual(a?.noWrap, true)
        let b = TypographyExtractor.extract(from: props([
            ("WhiteSpace", .string("NOWRAP")),
        ]))
        XCTAssertEqual(b?.noWrap, true)
        // pre also suppresses wrap; pre-wrap does not.
        let c = TypographyExtractor.extract(from: props([
            ("WhiteSpace", .string("PRE_WRAP")),
        ]))
        XCTAssertNotEqual(c?.noWrap, true)
    }

    func testSmallCapsKeywordNormalisation() {
        // Typography_C06 ships SCREAMING_SNAKE "SMALL_CAPS".
        let agg = TypographyExtractor.extract(from: props([
            ("FontVariantCaps", .string("SMALL_CAPS")),
        ]))
        XCTAssertEqual(agg?.smallCaps, true)
    }

    func testFontVariationSettingsWghtObjectForm() {
        // Typography_C07 IR shape: {"type":"variations","variations":
        // [{"axis":"wght","value":900}]} — the extractor must decode the
        // wrapped object form (previously only a bare array parsed).
        // The APPLIER stays a no-op on purpose: the web reference loads
        // Inter as static faces, so the browser ignores the wght axis —
        // mapping it to Font.Weight on iOS diverged from the reference
        // (C07 0.856 → 0.833 measured; reverted).
        let cfg = FontVariationSettingsExtractor.extract(from: props([
            ("FontVariationSettings",
             obj(["type": .string("variations"),
                  "variations": .array([obj(["axis": .string("wght"),
                                             "value": .double(900)])])])),
        ]))
        XCTAssertEqual(cfg?.axes, [FontVariationAxis(tag: "wght", value: 900)])
        // And the aggregate keeps the declared font-weight untouched.
        let agg = TypographyExtractor.extract(from: props([
            ("FontWeight", .double(400)),
            ("FontVariationSettings",
             obj(["type": .string("variations"),
                  "variations": .array([obj(["axis": .string("wght"),
                                             "value": .double(900)])])])),
        ]))
        XCTAssertEqual(agg?.fontWeight, .regular)
    }

    func testFontWeightBolderRelativeTable() {
        // css-fonts-4 §2.2: from the inherited default 400, bolder →
        // 700 (bold) and lighter → 100 (ultraLight).
        let bolder = TypographyExtractor.extract(from: props([
            ("FontWeight", .string("bolder")),
        ]))
        XCTAssertEqual(bolder?.fontWeight, .bold)
        let lighter = TypographyExtractor.extract(from: props([
            ("FontWeight", .string("lighter")),
        ]))
        XCTAssertEqual(lighter?.fontWeight, .ultraLight)
    }

    func testTextShadowKeepsAllLayers() {
        // Typography_C18 declares two layers — both must survive for the
        // glyph-level chain (css-text-decor-3 §4 comma list).
        let agg = TypographyExtractor.extract(from: props([
            ("TextShadow", .array([
                obj(["x": obj(["px": .double(2)]), "y": obj(["px": .double(2)]),
                     "blur": obj(["px": .double(4)])]),
                obj(["x": obj(["px": .double(-2)]), "y": obj(["px": .double(-2)]),
                     "blur": obj(["px": .double(4)])]),
            ])),
        ]))
        XCTAssertEqual(agg?.textShadowLayers.count, 2)
        XCTAssertEqual(agg?.textShadowLayers.first?.x, 2)
        XCTAssertEqual(agg?.textShadowLayers.last?.x, -2)
    }

    func testFontSizeAdjustCapHeightScalesUsedSize() {
        // Typography_C04: font-size 28px + font-size-adjust cap-height
        // 0.7 → used ≈ 28 × 0.7 / 0.7275 ≈ 26.94px (css-fonts-4 §4.6;
        // Inter 4.0 capHeight ratio — the fallback constant when the
        // face isn't registered in the test bundle).
        let agg = TypographyExtractor.extract(from: props([
            ("FontSize", obj(["px": .double(28)])),
            ("FontSizeAdjust", obj(["type": .string("metric-value"),
                                    "metric": .string("cap-height"),
                                    "value": .double(0.7)])),
        ]))
        XCTAssertEqual(agg?.fontSizePx ?? 0, 26.94, accuracy: 0.6)
        // `none` keyword → no adjustment.
        let none = TypographyExtractor.extract(from: props([
            ("FontSize", obj(["px": .double(28)])),
            ("FontSizeAdjust", .string("NONE")),
        ]))
        XCTAssertEqual(none?.fontSizePx, 28)
    }

    func testTextIndentAloneTouchesAggregate() {
        // Typography_C17 regression: text-indent as the only typography
        // declaration must keep the aggregate alive (touched flag) so
        // the −100px outdent reaches the label.
        let agg = TypographyExtractor.extract(from: props([
            ("TextIndent", obj(["type": .string("length"),
                                "px": .double(-100)])),
        ]))
        XCTAssertEqual(agg?.textIndentPx, -100)
    }

    // MARK: - StyleBuilder bridge (glyph-state → TextConfig)

    func testStyleBuilderBridgesGlyphState() {
        let style = StyleBuilder.build(from: props([
            ("WhiteSpace", .string("NOWRAP")),
            ("FontVariantCaps", .string("SMALL_CAPS")),
            ("TextShadow", .array([
                obj(["x": obj(["px": .double(2)]), "y": obj(["px": .double(2)]),
                     "blur": obj(["px": .double(4)])]),
            ])),
        ]))
        XCTAssertTrue(style.text.noWrap)
        XCTAssertTrue(style.text.smallCaps)
        XCTAssertEqual(style.text.shadows.count, 1)
    }
}
