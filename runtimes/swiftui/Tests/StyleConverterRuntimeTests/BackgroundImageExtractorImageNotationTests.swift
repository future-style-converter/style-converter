//
//  BackgroundImageExtractorImageNotationTests.swift
//  Wave 48, lane F3 (S2 must-fix 1 + S4 defect 2) — the iOS half of the
//  image() seam pins, byte-parallel with the Compose twin
//  (ColorExtractorImageNotationTest.kt: same wires, same precedence).
//
//  The applied image() seam (css-images-4 §2.1, wave-48 lane W5) shipped
//  with ZERO unit pins, and S4's executed twin probe caught a real
//  divergence: on a colour key that fails to parse, Kotlin fell through
//  `?:` to srcs while this extractor returned .color(.unknown) — which
//  the applier paints as CLEAR, silently eating the still-usable
//  sources. The fix (BackgroundImageExtractor.swift "image" case) makes
//  the `.unknown` sentinel fall through to srcs; these pins freeze the
//  aligned table.
//
//  Wires are VERBATIM: the corpus rows (001/002/005) are the freshly
//  converted WPT css-image-fallbacks-and-annotations layers captured by
//  skeptic S2 (probe5-wires.json, re-derivable from tools/titan/runs/
//  wave48-cal/sections/css-images/per-test-ir/); the adversarial rows
//  (garbage-colour, object-src, bare image()) are skeptic S4's executed
//  probe payloads (S4ImageNotationSeamProbe.kt).
//

import XCTest
@testable import StyleConverterRuntime

final class BackgroundImageExtractorImageNotationTests: XCTestCase {

    /// Feed one raw BackgroundImage wire string through the SHIPPED
    /// extractor entry point — the exact seam the probes executed
    /// (IRValue is Decodable, so the JSON bytes stay verbatim).
    private func config(_ json: String) -> BackgroundImageConfig? {
        let value = try! JSONDecoder().decode(IRValue.self, from: Data(json.utf8))
        return BackgroundImageExtractor.extract(
            from: [IRProperty(type: "BackgroundImage", data: value)])
    }

    func testVerbatim001WireFallbackColourWinsOverTheSrcsList() {
        // fallbacks-and-annotations-001: `image("green.png", green)` — the
        // source is deliberately missing in WPT, so §2.1 says the colour
        // paints; loadability is unknowable at extract time, so colour
        // presence IS the precedence rule this seam implements.
        let cfg = config(
            #"[{"type":"image","srcs":["green.png"],"color":{"srgb":{"r":0.0,"g":0.5019607843137255,"b":0.0},"original":"green"}}]"#
        )
        // Exactly one layer: the corpus green as a solid fill (alpha
        // defaults to 1.0 when the srgb block omits `a`).
        XCTAssertEqual(cfg?.layers,
                       [.color(.srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1))])
    }

    func testVerbatim002WireNoColourMeansTheFirstSrcRidesTheUrlPipeline() {
        // fallbacks-and-annotations-002: `image("support/1x1-green.png")`
        // — no fallback colour on the wire, so the first candidate source
        // takes the existing url path (the S4-probed srcs-fallback row).
        let cfg = config(#"[{"type":"image","srcs":["support/1x1-green.png"]}]"#)
        // One url layer carrying the src string byte-for-byte.
        XCTAssertEqual(cfg?.layers, [.url("support/1x1-green.png")])
    }

    func testObjectWrappedSrcTheDataUriIRUrlShapeReachesTheUrlPipeline() {
        // The IRUrl wire has TWO shapes (BackgroundImageSerializer.kt):
        // bare string, or {url, data:true} for data URIs — this pins the
        // object arm of the srcs read (S4's object-src probe payload).
        let cfg = config(
            #"[{"type":"image","srcs":[{"url":"data:image/png;base64,AA==","data":true}]}]"#
        )
        // The url string inside the object is authoritative.
        XCTAssertEqual(cfg?.layers, [.url("data:image/png;base64,AA==")])
    }

    func testUnparseableColourFallsThroughToSrcsTheTwinAlignedRule() {
        // S4 defect 2's exact divergence wire: the colour key is present
        // but garbage, so extractColor yields the `.unknown` sentinel
        // (ColorValue.swift) and the seam must fall through to the
        // sources instead of painting clear — the behaviour Kotlin's
        // null-then-`?:` chain always had. Before wave-48 F3 this
        // asserted .color(.unknown), i.e. an invisible layer.
        let cfg = config(#"[{"type":"image","srcs":["x.png"],"color":{"garbage":true}}]"#)
        // The first (only) source paints — never a clear layer.
        XCTAssertEqual(cfg?.layers, [.url("x.png")])
    }

    func testVerbatim005WireEmptySrcsWithAColourPaintsTheColourAlone() {
        // fallbacks-and-annotations-005: `image(rgba(0,0,255,0.5))` — the
        // colour is declared ALONE (srcs is EMPTY, not missing-source like
        // 001), and §2.1 makes the colour the outcome; the test's second
        // layer is the plain data-URI url() the same WPT file composites
        // beneath it, riding the untagged {url, data} layer shape.
        let cfg = config(
            #"[{"type":"image","srcs":[],"color":{"srgb":{"r":0.0,"g":0.0,"b":1.0,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}}},{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]"#
        )
        // Exactly the two wire layers — nothing invented, nothing dropped.
        XCTAssertEqual(cfg?.layers.count, 2)
        // Layer 0: half-alpha blue solid (the colour, NOT a src fallback).
        XCTAssertEqual(cfg?.layers.first, .color(.srgb(r: 0, g: 0, b: 1, a: 0.5)))
        // Layer 1: the sibling data-URI layer survives untouched, so the
        // image() arm cannot have swallowed its neighbours.
        guard case .url(let u)? = cfg?.layers.last else {
            return XCTFail("expected the sibling data-URI url layer")
        }
        XCTAssertTrue(u.hasPrefix("data:image/png,"))
    }

    func testBareImageWithNeitherColourNorSrcsExtractsNoLayer() {
        // Neither branch of the precedence has anything to paint: the
        // entry parses to nil, compactMap drops it, and an all-empty
        // layer list makes extract return nil — an honest no-op, not a
        // phantom clear layer (S4's empty-image probe).
        XCTAssertNil(config(#"[{"type":"image"}]"#))
    }
}
