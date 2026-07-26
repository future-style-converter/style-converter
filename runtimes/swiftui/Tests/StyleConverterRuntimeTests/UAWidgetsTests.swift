//
//  UAWidgetsTests.swift
//  StyleConverterRuntimeTests — lane W2 (wave 20).
//
//  The iOS half of the CROSS-PLATFORM PIN TABLE: per-widget paint-plan
//  pins (geometry + palette as language-neutral op strings). The Kotlin
//  twin (UAWidgetsGeometryTest.kt / UAWidgetsResolveTest.kt) asserts
//  the IDENTICAL expected strings — a change moving one side but not
//  the other fails one of the two suites (the drift defense). Plus:
//  meta.attrs wire decode, widget identity, accent resolution, and a
//  raster pin per painted-op family through the real UAWidgetView.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class UAWidgetsTests: XCTestCase {

    /// Deterministic width probe: 7px per char — matches the Kotlin twin
    /// so label-dependent plans pin the same strings on both natives.
    private let measure: (String) -> CGFloat = { CGFloat($0.count) * 7 }

    /// Pin helper: plan → op pin strings.
    private func pins(_ spec: UAWidgetsGeometry.Spec) -> [String] {
        UAWidgetsGeometry.plan(spec: spec, measure: measure).map(\.pin)
    }

    // MARK: - Pin table (byte-identical to the Kotlin twin's asserts)

    func testUncheckedCheckboxPins() {
        // 13x13 white rrect + half-pixel-inset #767676 border.
        XCTAssertEqual(pins(.init(kind: .checkbox)), [
            "fillRRect 0 0 13 13 2 #FFFFFFFF",
            "strokeRRect 0.5 0.5 12 12 2 #FF767676",
        ])
    }

    func testCheckedCheckboxPins() {
        // The accent-color-visited target: accent box + white check.
        XCTAssertEqual(pins(.init(kind: .checkbox, checked: true)), [
            "fillRRect 0 0 13 13 2 #FF0075FF",
            "line 3.25 6.75 5.25 8.75 1.6 #FFFFFFFF",
            "line 5.25 8.75 9.75 4.25 1.6 #FFFFFFFF",
        ])
        // accent-color: red — the fill takes the resolved accent.
        XCTAssertEqual(pins(.init(kind: .checkbox, checked: true, accent: 0xFFFF0000)).first,
                       "fillRRect 0 0 13 13 2 #FFFF0000")
    }

    func testRadioPins() {
        // Unchecked ring, checked donut (three concentric fills).
        XCTAssertEqual(pins(.init(kind: .radio)), [
            "fillCircle 6.5 6.5 6.5 #FFFFFFFF",
            "strokeCircle 6.5 6.5 6 #FF767676",
        ])
        XCTAssertEqual(pins(.init(kind: .radio, checked: true)), [
            "fillCircle 6.5 6.5 6.5 #FF0075FF",
            "fillCircle 6.5 6.5 4.75 #FFFFFFFF",
            "fillCircle 6.5 6.5 3 #FF0075FF",
        ])
    }

    func testButtonTextfieldTextareaPins() {
        // Button hugs its label: "button" (6×7) + 14 pad = 56 wide.
        XCTAssertEqual(pins(.init(kind: .button, label: "button")), [
            "fillRRect 0 0 56 21 2 #FFEFEFEF",
            "strokeRRect 0.5 0.5 55 20 2 #FF767676",
            "label 'button' 7 3.5 13.33 #FF000000",
        ])
        // 153x21 field with left-aligned value.
        XCTAssertEqual(pins(.init(kind: .textfield, label: "input-text")), [
            "fillRect 0 0 153 21 #FFFFFFFF",
            "strokeRect 0.5 0.5 152 20 #FF767676",
            "label 'input-text' 4 3.5 13.33 #FF000000",
        ])
        // 184x36 (the W3 atom box) area + the two resize-grip diagonals.
        XCTAssertEqual(pins(.init(kind: .textarea, label: "textarea")), [
            "fillRect 0 0 184 36 #FFFFFFFF",
            "strokeRect 0.5 0.5 183 35 #FF767676",
            "label 'textarea' 3 2 13.33 #FF000000",
            "line 177 34 182 29 1 #FF767676",
            "line 180 34 182 32 1 #FF767676",
        ])
    }

    func testRangeProgressMeterPins() {
        // fix 4 — 129x21 atom box; track ink 127x8 at (x3,y6) with the
        // refresh's #B2B2B2 hairline (ref bands x19.5..145.5 × y134..141
        // against the box at x16.5/y127.5); f=0.5 → thumb cx = 3 + 8 +
        // 0.5×111 = 66.5 (ref thumb right edge x90.5 → center 82.5 =
        // box 16 + 66.5); accent fill painted OVER the stroke.
        XCTAssertEqual(pins(.init(kind: .range, fraction: 0.5)), [
            "fillRRect 3 6 127 8 4 #FFEFEFEF",
            "strokeRRect 3.5 6.5 126 7 4 #FFB2B2B2",
            "fillRRect 3 6 63.5 8 4 #FF0075FF",
            "fillCircle 66.5 10 8 #FF0075FF",
        ])
        // Progress (160x16 gauge box, ink centered y4) + the fix-4
        // #B2B2B2 hairline (ref edge rows y159/y166): value=0.5 → 80px
        // accent chunk covering the stroke; nil → bare stroked track.
        XCTAssertEqual(pins(.init(kind: .progress, fraction: 0.5)), [
            "fillRRect 0 4 160 8 4 #FFEFEFEF",
            "strokeRRect 0.5 4.5 159 7 4 #FFB2B2B2",
            "fillRRect 0 4 80 8 4 #FF0075FF",
        ])
        XCTAssertEqual(pins(.init(kind: .progress)), [
            "fillRRect 0 4 160 8 4 #FFEFEFEF",
            "strokeRRect 0.5 4.5 159 7 4 #FFB2B2B2",
        ])
        // Meter is not accent-driven — optimum green in the 80x16 box +
        // the same hairline as the progress twin (its ref probe).
        XCTAssertEqual(pins(.init(kind: .meter, fraction: 0.5, accent: 0xFFFF0000)), [
            "fillRRect 0 4 80 8 4 #FFEFEFEF",
            "strokeRRect 0.5 4.5 79 7 4 #FFB2B2B2",
            "fillRRect 0 4 40 8 4 #FF00AA00",
        ])
    }

    func testColorMenulistListboxPins() {
        // 50x27 (the W3 atom box) chrome frame + black swatch. fix 4:
        // per-axis insets 5.5/6.5 → the ref's 39x14 swatch band
        // (x209.5..248.5 × y131.5..145.5), not the old 4px/42x19.
        XCTAssertEqual(pins(.init(kind: .color)), [
            "fillRect 0 0 50 27 #FFEFEFEF",
            "strokeRect 0.5 0.5 49 26 #FF767676",
            "fillRect 5.5 6.5 39 14 #FF000000",
        ])
        // Menulist: "select" (42) + 4 + 16 = 62 wide. fix 4: the slab IS
        // the 19px box (ref borders y132..150) and the arrow is a
        // STROKED CHEVRON — two 1.8px arms from (w−13.5, 6)/(w−5.5, 6)
        // to the apex (w−9.5, 10.5); the ref shows white BETWEEN the
        // arms (a filled triangle would be solid across).
        XCTAssertEqual(pins(.init(kind: .menulist, label: "select")), [
            "fillRRect 0 0 62 19 2 #FFFFFFFF",
            "strokeRRect 0.5 0.5 61 18 2 #FF767676",
            "label 'select' 4 2.5 13.33 #FF000000",
            "line 48.5 6 52.5 10.5 1.8 #FF000000",
            "line 52.5 10.5 56.5 6 1.8 #FF000000",
        ])
        // Listbox: widest option + 6; height 70 (W3 box); rows advance 17.
        XCTAssertEqual(pins(.init(kind: .listbox, options: ["aa", "bbbb", "c", "dd", "eee"])), [
            "fillRect 0 0 34 70 #FFFFFFFF",
            "strokeRect 0.5 0.5 33 69 #FF767676",
            "label 'aa' 3 1 13.33 #FF000000",
            "label 'bbbb' 3 18 13.33 #FF000000",
            "label 'c' 3 35 13.33 #FF000000",
            "label 'dd' 3 52 13.33 #FF000000",
        ])
    }

    func testFileImageHiddenPins() {
        // File: "Choose File" (11×7=77) + 14 = 91 button; status at 97.
        XCTAssertEqual(pins(.init(kind: .file)), [
            "fillRRect 0 0 91 21 2 #FFEFEFEF",
            "strokeRRect 0.5 0.5 90 20 2 #FF767676",
            "label 'Choose File' 7 3.5 13.33 #FF000000",
            "label 'No file chosen' 97 3.5 13.33 #FF000000",
        ])
        // Image: alt/value text only; hidden: nothing at all.
        XCTAssertEqual(pins(.init(kind: .image, label: "def")), ["label 'def' 0 1 13.33 #FF000000"])
        XCTAssertEqual(pins(.init(kind: .hidden)), [])
        let hidden = UAWidgetsGeometry.intrinsicSize(spec: .init(kind: .hidden), measure: measure)
        XCTAssertEqual(hidden.w, 0); XCTAssertEqual(hidden.h, 0)
    }

    // MARK: - Widget identity (resolve twin table)

    /// Standalone-component decode helper (the lenient path tests use
    /// wire-shaped JSON so identity pins run against real decode output).
    private func comp(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    func testKindTableFromWireShapes() throws {
        // <a> is no widget — normal text path (appearance-checkbox-001's
        // first child renders plain "a" even with appearance: checkbox).
        let a = try comp(#"{"id":"a","name":"a","text":"a","meta":{"sourceTag":"a"}}"#)
        XCTAssertNil(UAWidgetsResolve.resolve(component: a, properties: []))
        // input type=checkbox checked (accent-color-visited's only ink).
        let cb = try comp(#"{"id":"c","name":"c","meta":{"sourceTag":"input","attrs":{"type":"checkbox","checked":true}}}"#)
        let cbSpec = try XCTUnwrap(UAWidgetsResolve.resolve(component: cb, properties: []))
        XCTAssertEqual(cbSpec.kind, .checkbox)
        XCTAssertTrue(cbSpec.checked)
        XCTAssertEqual(cbSpec.accent, 0xFF0075FF) // default accent
        // select multiple → listbox; plain select → menulist labeled by
        // its first option child.
        let sel = try comp(#"{"id":"s","name":"s","meta":{"sourceTag":"select"},"children":[{"id":"o","name":"o","text":"select","meta":{"sourceTag":"option"}}]}"#)
        let selSpec = try XCTUnwrap(UAWidgetsResolve.resolve(component: sel, properties: []))
        XCTAssertEqual(selSpec.kind, .menulist)
        XCTAssertEqual(selSpec.label, "select")
        let multi = try comp(#"{"id":"m","name":"m","meta":{"sourceTag":"select","attrs":{"multiple":true}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: multi, properties: [])?.kind, .listbox)
        // meter value=0.5 rides the numeric channel.
        let meter = try comp(#"{"id":"mt","name":"mt","meta":{"sourceTag":"meter","attrs":{"value":0.5}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: meter, properties: [])?.fraction, 0.5)
    }

    /// Property helper: type + wire-shaped data JSON.
    private func prop(_ type: String, _ dataJson: String) throws -> IRProperty {
        IRProperty(type: type, data: try JSONDecoder().decode(IRValue.self, from: Data(dataJson.utf8)))
    }

    func testAppearanceGate() throws {
        let cb = try comp(#"{"id":"c","name":"c","meta":{"sourceTag":"input","attrs":{"type":"checkbox"}}}"#)
        // appearance: none → devolved (no widget).
        XCTAssertNil(UAWidgetsResolve.resolve(component: cb,
            properties: [try prop("Appearance", #"{"type":"none"}"#)]))
        // auto + every compat alias → native (alias-to-auto tests).
        for v in ["auto", "button", "checkbox", "listbox", "menulist", "square-button", "textfield"] {
            XCTAssertEqual(UAWidgetsResolve.resolve(component: cb,
                properties: [try prop("Appearance", #"{"type":"\#(v)"}"#)])?.kind,
                .checkbox, "appearance:\(v) must stay native")
        }
        // `initial` computes to the initial value `none` → devolved.
        XCTAssertNil(UAWidgetsResolve.resolve(component: cb,
            properties: [try prop("Appearance", #"{"type":"keyword","keyword":"initial"}"#)]))
        // Non-widget input types ignore appearance entirely.
        let img = try comp(#"{"id":"i","name":"i","meta":{"sourceTag":"input","attrs":{"type":"image","value":"def"}}}"#)
        let imgSpec = try XCTUnwrap(UAWidgetsResolve.resolve(component: img,
            properties: [try prop("Appearance", #"{"type":"auto"}"#)]))
        XCTAssertEqual(imgSpec.kind, .image)
        XCTAssertEqual(imgSpec.label, "def") // alt absent → value fallback
    }

    func testAccentResolution() throws {
        let cb = try comp(#"{"id":"c","name":"c","meta":{"sourceTag":"input","attrs":{"type":"checkbox","checked":true}}}"#)
        // Explicit srgb red.
        XCTAssertEqual(UAWidgetsResolve.resolve(component: cb, properties: [
            try prop("AccentColor", #"{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"#),
        ])?.accent, 0xFFFF0000)
        // currentColor resolves against the element's used color (the
        // merged list — accent-color-parent-currentcolor's red chain).
        XCTAssertEqual(UAWidgetsResolve.resolve(component: cb, properties: [
            try prop("AccentColor", #"{"type":"color","original":"currentColor"}"#),
            try prop("Color", #"{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"#),
        ])?.accent, 0xFFFF0000)
    }

    func testFractionEdgeCasesStayFiniteAndTwinAligned() throws {
        // Wave-20 skeptic pins (Kotlin twin: fractionEdgeCases_...).
        // "NaN" parses via Double.init but is NOT a valid HTML float
        // (§2.3.5.1) — must fall back to the midpoint, never NaN.
        let nan = try comp(#"{"id":"r","name":"r","meta":{"sourceTag":"input","attrs":{"type":"range","value":"NaN"}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: nan, properties: [])?.fraction, 0.5)
        // Degenerate travel (min == max) → midpoint, not 0/0 NaN.
        let deg = try comp(#"{"id":"d","name":"d","meta":{"sourceTag":"input","attrs":{"type":"range","value":"5","min":5,"max":5}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: deg, properties: [])?.fraction, 0.5)
        // progress max=0 — HTML §4.10.13 pins max > 0, so a zero wire
        // max falls back to 1.0 (0.5/0.0 was NaN plan geometry before).
        let p0 = try comp(#"{"id":"p","name":"p","meta":{"sourceTag":"progress","attrs":{"value":0.5,"max":0}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: p0, properties: [])?.fraction, 0.5)
        // meter max <= min stays guarded at 0.
        let mg = try comp(#"{"id":"m","name":"m","meta":{"sourceTag":"meter","attrs":{"value":1,"min":2,"max":2}}}"#)
        XCTAssertEqual(UAWidgetsResolve.resolve(component: mg, properties: [])?.fraction, 0)
    }

    func testMetaAttrsTypedChannelsNeverCoerceStrings() throws {
        // Twin-alignment pin (wave-20 skeptic): quoted strings must stay
        // on the string channel only — matching the Compose decoder's
        // non-string gate — so an off-contract writer decodes the same
        // on both natives.
        let c = try comp(#"{"id":"a","name":"a","meta":{"sourceTag":"input","attrs":{"type":"range","value":"50","min":"5","max":"10","checked":"true"}}}"#)
        XCTAssertEqual(c.meta?.attrs?.value, "50") // verbatim string
        XCTAssertNil(c.meta?.attrs?.valueNumber)   // numbers-only channel
        XCTAssertNil(c.meta?.attrs?.min)
        XCTAssertNil(c.meta?.attrs?.max)
        XCTAssertNil(c.meta?.attrs?.checked)       // booleans-only channel
    }

    // MARK: - The unset-Color merge refinement (css-cascade-4 §7.3)

    func testUnsetColorDoesNotBlockInheritance() throws {
        // Own `color: unset` must let the ancestor's red flow in — the
        // accent-color-parent-currentcolor chain (checkbox turns red).
        let own = [try prop("Color", #"{"original":"unset"}"#)]
        let inherited = [try prop("Color", #"{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"#)]
        let merged = InheritedText.merge(own: own, inherited: inherited)
        // The keyword entry is gone; the inherited red is the only Color.
        XCTAssertEqual(merged.filter { $0.type == "Color" }.count, 1)
        XCTAssertEqual(extractColor(merged.first { $0.type == "Color" }?.data),
                       .srgb(r: 1, g: 0, b: 0, a: 1))
        // AccentColor now rides the inheritance channel (css-ui-4 §7.1).
        XCTAssertTrue(InheritedText.inheritedTypes.contains("AccentColor"))
    }

    // MARK: - meta.attrs wire decode (strict v2 reader)

    func testMetaAttrsStrictWireDecode() throws {
        // A live-wire-shaped v2 document exercising every attrs type
        // class: strings, booleans, numbers (meter value/min/max).
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"a","name":"a","properties":[],
           "meta":{"sourceTag":"input","attrs":{"type":"checkbox","checked":true}}},
          {"id":"b","name":"b","properties":[],
           "meta":{"sourceTag":"meter","attrs":{"value":0.5,"min":0,"max":1}}},
          {"id":"c","name":"c","properties":[],
           "meta":{"sourceTag":"select","attrs":{"multiple":true,"size":"4"}}}
        ]}
        """.utf8))
        // Flat wire order preserved; every capsule decodes typed.
        let flat = try XCTUnwrap(doc.flatComponents)
        XCTAssertEqual(flat[0].meta?.attrs?.checked, true)
        XCTAssertEqual(flat[0].meta?.attrs?.type, "checkbox")
        XCTAssertEqual(flat[1].meta?.attrs?.valueNumber, 0.5)
        XCTAssertEqual(flat[1].meta?.attrs?.min, 0)
        XCTAssertEqual(flat[1].meta?.attrs?.max, 1)
        XCTAssertEqual(flat[2].meta?.attrs?.multiple, true)
        XCTAssertEqual(flat[2].meta?.attrs?.size, "4") // string per contract
    }

    func testMetaAttrsUnknownKeyIsLoud() {
        // Strict envelope posture: a key outside the pinned ten errors.
        XCTAssertThrowsError(try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"a","name":"a","properties":[],
           "meta":{"sourceTag":"input","attrs":{"placeholder":"x"}}}]}
        """.utf8)))
    }

    // MARK: - Raster pins (real UAWidgetView pixels)

    /// Rasterize a widget view at scale 1 on white (harness conditions).
    @MainActor
    private func render<V: View>(_ view: V) throws -> (px: [UInt8], w: Int, h: Int) {
        // White stage: "nothing painted" is an assertable color.
        let renderer = ImageRenderer(content: view.background(Color.white))
        renderer.scale = 1 // one buffer pixel per CSS px, like the harness
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// RGB triple at a raster point (RGBA8 buffer from render above).
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int), _ x: Int, _ y: Int) -> (Int, Int, Int) {
        let i = (y * img.w + x) * 4
        return (Int(img.px[i]), Int(img.px[i + 1]), Int(img.px[i + 2]))
    }

    @MainActor
    func testCheckedCheckboxRaster() throws {
        // The accent-color-visited ink: a 13x13 accent-filled box.
        let img = try render(UAWidgetView(spec: .init(kind: .checkbox, checked: true)))
        // Geometry pin: the intrinsic frame IS the raster size.
        XCTAssertEqual(img.w, 13); XCTAssertEqual(img.h, 13)
        // Palette pin: interior accent #0075FF sampled at (2,3) — inside
        // the fill (r2 corners end at y2) and clear of the check-mark
        // stroke AND its round start cap (which grazes (2,6): the cap
        // circle around (3.25,6.75) r0.8 antialiases into that pixel —
        // the first sample point tried, half-white blended).
        let c = rgb(img, 2, 3)
        XCTAssertLessThanOrEqual(abs(c.0 - 0x00), 24)
        XCTAssertLessThanOrEqual(abs(c.1 - 0x75), 24)
        XCTAssertLessThanOrEqual(abs(c.2 - 0xFF), 24)
        // The white check mark exists somewhere in the interior: count
        // near-white pixels over the box (single-pixel sampling is AA-
        // fragile; the polyline covers ≥6 px of ink at stroke 1.6).
        var whiteish = 0
        for y in 2..<11 {
            for x in 2..<11 {
                let m = rgb(img, x, y)
                // near-white = every channel bright (the accent field
                // keeps red ≈ 0, so this can only be check-mark ink).
                if m.0 > 180 && m.1 > 180 && m.2 > 180 { whiteish += 1 }
            }
        }
        XCTAssertGreaterThanOrEqual(whiteish, 3, "check mark ink missing")
    }

    @MainActor
    func testUncheckedCheckboxRaster() throws {
        // Wave-20 skeptic pin — the unchecked side of the checked raster
        // below: 13x13 white interior + #767676 hairline border.
        let img = try render(UAWidgetView(spec: .init(kind: .checkbox)))
        XCTAssertEqual(img.w, 13); XCTAssertEqual(img.h, 13)
        // Interior is white (indistinguishable from the stage — the ink
        // assertion is the border ring around it).
        let interior = rgb(img, 6, 6)
        XCTAssertGreaterThan(interior.0 + interior.1 + interior.2, 720)
        // Top border row: the 1px stroke centered at y0.5 fully covers
        // pixel row 0 at mid-width → #767676 within AA tolerance.
        let edge = rgb(img, 6, 0)
        XCTAssertLessThanOrEqual(abs(edge.0 - 0x76), 40)
        XCTAssertLessThanOrEqual(abs(edge.1 - 0x76), 40)
        XCTAssertLessThanOrEqual(abs(edge.2 - 0x76), 40)
    }

    @MainActor
    func testButtonRaster() throws {
        // Wave-20 skeptic pin — the button replica through the REAL
        // UAWidgetView (real measureLabel, not the fake 7px probe):
        // label-hugging chrome slab, hairline border, black label ink.
        let img = try render(UAWidgetView(spec: .init(kind: .button, label: "button")))
        XCTAssertEqual(img.h, 21)
        // The raster width IS the intrinsic width from the live probe.
        XCTAssertEqual(img.w, Int(UAWidgetView.measureLabel("button") + 14))
        // Chrome interior left of the label inset (x3, mid-height).
        let chrome = rgb(img, 3, 10)
        XCTAssertLessThanOrEqual(abs(chrome.0 - 0xEF), 24)
        XCTAssertLessThanOrEqual(abs(chrome.1 - 0xEF), 24)
        XCTAssertLessThanOrEqual(abs(chrome.2 - 0xEF), 24)
        // Hairline border on the top edge (mid-width, straight segment).
        let border = rgb(img, img.w / 2, 0)
        XCTAssertLessThanOrEqual(abs(border.0 - 0x76), 40)
        XCTAssertLessThanOrEqual(abs(border.1 - 0x76), 40)
        // Label ink exists: count near-black pixels over the label band
        // (single-pixel glyph sampling is AA/kerning-fragile).
        var dark = 0
        for y in 4..<18 {
            for x in 7..<(img.w - 7) {
                let m = rgb(img, x, y)
                if m.0 < 90 && m.1 < 90 && m.2 < 90 { dark += 1 }
            }
        }
        XCTAssertGreaterThanOrEqual(dark, 5, "button label ink missing")
    }

    @MainActor
    func testRangeRaster() throws {
        // Default range: the 129x21 atom box; thumb mid-travel at x≈64.
        let img = try render(UAWidgetView(spec: .init(kind: .range, fraction: 0.5)))
        XCTAssertEqual(img.w, 129); XCTAssertEqual(img.h, 21)
        // Thumb center is accent; right track end is chrome #EFEFEF.
        let thumb = rgb(img, 64, 10)
        XCTAssertLessThanOrEqual(abs(thumb.1 - 0x75), 24)
        XCTAssertLessThanOrEqual(abs(thumb.2 - 0xFF), 24)
        let track = rgb(img, 120, 10)
        XCTAssertLessThanOrEqual(abs(track.0 - 0xEF), 24)
        XCTAssertLessThanOrEqual(abs(track.1 - 0xEF), 24)
    }

    @MainActor
    func testProgressRaster() throws {
        // Half progress: the 160x16 gauge box, 8px ink centered — accent
        // left half, chrome right half at the box midline.
        let img = try render(UAWidgetView(spec: .init(kind: .progress, fraction: 0.5)))
        XCTAssertEqual(img.w, 160); XCTAssertEqual(img.h, 16)
        let fill = rgb(img, 40, 8)   // inside the accent chunk
        XCTAssertLessThanOrEqual(abs(fill.2 - 0xFF), 24)
        let track = rgb(img, 120, 8) // inside the chrome remainder
        XCTAssertLessThanOrEqual(abs(track.0 - 0xEF), 24)
        // Above the ink band (y1) the box is unpainted → the white stage.
        let stage = rgb(img, 80, 1)
        XCTAssertGreaterThan(stage.0 + stage.1 + stage.2, 700)
    }
}
