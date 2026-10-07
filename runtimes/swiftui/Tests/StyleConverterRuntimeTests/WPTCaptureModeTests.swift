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
//       canvas (the EmptyFlexContainerTests approach). Since wave 51 PR (A)
//       the synthesized name label is HARNESS CHROME (HarnessLabelChrome,
//       drawn by the capture canvas, never by the runtime), so the pixel
//       pass proves the runtime paints NO name glyphs in either mode while
//       real element text still renders (and flips to spec-black ink in
//       WPT mode). Label presence/geometry: HarnessLabelChromeRasterTests.
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
    /// background. The block-font name label's FIXED rgba(237,237,237,
    /// 179/255) ink (BlockLabel.labelColor) would separate from this fill
    /// where it would vanish over #eee — the light band below is where
    /// such ink lands, so the runtime pin can prove it is ABSENT (the label
    /// is harness chrome since wave 51 PR (A)). Real text in WPT mode
    /// paints the corpus-v4.1 spec-BLACK default ink (WPTCanvas.textInk
    /// via PlaceholderLabel.resolvedColor), so the pixel probes use TWO
    /// bands: the light band for would-be name-label ink, the dark band for
    /// WPT-mode prose (see labelInkPixelCount). The explicit height pins
    /// the box size whether or not any glyphs render. `text` verbatim.
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
    /// use: the default light-ink band (where a block-font name label's
    /// fixed rgba(237,237,237,179/255) would land over the #222 fill — the
    /// runtime must paint NONE there since PR (A)) and a dark band for
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

    /// Wave 51 PR (A): the RUNTIME paints NO name label in EITHER mode. The
    /// label is harness chrome now — HarnessLabelChrome, drawn by the
    /// capture canvas as a sibling overlay over the root (presence and
    /// geometry pinned in HarnessLabelChromeRasterTests). Until (A) the
    /// flag-OFF arm of this test asserted the glyphs WERE painted through
    /// this very path; that arm is now the pin that ComponentRenderer's
    /// nameless-leaf branch stays EmptyView(). Mutation-proven (EXECUTED,
    /// wave 51): restoring `BlockLabel(label: …, componentWidth: nil)` in
    /// that branch → `off` > 0, red.
    @MainActor
    func testRuntimePaintsNoNameLabelInEitherMode() throws {
        let box = try component(boxJSON(name: "background color rgb 001"))
        let off = try labelInkPixelCount(box, wpt: false)
        let on  = try labelInkPixelCount(box, wpt: true)
        // Baseline path: the runtime paints no label — the harness does.
        XCTAssertEqual(off, 0,
            "the runtime must not paint the name label (it is harness chrome " +
            "since wave 51 PR (A)) — \(off) glyph pixels painted with the flag OFF")
        // WPT path: still a clean box — zero glyph ink.
        XCTAssertEqual(on, 0,
            "WPT mode must paint no synthesized name placeholder " +
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

    // MARK: - wave 27 A-RC1: the containment gate on canvas propagation

    /// The propagation `composedBackground` implements is NOT unconditional.
    /// css-backgrounds-3 §2.11.2 propagates the root/body background to the
    /// canvas only while that element is ON the propagation path, and
    /// css-contain-2 §3.5 takes it off that path as soon as it has ANY
    /// containment — a contained body paints its background on its OWN box
    /// and the canvas keeps the UA default.
    ///
    /// MEASURED (wave-27 gate, css-contain): contain-body-bg-001..004 declare
    /// `body { background: red; contain: layout|paint|size|style }` over a
    /// white 300×200 `<p>` and share one PURE WHITE reference ("Test passes
    /// if there is no red"). All four scored 0.6268 on iOS — the whole
    /// capture flooded red. These pins hold the shared rule (web
    /// `bodyRootHasContainment`, Compose `containmentBlocksCanvasPropagation`).
    func testContainmentGateAbsentOrNoneIsNotContainment() {
        // No declaration — the common case, and the one that keeps every
        // pre-wave-27 capture byte-identical.
        XCTAssertFalse(WPTCanvas.containmentBlocksPropagation(nil))
        // An empty list is a malformed leaf, not evidence of containment.
        XCTAssertFalse(WPTCanvas.containmentBlocksPropagation([]))
        // css-contain-2 §2: `none` is the initial value and applies NO
        // containment, so the body stays on the propagation path.
        XCTAssertFalse(WPTCanvas.containmentBlocksPropagation(["NONE"]))
    }

    /// Any containment keyword blocks, regardless of KIND: the four WPT
    /// variants set layout / paint / size / style and all match the SAME
    /// all-white reference, so the gate cannot grade by kind.
    func testContainmentGateAnyKeywordBlocks() {
        for kw in ["LAYOUT", "PAINT", "SIZE", "STYLE", "STRICT", "CONTENT"] {
            XCTAssertTrue(WPTCanvas.containmentBlocksPropagation([kw]), kw)
        }
        // A multi-keyword list (`contain: size style`) needs only one token.
        XCTAssertTrue(WPTCanvas.containmentBlocksPropagation(["SIZE", "STYLE"]))
        // Case/whitespace tolerance — the reader hands tokens through verbatim.
        XCTAssertTrue(WPTCanvas.containmentBlocksPropagation([" layout "]))
    }

    /// contain-body-bg-001's exact shape: an OPAQUE red body background that
    /// would otherwise take the α=1 identity path straight onto the canvas.
    /// The gate fires BEFORE the color is read, so the canvas stays white and
    /// the body-root paints its own red box — which the test's own white
    /// `<p>` then covers, the ref's reading.
    func testComposedBackgroundContainedBodyKeepsWhiteCanvas() {
        let red = Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 1)
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: red, contained: true),
                       WPTCanvas.background)
        // Translucent ink is gated identically — no blend, just the canvas.
        let halfRed = Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 0.5)
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: halfRed, contained: true),
                       WPTCanvas.background)
    }

    /// `contained` defaults to false, so every existing caller and every
    /// non-contained document keeps its wave-26 result verbatim.
    func testComposedBackgroundUncontainedDefaultUnchanged() {
        let grey = Color(.sRGB, red: 0.4, green: 0.4, blue: 0.4, opacity: 1)
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: grey), grey)
        XCTAssertEqual(WPTCanvas.composedBackground(resolved: grey, contained: false), grey)
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

    // MARK: - wave-25 round 3: the composed canvas FRAME + ICB extents

    /// The ONE number all three platforms read for the ref's image-space
    /// frame (capture-browser-ref.mjs CANVAS_PAD_PX = 16, Compose
    /// WPT_CANVAS_FRAME_DP, web CANVAS_FRAME_PX). Since CAL-RC1 it is NOT
    /// the author-beatable body padding: the ref renders unpadded at 358
    /// and the frame is memcpy'd around the finished PNG, so no cascade
    /// can cancel it.
    func testCanvasFrameIsTheRefImagePad() {
        XCTAssertEqual(WPTCanvas.canvasFramePx, 16)
    }

    /// The ICB is the ref's RENDER VIEWPORT, not the framed image:
    /// 390 − 2×16 = 358 (REF_RENDER_WIDTH) and 600 − 2×16 = 568
    /// (REF_RENDER_MIN_HEIGHT). This is what out-of-flow boxes anchor
    /// against (css-position-3 §3.1/§3.2), so a `right: 0` box lands flush
    /// with the CONTENT edge and the frame stays visible.
    func testIcbExtentIsTheRefRenderViewport() {
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 390), 358)
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 600), 568)
        // An explicit zero frame is the identity — the frame is a pure
        // translation of the canvas, it re-derives nothing.
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 390, frame: 0), 390)
    }

    /// A degenerate canvas must never yield a NEGATIVE containing block:
    /// end-anchored boxes would be placed off the far side of the surface.
    func testIcbExtentNeverGoesNegative() {
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 20), 0)
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 0), 0)
    }

    // MARK: - Wave 26 (lane RES residual 2) — the PUBLISHED viewport basis.
    //
    // Byte-parallel with the Compose twin's WptComposedGeometryTest: the
    // composed canvas publishes the REF's render viewport as the vw/vh and
    // runtime-v1 media basis, not the 390-wide framed image (and not the
    // 844-tall device window). capture-browser-ref.mjs lays each ref page out
    // at REF_RENDER_WIDTH × max(scrollHeight, REF_RENDER_MIN_HEIGHT) and adds
    // the 16px frame to the finished PNG in IMAGE space, where no `vw` and no
    // media query can see it.

    func testPublishedViewportBasisIsTheRefRenderViewport() {
        // Width: 358, the exact number the ref lays out at.
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 390), 358)
        // Height on a floor-height canvas: 568 (the historical value, so
        // every capture whose content fits the floor is byte-identical).
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: max(600, 600)), 568)
    }

    func testPublishedViewportHeightGrowsWithTheDocument() {
        // A 1200-tall composed canvas (frame + 1168 of flow) publishes 1168 —
        // the ref's second-pass viewport height — so a `100vh` value and a
        // `bottom: 0` hoisted box agree with the raster instead of both
        // pinning the 568 floor. `max(measured, minHeight)` is the canvas's
        // own floor rule, spelled here exactly as the harness applies it.
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: max(1200, 600)), 1168)
        // One point past the floor already tracks the content.
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: max(601, 600)), 569)
        // A degenerate mid-layout measurement still floors, never stubs.
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: max(0, 600)), 568)
    }

    func testPublishedViewportIsNeverTheFramedImage() {
        // Dark-stage guard in pin form: the published basis must NEVER equal
        // the outer image extent — that equality was the wave-25 shape, and
        // it is the only one that could re-base a capture's vw by 32pt.
        XCTAssertNotEqual(WPTCanvas.icbExtent(canvasExtent: 390), 390)
        XCTAssertEqual(WPTCanvas.icbExtent(canvasExtent: 900),
                       900 - 2 * WPTCanvas.canvasFramePx)
    }
    // MARK: - wave-53 lane L3 (item A) — the ROOT's background IMAGE on the canvas
    //
    // css-backgrounds-3 §2.11.2: the composed canvas propagated the body-root
    // COLOUR only (wave52-ship display-contents-root-background ios f 0.5346,
    // background-attachment-margin-root-001/-002 ios f 0.4509 / 0.3391). These
    // pin the runtime's RootBackgroundPropagation (plan, strip) AND its
    // RootCanvasBackground paint by an ImageRenderer pixel pass mounted exactly
    // as ComposedCaptureCanvas mounts it (`.background(…)` on the 390-wide
    // surface, before the colour). Payloads VERBATIM from
    // tools/titan/runs/wave52-ship/sections/<section>/per-test-ir/.
    //
    // EXECUTED MUTATIONS (RootBackgroundPropagation.swift, these tests run
    // alone, source restored byte-exact — sha256 in
    // tools/titan/results/wave53-canvas-root/_note.md):
    //   SA1 plan() returns nil (colour-only read)        → testRootBgA1* red.
    //   SA2 the strip returns `roots`                     → testRootBgA1TargetPlanAndStrip red.
    //   SA3 `uniform` forced true                         → testRootBgA2MarginRoot001* red.
    //   SA4 attachment ignored (every layer `.scroll`)    → testRootBgA2MarginRoot002* red.
    //   SA5 dataPngIs1x1 true for any PNG url             → testRootBgA3* red.
    //   SA6 the `contained` early return dropped          → testRootBgA4* red.

    /// css-display/display-contents-root-background — both roots, verbatim.
    private let rootBgTarget = """
        {"id":"wpt__css-display__display-contents-root-background__0-234","name":"wpt__css-display__display-contents-root-background__0","properties":[{"type":"Display","data":"CONTENTS"},{"type":"BackgroundImage","data":[{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-display__display-contents-root-background__1-235","name":"wpt__css-display__display-contents-root-background__1","properties":[],"text":"Pass if the background is green.","meta":{"sourceTag":"p"}}
        """
    /// css-backgrounds/background-attachment-margin-root-001 — verbatim (`scroll, fixed`).
    private let rootBgMR001 = """
        {"id":"wpt__css-backgrounds__background-attachment-margin-root-001__0-091","name":"wpt__css-backgrounds__background-attachment-margin-root-001__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"scroll"},{"type":"fixed"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}}
        """
    /// css-backgrounds/background-attachment-margin-root-002 — verbatim (`fixed, scroll`).
    private let rootBgMR002 = """
        {"id":"wpt__css-backgrounds__background-attachment-margin-root-002__0-092","name":"wpt__css-backgrounds__background-attachment-margin-root-002__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"fixed"},{"type":"scroll"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}}
        """
    /// css-cascade/initial-background-color — verbatim (colour-only body).
    private let rootBgInitial = """
        {"id":"wpt__css-cascade__initial-background-color__0-033","name":"wpt__css-cascade__initial-background-color__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-cascade__initial-background-color__1-034","name":"wpt__css-cascade__initial-background-color__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"percentage","value":100}},{"type":"BackgroundColor","data":{"original":"initial"}}]}
        """
    /// css-color/a98rgb-003 — verbatim (colour-only body).
    private let rootBgA98 = """
        {"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-color__a98rgb-003__1-006","name":"wpt__css-color__a98rgb-003__1","properties":[],"text":"Test passes if you see a single square, and not two rectangles of different colors.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__2-007","name":"wpt__css-color__a98rgb-003__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.996078431372549,"g":0.996078431372549,"b":0.996078431372549},"original":{"r":254,"g":254,"b":254}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginBottom","data":{"px":0}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__3-008","name":"wpt__css-color__a98rgb-003__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9999300658875595,"g":1,"b":1},"original":{"type":"color","colorSpace":"a98-rgb","values":[1,1,1]}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginTop","data":{"px":0}}]}
        """
    /// css-contain/contain-body-bg-001 — verbatim (contained colour body).
    private let rootBgContain = """
        {"id":"wpt__css-contain__contain-body-bg-001__0-003","name":"wpt__css-contain__contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Contain","data":["LAYOUT"]}],"meta":{"role":"body-root","lang":"en"}},
        {"id":"contain-body-bg-001__0-004","name":"contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}}],"slot":{"parent":"wpt__css-contain__contain-body-bg-001__0-003"},"text":"Test passes if there is no red.","meta":{"sourceTag":"p","lang":"en"}}
        """

    /// Decode a v2 document's components exactly as the harness does.
    private func rootBgRoots(_ componentsJSON: String) throws -> [IRComponent] {
        let json = "{\"irVersion\":2,\"minReaderVersion\":2,\"components\":[\(componentsJSON)]}"
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8)).components
    }

    /// The harness's resolver, verbatim in shape (CaptureCanvas.rootImagePlan).
    private func rootBgPlan(_ roots: [IRComponent]) -> RootBackgroundPropagation.Plan? {
        guard let body = roots.first(where: { $0.meta?.role == "body-root" }) else { return nil }
        let m = UABlockMargin.canvasBodyMargin(roots)
        let contain = body.properties.first(where: { $0.type == "Contain" })?.data.arrayValue?.compactMap { $0.stringValue }
        return RootBackgroundPropagation.plan(body.properties, marginTop: m.top, marginLeft: m.left,
                                              contained: WPTCanvas.containmentBlocksPropagation(contain))
    }

    /// Render the canvas background exactly as ComposedCaptureCanvas mounts it
    /// (390×600 surface, the image view behind the content, the white colour
    /// behind it), scale 1; returns (width, RGBA bytes).
    @MainActor
    private func rootBgPixels(_ roots: [IRComponent], _ plan: RootBackgroundPropagation.Plan) throws -> (Int, [UInt8]) {
        let body = try XCTUnwrap(roots.first(where: { $0.meta?.role == "body-root" }))
        let view = Color.clear.frame(width: 390, height: 600)
            .background(RootCanvasBackground(properties: body.properties, plan: plan, frame: WPTCanvas.canvasFramePx))
            .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let ctx = try XCTUnwrap(CGContext(data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8,
                                          bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        XCTAssertEqual(cg.width, 390); XCTAssertEqual(cg.height, 600)
        return (cg.width, buf)
    }

    /// One pixel (r, g, b) at (x, y) of a rendered buffer.
    private func rootBgAt(_ px: (Int, [UInt8]), _ x: Int, _ y: Int) -> (Int, Int, Int) {
        let i = (y * px.0 + x) * 4
        return (Int(px.1[i]), Int(px.1[i + 1]), Int(px.1[i + 2]))
    }

    /// The 16-px frame's pixel coordinates on the 390×600 surface.
    private func rootBgFrame() -> [(Int, Int)] {
        var out: [(Int, Int)] = []
        for y in 0..<600 { for x in 0..<390 where x < 16 || x >= 374 || y < 16 || y >= 584 { out.append((x, y)) } }
        return out
    }

    func testRootBgA1TargetPlanAndStrip() throws {
        let roots = try rootBgRoots(rootBgTarget)
        // One scroll layer at the ICB corner, uniform (1×1 PNG, initial repeat).
        XCTAssertEqual(rootBgPlan(roots), RootBackgroundPropagation.Plan(
            attachments: [.scroll], origins: [.init(x: 0, y: 0)], uniform: true))
        // §2.11.2 "not painted again": the body-root keeps Display only.
        let out = RootBackgroundPropagation.withCanvasOwnedRootBackground(roots, rootBgPlan(roots))
        XCTAssertEqual(out.first(where: { $0.meta?.role == "body-root" })?.properties.map(\.type), ["Display"])
        XCTAssertEqual(out[1].id, roots[1].id)
    }

    @MainActor
    func testRootBgA1TargetPaintsTheFramedSurfaceGreen() throws {
        let roots = try rootBgRoots(rootBgTarget)
        let px = try rootBgPixels(roots, try XCTUnwrap(rootBgPlan(roots)))
        // The 1×1 (0,128,0) tile covers the WHOLE surface, frame included (F2).
        let green = { (p: (Int, Int, Int)) in p.0 <= 8 && abs(p.1 - 128) <= 8 && p.2 <= 8 }
        let frame = rootBgFrame()
        XCTAssertEqual(frame.filter { green(self.rootBgAt(px, $0.0, $0.1)) }.count, frame.count)
        XCTAssertTrue(green(rootBgAt(px, 195, 300)))
    }

    func testRootBgA2MarginRoot001PlanIsIcbOnly() throws {
        // Layer 0 is a real green→blue ramp → not uniform; origins per §3.4.
        XCTAssertEqual(rootBgPlan(try rootBgRoots(rootBgMR001)), RootBackgroundPropagation.Plan(
            attachments: [.scroll, .fixed], origins: [.init(x: 50, y: 50), .init(x: 0, y: 0)], uniform: false))
    }

    /// Rows on the x=195 column where green jumps by > 60 (navy → green: a 100-px band restarts).
    private func rootBgBandStarts(_ px: (Int, [UInt8])) -> [Int] {
        (17..<584).filter { rootBgAt(px, 195, $0).1 - rootBgAt(px, 195, $0 - 1).1 > 60 }
    }

    @MainActor
    func testRootBgA2MarginRoot001PixelsFrameWhiteBandAt66() throws {
        let roots = try rootBgRoots(rootBgMR001)
        let px = try rootBgPixels(roots, try XCTUnwrap(rootBgPlan(roots)))
        // The frame keeps the colour (white); the ICB is tiled, phase = the root box (y66).
        XCTAssertEqual(rootBgFrame().filter { self.rootBgAt(px, $0.0, $0.1) != (255, 255, 255) }.count, 0)
        XCTAssertEqual(rootBgBandStarts(px), [66, 166, 266, 366, 466, 566])
    }

    @MainActor
    func testRootBgA2MarginRoot002PixelsBandAtTheIcbCorner() throws {
        let roots = try rootBgRoots(rootBgMR002)
        let plan = try XCTUnwrap(rootBgPlan(roots))
        XCTAssertEqual(plan.origins, [.init(x: 0, y: 0), .init(x: 50, y: 50)])
        // `fixed` top layer → phase = the viewport (ICB) corner: bands restart at y16.
        let px = try rootBgPixels(roots, plan)
        let starts = rootBgBandStarts(px)
        XCTAssertEqual(starts, [116, 216, 316, 416, 516])
        XCTAssertGreaterThan(rootBgAt(px, 195, 16).1, 100)  // the first band opens green at the ICB top
    }

    func testRootBgA3IhdrUniformity() throws {
        let body = try XCTUnwrap(try rootBgRoots(rootBgTarget).first)
        let url = try XCTUnwrap(body.properties.first(where: { $0.type == "BackgroundImage" })?.data.arrayValue?.first?["url"]?.stringValue)
        XCTAssertTrue(RootBackgroundPropagation.dataPngIs1x1(url))
        // The SAME payload with IHDR width/height patched to 2 → not uniform.
        let twoByTwo = url.replacingOccurrences(of: "%49%48%44%52%00%00%00%01%00%00%00%01",
                                                with: "%49%48%44%52%00%00%00%02%00%00%00%02")
        XCTAssertNotEqual(twoByTwo, url)
        XCTAssertFalse(RootBackgroundPropagation.dataPngIs1x1(twoByTwo))
        XCTAssertFalse(RootBackgroundPropagation.dataPngIs1x1("data:image/gif,%47%49%46%38%39%61%01%00%01%00"))
    }

    func testRootBgA4ContainedDoesNotPropagate() throws {
        // The target + `contain: layout` → off the propagation path (css-contain-2 §2).
        let json = rootBgTarget.replacingOccurrences(of: "{\"type\":\"Display\",\"data\":\"CONTENTS\"}",
            with: "{\"type\":\"Display\",\"data\":\"CONTENTS\"},{\"type\":\"Contain\",\"data\":[\"LAYOUT\"]}")
        XCTAssertNotEqual(json, rootBgTarget)
        XCTAssertNil(rootBgPlan(try rootBgRoots(json)))
    }

    @MainActor
    func testRootBgA5ColourOnlyBodiesAreIdentity() throws {
        for doc in [rootBgInitial, rootBgA98, rootBgContain] {
            let roots = try rootBgRoots(doc)
            XCTAssertNil(rootBgPlan(roots))
            // No plan → the strip hands back the same components.
            let out = RootBackgroundPropagation.withCanvasOwnedRootBackground(roots, nil)
            XCTAssertEqual(out.map(\.properties), roots.map(\.properties))
        }
        // And the harness's empty background slot is paint-neutral: the SAME
        // bytes with and without `.background(<no plan>)`.
        let none: RootCanvasBackground? = nil
        let base = Color.clear.frame(width: 390, height: 600).background(Color.white)
        let withSlot = Color.clear.frame(width: 390, height: 600).background(none).background(Color.white)
        let a = ImageRenderer(content: base); a.scale = 1
        let b = ImageRenderer(content: withSlot); b.scale = 1
        let da = try XCTUnwrap(a.cgImage?.dataProvider?.data) as Data
        let db = try XCTUnwrap(b.cgImage?.dataProvider?.data) as Data
        XCTAssertEqual(da, db)
    }
}
