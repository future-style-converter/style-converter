//
//  WPTCaptureModeTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the WPT-capture-mode placeholder suppression (WPTCaptureMode.swift +
//  ComponentRenderer.suppressesNamePlaceholder). The synthesized
//  component-NAME placeholder is a harness debug label the Chromium
//  browser-ref never paints; under the WPT capture flag the renderer must
//  drop it for nameless-empty leaves — WITHOUT ever suppressing real
//  element text (the IR `_text`/rawText channel). This mirrors the web
//  harness's ?wpt=1 empty-visible-text path.
//
//  Two layers of proof:
//    1. The pure static predicate — deterministic, no render surface
//       (exactly the style of FidelityWave3Tests' isOutOfFlow pins).
//    2. An end-to-end ImageRenderer pixel pass through the real capture
//       canvas (the EmptyFlexContainerTests approach), proving the name
//       glyphs actually vanish only in the nameless-empty WPT case and
//       that the flag defaults OFF keep the placeholder byte-for-byte.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class WPTCaptureModeTests: XCTestCase {

    // MARK: - IR helpers

    /// Decode from wire JSON — IRComponent is Decodable-only, and decoding
    /// keeps the fixtures on the exact byte shapes the converter emits.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// A childless box with an explicit 120×40 size and a DARK (#222)
    /// background. The block-font name label (applier campaign) paints the
    /// FIXED rgba(237,237,237,0.7) ink — BlockLabel.labelColor, no
    /// luminance contrast pick anymore — so glyph ink only separates from
    /// the fill on a dark background (over the old #eee fill it composited
    /// to ≈237 vs 238: invisible to a pixel probe). Real text in WPT mode
    /// paints the corpus-v4.1 spec-BLACK default ink (WPTCanvas.textInk
    /// via PlaceholderLabel.resolvedColor), so the pixel probes use TWO
    /// bands: the light band for the non-WPT name label, the dark band for
    /// WPT-mode prose (see labelInkPixelCount). The explicit height pins
    /// the box size whether or not the label is present. `text` verbatim.
    private func boxJSON(name: String, text: String? = nil) -> String {
        let textField = text.map { ",\"text\":\"\($0)\"" } ?? ""
        return """
        {"id":"t","name":"\(name)"\(textField),
         "properties":[
           {"type":"Width","data":{"type":"length","px":120.0}},
           {"type":"Height","data":{"type":"length","px":40.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.13333333333333333,"g":0.13333333333333333,"b":0.13333333333333333},"original":"#222222"}}
         ]}
        """
    }

    // MARK: - Pure predicate pins (no render surface)

    /// Flag OFF is the product / baseline path — NEVER suppress, so every
    /// committed capture keeps its name placeholder exactly.
    func testPredicateFlagOffNeverSuppresses() throws {
        let nameless = try component(boxJSON(name: "background color rgb 001"))
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(nameless, wptCaptureMode: false),
            "flag OFF must never suppress the placeholder (baseline behaviour)")
    }

    /// The target case: WPT mode ON + a nameless-empty leaf ⇒ suppress.
    func testPredicateNamelessEmptyInWptModeSuppresses() throws {
        let nameless = try component(boxJSON(name: "background color rgb 001"))
        XCTAssertTrue(
            ComponentRenderer.suppressesNamePlaceholder(nameless, wptCaptureMode: true),
            "WPT mode must suppress the synthesized name placeholder for a nameless-empty leaf")
    }

    /// Real element text (`text`) is actual content — NEVER suppressed,
    /// even in WPT mode. Web gives `_text` precedence over the empty-string
    /// suppression; so do we.
    func testPredicateRealTextInWptModeNotSuppressed() throws {
        let withText = try component(boxJSON(name: "css color 001", text: "Filler text"))
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(withText, wptCaptureMode: true),
            "real element text must render even in WPT mode")
    }

    /// A component WITH children renders no synthesized name at all — there
    /// is nothing to suppress, WPT mode or not.
    func testPredicateWithChildrenNotSuppressed() throws {
        let parent = try component("""
        {"id":"p","name":"parent",
         "properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9,"g":0.9,"b":0.9},"original":"#e6e6e6"}}],
         "children":[{"id":"c","name":"child","properties":[]}]}
        """)
        XCTAssertFalse(
            ComponentRenderer.suppressesNamePlaceholder(parent, wptCaptureMode: true),
            "a component with children paints no synthesized name to suppress")
    }

    // MARK: - Line-box calibration pins (TITAN Round 4 GAP 1, height half)

    /// Flag OFF (product + baseline): bare text stays on SwiftUI's natural
    /// metrics (nil) — no calibration line box, so every committed capture
    /// is byte-identical.
    func testEffectiveLineHeightOffKeepsNaturalMetrics() {
        XCTAssertNil(
            ComponentRenderer.effectiveLineHeight(declared: nil, wptCaptureMode: false),
            "flag OFF + no IR line-height must stay nil (natural metrics)")
    }

    /// Flag ON + no IR line-height: pin the ref line box (20px @16px — the
    /// corpus-v4.1 REF_LINE_HEIGHT pin, no longer any font's `normal`
    /// metrics) so the forced-Inter bar matches the browser-ref's pinned `<p>`.
    func testEffectiveLineHeightOnPinsRefLineBox() {
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: nil, wptCaptureMode: true),
            ComponentRenderer.wptRefLineBoxPx,
            "flag ON + no IR line-height must pin the ref line box")
        // 20 == 16px root × the ref injection's unitless 1.25
        // (capture-browser-ref.mjs REF_LINE_HEIGHT) — the four-surface
        // corpus-v4.1 lock-step value.
        XCTAssertEqual(ComponentRenderer.wptRefLineBoxPx, 20)
    }

    /// An IR-declared line-height ALWAYS wins (author > calibration), flag
    /// on or off — a test that sets its own line-height keeps it exactly.
    func testEffectiveLineHeightDefersToIR() {
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: 30, wptCaptureMode: true), 30,
            "IR line-height must win over the ref-line-box pin in WPT mode")
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: 30, wptCaptureMode: false), 30,
            "IR line-height must pass through unchanged with the flag off")
    }

    // MARK: - End-to-end pixel proof

    /// Render a component through the capture-canvas contract (390pt width,
    /// 16pt padding, top-leading, scale 1) with the WPT flag set as given,
    /// and count pixels in the given channel-sum band. Two bands are in
    /// use: the default light-ink band (the non-WPT name label's fixed
    /// rgba(237,237,237,0.7) over the #222 fill) and a dark band for
    /// corpus-v4.1 WPT-mode prose (spec-black glyphs over the same fill) —
    /// see the band rationale at the counting loop below.
    @MainActor
    private func labelInkPixelCount(_ comp: IRComponent, wpt: Bool,
                                    band: Range<Int> = 480..<620) throws -> Int {
        // Mirror CaptureCanvas geometry; publish the WPT flag exactly the
        // way captureAllComponents does (`.environment(\.wptCaptureMode, …)`).
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, wpt)
            .frame(maxWidth: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
            .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        // Glyph-ink bands over the #222 box (fill sums to 102, the white
        // canvas to 765; every box edge is integer-aligned at scale 1, so
        // no fractional-coverage blend pixels muddy the bands):
        //   • light band 480..<620 (the default) — the name label's fixed
        //     rgba(237,237,237,0.7) composites to ≈176/channel (sum ≈530).
        //   • dark band 0..<60 — corpus-v4.1 WPT-mode prose paints the
        //     spec-black WPTCanvas.textInk (sum ≈0 for core glyph pixels;
        //     antialiased edges blend toward the 102 fill and are
        //     deliberately excluded so the count is unambiguous ink).
        var count = 0
        for i in stride(from: 0, to: buf.count, by: 4) {
            let sum = Int(buf[i]) + Int(buf[i + 1]) + Int(buf[i + 2])
            if band.contains(sum) { count += 1 }
        }
        return count
    }

    /// Nameless-empty leaf: flag OFF paints the name glyphs, flag ON drops
    /// them — the glyph ink must go from "present" to "none".
    @MainActor
    func testNamelessEmptyGlyphsVanishOnlyInWptMode() throws {
        let box = try component(boxJSON(name: "background color rgb 001"))
        let off = try labelInkPixelCount(box, wpt: false)
        let on  = try labelInkPixelCount(box, wpt: true)
        // Baseline path must still paint the placeholder glyphs.
        XCTAssertGreaterThan(off, 0,
            "flag OFF must keep painting the name placeholder (baseline behaviour)")
        // WPT path must leave a clean box — zero glyph ink.
        XCTAssertEqual(on, 0,
            "WPT mode must suppress the synthesized name placeholder entirely " +
            "— \(on) glyph pixels still painted")
    }

    /// Real element text survives WPT mode — and paints the corpus-v4.1
    /// spec-BLACK default ink. Through v4.0 WPT-mode prose picked the
    /// near-white 0.93@0.7 contrast ink (the light band); from the ink
    /// sub-boundary a colorless run bottoms out at WPTCanvas.textInk, so
    /// the glyphs land in the DARK band over the #222 fill and the light
    /// band goes empty — proving both presence AND the ink flip end-to-end
    /// through the real render path.
    @MainActor
    func testRealTextRendersBlackInkInWptMode() throws {
        let withText = try component(boxJSON(name: "css color 001", text: "Filler text"))
        // Black glyph ink present: real text still renders in WPT mode.
        let dark = try labelInkPixelCount(withText, wpt: true, band: 0..<60)
        XCTAssertGreaterThan(dark, 0,
            "real element text must still render in WPT mode (only the " +
            "synthesized NAME placeholder is suppressed) — and in the " +
            "corpus-v4.1 spec-black default ink")
        // The v4.0 near-white ink is gone: nothing composites into the old
        // light band anymore (a regression here means the WPT bottom-out
        // silently fell back to the stage contrast pick).
        let light = try labelInkPixelCount(withText, wpt: true)
        XCTAssertEqual(light, 0,
            "WPT-mode default prose must not paint the pre-v4.1 near-white " +
            "ink — \(light) light-band pixels still painted")
    }

    // MARK: - WPT canvas background split (TITAN-WHITE lane, corpus-v4)

    /// The corpus-v4 WPT canvas is opaque WHITE: WPT reftests are authored
    /// against the spec-default white page, so white ink must vanish into
    /// the canvas exactly as it does in the white browser-ref
    /// (capture-browser-ref.mjs CANVAS_BG) and the web/Android WPT
    /// canvases. `Color(white: 1)` is the SwiftUI literal for #FFFFFF.
    func testWptCanvasBackgroundIsWhite() {
        XCTAssertEqual(WPTCanvas.background, Color(white: 1))
    }

    /// The pure mode split (the 1:1 twin of Compose's
    /// `captureCanvasBackground`, pinned by WptCanvasBackgroundTest.kt):
    /// WPT capture mode paints the corpus-v4 white; every other path gets
    /// the caller's stage color back VERBATIM so the committed 327-pair
    /// baselines (captured on the harness's dark #1A1A2E stage) stay
    /// byte-identical. The harness wiring is separately pinned by
    /// tools/titan/wpt-white-canvas.test.mjs — this holds the semantics.
    func testCaptureBackgroundSplitsOnWptMode() {
        // The harness's historical dark stage (#1A1A2E).
        let darkStage = Color(red: 0x1A / 255.0, green: 0x1A / 255.0, blue: 0x2E / 255.0)
        // WPT mode → the white canvas, regardless of the caller default.
        XCTAssertEqual(
            WPTCanvas.captureBackground(wptCaptureMode: true, defaultBackground: darkStage),
            WPTCanvas.background)
        // Non-WPT mode → the caller's stage verbatim (baseline contract).
        XCTAssertEqual(
            WPTCanvas.captureBackground(wptCaptureMode: false, defaultBackground: darkStage),
            darkStage)
        // And the split is real — the two modes never collapse to one color.
        XCTAssertNotEqual(
            WPTCanvas.captureBackground(wptCaptureMode: true, defaultBackground: darkStage),
            WPTCanvas.captureBackground(wptCaptureMode: false, defaultBackground: darkStage))
    }

    // MARK: - Composed-canvas alpha compositing (wave 15, NATIVES-ALPHA)

    /// sRGB component extraction for the compositing pins — the same UIKit
    /// bridge the production helper uses, so the assertions read exactly
    /// what a capture surface would paint.
    private func srgb(_ c: Color) -> (r: Double, g: Double, b: Double, a: Double) {
        // Every color under test is Color(.sRGB, …), for which getRed
        // always succeeds — force-unwrap keeps a failure loud, not silent.
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        XCTAssertTrue(UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a),
                      "sRGB extraction must succeed for Color(.sRGB, …)")
        return (Double(r), Double(g), Double(b), Double(a))
    }

    /// The 0.000 reproduction pin: a body-root that resolves to
    /// `rgba(0,0,0,0)` (background-color-transparent-animation-in-body's
    /// CORRECTLY baked wave-14 sampler value) must yield the WHITE canvas —
    /// fully-transparent ink composited source-over onto white IS white,
    /// exactly as the browser-ref (and web, which composites for free over
    /// its white page) renders it. Painting it verbatim flattened the
    /// opaque capture PNG to black.
    func testComposedBackgroundTransparentBodyComposesToWhite() {
        let out = srgb(WPTCanvas.composedBackground(
            resolved: Color(.sRGB, red: 0, green: 0, blue: 0, opacity: 0)))
        // α=0 → the white canvas shows through untouched, fully opaque.
        XCTAssertEqual(out.r, 1, accuracy: 1e-9)
        XCTAssertEqual(out.g, 1, accuracy: 1e-9)
        XCTAssertEqual(out.b, 1, accuracy: 1e-9)
        XCTAssertEqual(out.a, 1, accuracy: 1e-9)
    }

    /// The partial-alpha arithmetic pin: rgba(1,0,0,0.5) over white is the
    /// CSS source-over blend 0.5·src + 0.5·white per channel — pink
    /// (1, 0.5, 0.5), opaque. Pins the actual compositing math, not just
    /// the two degenerate endpoints.
    func testComposedBackgroundHalfAlphaRedIsPinkOverWhite() {
        let out = srgb(WPTCanvas.composedBackground(
            resolved: Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 0.5)))
        // r: 1·0.5 + 1·0.5 = 1; g/b: 0·0.5 + 1·0.5 = 0.5; α flattens to 1.
        XCTAssertEqual(out.r, 1.0, accuracy: 1e-9)
        XCTAssertEqual(out.g, 0.5, accuracy: 1e-9)
        XCTAssertEqual(out.b, 0.5, accuracy: 1e-9)
        XCTAssertEqual(out.a, 1.0, accuracy: 1e-9)
    }

    /// nil (no body-root / no declared background) → the corpus-v4 white
    /// canvas VERBATIM — the same fallback the harness previously inlined,
    /// so no-body documents render byte-identically across the wave-15 fix.
    func testComposedBackgroundNilFallsBackToWhiteCanvas() {
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: nil),
                       WPTCanvas.background)
    }

    /// A fully-OPAQUE body background returns VERBATIM (identity, not a
    /// re-packed round-trip): source-over with α=1 is the identity, and
    /// opaque body-root tests (a98rgb-003's grey) must stay bit-identical
    /// to their wave-14 rendering.
    func testComposedBackgroundOpaqueBodyReturnsVerbatim() {
        // a98rgb-003-ish opaque grey — any α=1 color takes the verbatim path.
        let grey = Color(.sRGB, red: 0.4, green: 0.4, blue: 0.4, opacity: 1)
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: grey), grey)
    }

    // MARK: - WPT default-ink split (corpus-v4.1: black ink)

    /// The corpus-v4.1 default TEXT INK is opaque BLACK: real WPT pages
    /// paint default prose in the UA `color: CanvasText` black, and the
    /// browser-ref injection now pins the same value
    /// (capture-browser-ref.mjs `:where(body) { color:#000 }`). Through
    /// v4.0 both sides kept near-white ink on the white canvas — vacuous
    /// prose passes. `Color(white: 0)` is the SwiftUI literal for #000000.
    func testWptDefaultTextInkIsBlack() {
        XCTAssertEqual(WPTCanvas.textInk, Color(white: 0))
    }

    /// The pure ink split (the 1:1 twin of Compose's `defaultTextInk`,
    /// pinned by WptCanvasBackgroundTest.kt): WPT capture mode bottoms
    /// text ink out at the spec black; every other path gets the caller's
    /// stage ink back VERBATIM so the committed 327-pair baselines
    /// (captured with the near-white #eee-family defaults) stay
    /// byte-identical. The harness/renderer wiring is separately pinned by
    /// tools/titan/wpt-white-canvas.test.mjs — this holds the semantics.
    func testCaptureTextInkSplitsOnWptMode() {
        // The runtime's dark-stage default ink (InheritedText.defaultTextColor).
        let stageInk = InheritedText.defaultTextColor
        // WPT mode → the spec black, regardless of the caller default.
        XCTAssertEqual(
            WPTCanvas.captureTextInk(wptCaptureMode: true, defaultInk: stageInk),
            WPTCanvas.textInk)
        // Non-WPT mode → the caller's ink verbatim (baseline contract).
        XCTAssertEqual(
            WPTCanvas.captureTextInk(wptCaptureMode: false, defaultInk: stageInk),
            stageInk)
        // And the ink split is real — the two modes never collapse.
        XCTAssertNotEqual(
            WPTCanvas.captureTextInk(wptCaptureMode: true, defaultInk: stageInk),
            WPTCanvas.captureTextInk(wptCaptureMode: false, defaultInk: stageInk))
    }

    // MARK: - WPT box-sizing default split (wave 11)

    /// The box-sizing tri-state resolution — the 1:1 twin of Compose's
    /// `SizingApplier.effectiveBoxSizing` (JUnit-pinned there) and the
    /// web harness's `body.wpt-mode [data-component-id] { box-sizing:
    /// content-box }` override. css-sizing-3 §3: the property's INITIAL
    /// value is content-box — the UA default every WPT ref is authored
    /// against — while an UNDECLARED keyword on the dark stage means the
    /// border-box status quo (the whole 327-pair corpus is captured
    /// against the web harness's `* { box-sizing: border-box }` reset).
    func testEffectiveBoxSizingSplitsOnWptMode() {
        // WPT mode: the unset slot picks up the spec initial value.
        XCTAssertEqual(
            SizeApplierMath.effectiveBoxSizing(declared: nil, wptCaptureMode: true),
            .contentBox)
        // Dark stage: unset STAYS unset — nil is the tri-state's
        // "border-box status quo" and must never silently flip.
        XCTAssertNil(
            SizeApplierMath.effectiveBoxSizing(declared: nil, wptCaptureMode: false))
        // A DECLARED border-box wins even in WPT mode — a WPT test that
        // writes `box-sizing: border-box` keeps its declared arithmetic.
        XCTAssertEqual(
            SizeApplierMath.effectiveBoxSizing(declared: .borderBox, wptCaptureMode: true),
            .borderBox)
        // A declared content-box passes through outside WPT mode too
        // (the Lane BX explicit-declaration path is mode-independent).
        XCTAssertEqual(
            SizeApplierMath.effectiveBoxSizing(declared: .contentBox, wptCaptureMode: false),
            .contentBox)
    }

    /// End-to-end frame arithmetic of the WPT default on the live wire
    /// of css/css-grid/abspos/grid-abspos-staticpos-align-items-center-
    /// large-border-padding.html: `width:100 height:500 padding:74/13/
    /// 42/13 border:23/23/45/23`. WPT mode → content-box → the frame
    /// inflates to 172×684 (the ref's padding+border-grown arithmetic);
    /// dark stage → nil → inflation (0,0), the 100×500 border box the
    /// baselines were captured with.
    func testWptBoxSizingDefaultInflatesTheGridFixtureFrame() {
        // The container's declaration list, IR shapes as the converter
        // emits them (px objects + UPPER_SNAKE keywords).
        let px: (String, Double) -> IRProperty = {
            IRProperty(type: $0, data: .object(["px": .double($1)]))
        }
        let props: [IRProperty] = [
            px("Width", 100), px("Height", 500),
            px("PaddingTop", 74), px("PaddingRight", 13),
            px("PaddingBottom", 42), px("PaddingLeft", 13),
            px("BorderTopWidth", 23), px("BorderRightWidth", 23),
            px("BorderBottomWidth", 45), px("BorderLeftWidth", 23),
            // Solid styles so the borders COUNT as painted (CSS 2.1
            // §8.5.3: style none ⇒ used width 0 — the inflation gate).
            IRProperty(type: "BorderTopStyle", data: .string("SOLID")),
            IRProperty(type: "BorderRightStyle", data: .string("SOLID")),
            IRProperty(type: "BorderBottomStyle", data: .string("SOLID")),
            IRProperty(type: "BorderLeftStyle", data: .string("SOLID")),
        ]
        // WPT capture: the fold defaults the unset keyword to contentBox…
        var wpt = StyleBuilder.build(from: props)
        wpt.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: wpt.size.boxSizing, wptCaptureMode: true)
        // …so the frame inflates by the bands: h 13+13+23+23 = 72 (→ 172
        // wide), v 74+42+23+45 = 184 (→ 684 tall).
        let inf = StyleBuilder.contentBoxInflation(wpt)
        XCTAssertEqual(inf.h, 72)
        XCTAssertEqual(inf.v, 184)
        // Dark stage: nil stays nil → zero inflation, byte-identical
        // border-box frames for every committed baseline.
        var dark = StyleBuilder.build(from: props)
        dark.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: dark.size.boxSizing, wptCaptureMode: false)
        XCTAssertNil(dark.size.boxSizing)
        XCTAssertEqual(StyleBuilder.contentBoxInflation(dark).h, 0)
        XCTAssertEqual(StyleBuilder.contentBoxInflation(dark).v, 0)
    }

    /// The injected-extent conversion that keeps the WPT default from
    /// double-counting bands: environment channels inject border-box
    /// FRAME extents, so under an effective content-box the declared
    /// slot receives frame − bands (and SizeApplier re-inflates back to
    /// the frame). Identity at inflate 0 — every non-WPT fold is
    /// byte-identical — and floored at 0 for over-padded degenerates.
    func testDeclaredFromFrameConvertsInjectedExtents() {
        // Identity when there is nothing to subtract (non-WPT paths).
        XCTAssertEqual(SizeApplierMath.declaredFromFrame(358, inflate: 0), 358)
        // frame 172 − bands 72 = declared content 100 (the grid fixture).
        XCTAssertEqual(SizeApplierMath.declaredFromFrame(172, inflate: 72), 100)
        // Over-padded degenerate floors at zero, never negative.
        XCTAssertEqual(SizeApplierMath.declaredFromFrame(40, inflate: 72), 0)
    }
}
