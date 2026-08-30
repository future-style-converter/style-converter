//
//  BackgroundImageNotationResolutionTests.swift
//  Wave 49, lane A3 — the css-images-4 §2.5 CANDIDATE WALK, pinned on the
//  iOS side. Byte-parallel twin: ColorApplierImageNotationTest.kt.
//
//  WHY THESE PINS EXIST. The wave-48 seam collapsed an image() value at
//  EXTRACT time (colour if present, else srcs[0]) and therefore threw
//  candidates 2..n away. WPT css-image-fallbacks-and-annotations003/004 open
//  with `1x1-green.svg`, a path that does not exist beside those tests, so
//  the only paintable candidate was one of the discarded ones — measured at
//  the wave-48 gate as iOS 0.9990 `colorFailed`, 17% of the canvas in pure
//  red (255,0,0) where the reference is `green` (0,128,0). These tests pin
//  the walk that fixes it AND the two outcomes that must NOT move.
//
//  THE PAYLOADS ARE CORPUS BYTES, NOT INVENTIONS. `greenPngDataURI` is the
//  verbatim data: URI the converter already emits for
//  css-images/support/1x1-green.png (it appears on the 005 wire — see
//  tools/titan/runs/wave48-final/sections/css-images/per-test-ir/
//  wpt__css-images__css-image-fallbacks-and-annotations005.json — and is
//  byte-identical to percent-encoding that file). `greenGifDataURI` is the
//  same encoding of support/1x1-green.gif, 004's only resolvable candidate.
//

import XCTest
@testable import StyleConverterRuntime

final class BackgroundImageNotationResolutionTests: XCTestCase {

    /// support/1x1-green.png as the percent-encoded data: URI the corpus
    /// wire already carries (verbatim from the 005 per-test IR).
    private let greenPngDataURI = "data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82"

    /// support/1x1-green.gif, same encoding — 004's last candidate.
    private let greenGifDataURI = "data:image/gif,%47%49%46%38%39%61%01%00%01%00%80%00%00%00%7f%00%00%00%00%21%f9%04%00%07%00%ff%00%2c%00%00%00%00%01%00%01%00%00%02%02%44%01%00%3b"

    // ── The three §2.1 outcomes ───────────────────────────────────────────

    func testFirstDecodableCandidateWinsEvenWhenItIsNotTheFirstListed() {
        // The 003 shape: candidate 1 is the missing `1x1-green.svg`, and the
        // paintable one is BEHIND it. Before the candidate walk this value
        // resolved to `.url("1x1-green.svg")` and painted nothing, leaving
        // the forbidden red background-color showing.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: ["1x1-green.svg", greenPngDataURI, "support/1x1-green.gif"],
            fallback: nil)
        XCTAssertEqual(layer, .url(greenPngDataURI))
    }

    func testTheWalkStopsAtTheWinnerAndNeverPrefersALaterCandidate() {
        // §2.1 is "the FIRST one that can be displayed" — a decodable
        // candidate must not be overridden by a decodable one behind it.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: [greenPngDataURI, greenGifDataURI],
            fallback: nil)
        XCTAssertEqual(layer, .url(greenPngDataURI))
    }

    func testTheLastCandidateStillWinsTheFourShape() {
        // The 004 shape: BOTH leading candidates are missing files and only
        // the third resolves. A first-src-only read can never reach it.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: ["1x1-green.svg", "1x1-green.png", greenGifDataURI],
            fallback: nil)
        XCTAssertEqual(layer, .url(greenGifDataURI))
    }

    func testAllCandidatesDeclinedFallsBackToTheColour() {
        // The 001 shape: `image("green.png", green)`. Every candidate is
        // undecodable, so §2.1 paints the colour — the SAME layer the
        // pre-wave-49 extractor produced, which is why 001 keeps passing.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: ["green.png"],
            fallback: .srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1))
        XCTAssertEqual(layer, .color(.srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1)))
    }

    func testEmptyCandidateListPaintsTheColourAlone() {
        // The 005 shape: `image(rgba(0,0,255,0.5))` — no walk to run, the
        // colour IS the image (§2.1's src-less form).
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: [], fallback: .srgb(r: 0, g: 0, b: 1, a: 0.5))
        XCTAssertEqual(layer, .color(.srgb(r: 0, g: 0, b: 1, a: 0.5)))
    }

    func testAllCandidatesDeclinedWithNoColourPaintsNothing() {
        // The 002/003/004 shape as the wire stands TODAY (no host asset
        // delivery): nothing is decodable and there is no fallback colour,
        // so the layer paints nothing and the element's own
        // background-color shows through — the browser's failed-load
        // visual, and the honest capture this lane does not fake.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: ["support/1x1-green.png"], fallback: nil)
        XCTAssertEqual(layer, BackgroundImageLayer.none)
    }

    // ── The precedence change this lane makes, stated as a test ───────────

    func testALoadableSourceNowBeatsAFallbackColour() {
        // THE ONE BEHAVIOUR THIS LANE DELIBERATELY CHANGES. The wave-48
        // seam painted the colour whenever one was present, because the
        // extractor could not know whether a source would load. The paint
        // path CAN know, and §2.1 says a displayable source wins — so a
        // decodable candidate must now beat the fallback colour. No corpus
        // test carries this combination today; the pin is what stops the
        // old precedence creeping back in unnoticed.
        let layer = BackgroundImageLayer.resolveImageNotation(
            srcs: [greenPngDataURI],
            fallback: .srgb(r: 1, g: 0, b: 0, a: 1))
        XCTAssertEqual(layer, .url(greenPngDataURI))
    }

    // ── The pure walk, independent of any decoder ─────────────────────────

    func testChainReportsEveryDeclinedCandidateInAuthorOrder() {
        // `declined` is what keeps the fallthrough loud (the resolver logs
        // it): an investigator must be able to see WHICH candidates were
        // refused, in order, not just that something failed.
        let outcome = ImageCandidateChain.firstPaintable(["a", "b", "c"]) { src in
            src == "c" ? 42 : nil
        }
        XCTAssertEqual(outcome.src, "c")
        XCTAssertEqual(outcome.image, 42)
        XCTAssertEqual(outcome.declined, ["a", "b"])
    }

    func testChainTrimsCandidatesAndSkipsBlankWireSlotsWithoutDecliningThem() {
        // A blank entry is an empty wire slot, not a source the author
        // wrote — skipping it silently is correct; counting it as a decline
        // would put noise in the log the real declines live in.
        var probed: [String] = []
        let outcome = ImageCandidateChain.firstPaintable(["  ", " a.png ", "b"]) { src in
            probed.append(src)
            return src == "b" ? 1 : nil
        }
        XCTAssertEqual(probed, ["a.png", "b"])
        XCTAssertEqual(outcome.declined, ["a.png"])
        XCTAssertEqual(outcome.src, "b")
    }

    func testChainOnAnEmptyListDeclinesNothingAndWinsNothing() {
        // The `image(<color>)` path: no walk, no declines, no winner.
        let outcome = ImageCandidateChain.firstPaintable([]) { _ -> Int? in
            XCTFail("no candidate should ever be probed")
            return nil
        }
        XCTAssertNil(outcome.src)
        XCTAssertTrue(outcome.declined.isEmpty)
    }
}
