//
//  ScriptRunFallbackTests.swift
//  Wave 34, lane F1 — pin table for the PER-SCRIPT RUN SEGMENTATION that
//  stands in for the per-character font fallback SwiftUI cannot express
//  (no `kCTFontCascadeListAttribute` hook on `Font`).
//
//  These pins hold the exact contract the Compose twin
//  (runtimes/compose/src/test/java/com/styleconverter/runtime/typography/font/
//  ScriptRunSegmenterTest.kt) asserts case-for-case: the same script table,
//  the same run boundaries, the same Common-codepoint answers. A silent
//  divergence between the two tables is the one failure mode that would make
//  the two natives resolve DIFFERENT faces for the same string — i.e.
//  re-open the Rule-43 boundary this lane exists to close — and it cannot be
//  seen in a device capture, only here.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ScriptRunFallbackTests: XCTestCase {

    private typealias S = ScriptRunSegmenter
    private typealias Script = ScriptRunSegmenter.TextScript

    // MARK: - The script table

    func testFiveTargetScriptsRecognisedByBlockRanges() {
        // css-counter-styles-3 §6.2 `arabic-indic` digits U+0660–U+0669.
        XCTAssertEqual(S.script(of: 0x0660), .arabic)
        XCTAssertEqual(S.script(of: 0x0669), .arabic)
        // §6.2 `persian` — extended Arabic-Indic, same block.
        XCTAssertEqual(S.script(of: 0x06F5), .arabic)
        // §6.2 additive `armenian` — Ժ = U+053A, the "10" of armenian-007.
        XCTAssertEqual(S.script(of: 0x053A), .armenian)
        XCTAssertEqual(S.script(of: 0x0561), .armenian)
        // §6.2 additive `hebrew`.
        XCTAssertEqual(S.script(of: 0x05D0), .hebrew)
        // §6.2 simple-numeric `bengali` digits U+09E6–U+09EF.
        XCTAssertEqual(S.script(of: 0x09E6), .bengali)
        XCTAssertEqual(S.script(of: 0x09EF), .bengali)
        // §6.2 `khmer`/`cambodian` digits U+17E0–U+17E9.
        XCTAssertEqual(S.script(of: 0x17E0), .khmer)
        XCTAssertEqual(S.script(of: 0x17E9), .khmer)
    }

    func testInterOwnCoverageStaysDefault() {
        // Inter covers Latin, Greek and Cyrillic; the Rule-43 table excludes
        // `lower-greek` for exactly that reason. Claiming a fallback face
        // here would swap a face the ref is already matching.
        XCTAssertEqual(S.script(of: 0x0041), .default)   // A
        XCTAssertEqual(S.script(of: 0x007A), .default)   // z
        XCTAssertEqual(S.script(of: 0x0037), .default)   // 7
        XCTAssertEqual(S.script(of: 0x03B1), .default)   // α
        XCTAssertEqual(S.script(of: 0x0410), .default)   // А
    }

    func testUnknownScriptAnswersDefaultRatherThanGuessing() {
        // Honest scope: the table is the five bundled scripts and nothing
        // else. CJK, Devanagari, Thai and emoji keep the pre-lane cascade.
        XCTAssertEqual(S.script(of: 0x4E00), .default)   // 一
        XCTAssertEqual(S.script(of: 0x0915), .default)   // क
        XCTAssertEqual(S.script(of: 0x0E01), .default)   // ก
        XCTAssertEqual(S.script(of: 0x1F600), .default)  // 😀
    }

    // MARK: - Common-codepoint attachment

    func testFullStopAfterArmenianStaysDefault() {
        // MEASURED (fontTools cmap dump): NotoSansArmenian, NotoSansBengali
        // and NotoSansHebrew have NO U+002E FULL STOP, and the markers this
        // lane exists to fix are spelled `Ժ.` / `ԺԱ.` / `০.`. The wave-31
        // sketch's unconditional "Common keeps with the preceding run" would
        // have handed that period to a face with no glyph for it.
        XCTAssertEqual(S.script(of: 0x002E), .default)
        XCTAssertEqual(S.segment("ԺԱ."),
                       [S.Run(start: 0, end: 2, script: .armenian),
                        S.Run(start: 2, end: 3, script: .default)])
    }

    func testArabicBlockNeutralsKeepWithTheArabicRun() {
        // U+060C ARABIC COMMA / U+061B / U+061F / U+0640 TATWEEL are
        // Script=Common but Inter has NO glyph for any of them, so §5.2
        // hands them to the same fallback family as the surrounding Arabic.
        // Their BLOCK membership already puts them there.
        XCTAssertEqual(S.script(of: 0x060C), .arabic)
        XCTAssertEqual(S.script(of: 0x061B), .arabic)
        XCTAssertEqual(S.script(of: 0x061F), .arabic)
        XCTAssertEqual(S.script(of: 0x0640), .arabic)
        XCTAssertEqual(S.segment("١٠ـ،"),
                       [S.Run(start: 0, end: 4, script: .arabic)])
    }

    func testSpaceBetweenArmenianWordsSplitsTheRun() {
        // Inter DOES supply U+0020, so the space takes the primary face
        // exactly as Chromium's shaper leaves it there. Three runs, not one.
        XCTAssertEqual(S.segment("ԺԱ ԺԲ"),
                       [S.Run(start: 0, end: 2, script: .armenian),
                        S.Run(start: 2, end: 3, script: .default),
                        S.Run(start: 3, end: 5, script: .armenian)])
    }

    func testBomDoesNotDragFollowingLatinOntoArabic() {
        // U+FEFF sits in the Arabic Presentation Forms-B block but is
        // Script=Common; the range deliberately stops at U+FEFE.
        XCTAssertEqual(S.script(of: 0xFEFF), .default)
        XCTAssertEqual(S.script(of: 0xFEFE), .arabic)
    }

    // MARK: - Segmentation

    func testAdjacentSameScriptMergesIntoOneRun() {
        XCTAssertEqual(S.segment("১২৩"),
                       [S.Run(start: 0, end: 3, script: .bengali)])
    }

    func testMixedLatinArmenianLatinYieldsThreeRunsInOrder() {
        XCTAssertEqual(S.segment("ab ԺԱ c"),
                       [S.Run(start: 0, end: 3, script: .default),
                        S.Run(start: 3, end: 5, script: .armenian),
                        S.Run(start: 5, end: 7, script: .default)])
    }

    func testAdjacentNonLatinScriptsDoNotMerge() {
        XCTAssertEqual(S.segment("ա٠০א០"),
                       [S.Run(start: 0, end: 1, script: .armenian),
                        S.Run(start: 1, end: 2, script: .arabic),
                        S.Run(start: 2, end: 3, script: .bengali),
                        S.Run(start: 3, end: 4, script: .hebrew),
                        S.Run(start: 4, end: 5, script: .khmer)])
    }

    func testSurrogatePairIsNeverSevered() {
        // 😀 is U+1F600 — two UTF-16 units, ONE scalar. A split between them
        // would hand half a character to a different `.font` attribute.
        XCTAssertEqual(S.segment("😀"),
                       [S.Run(start: 0, end: 2, script: .default)])
        XCTAssertEqual(S.segment("😀ա"),
                       [S.Run(start: 0, end: 2, script: .default),
                        S.Run(start: 2, end: 3, script: .armenian)])
    }

    func testEmptyStringIsOneDefaultRun() {
        XCTAssertEqual(S.segment(""),
                       [S.Run(start: 0, end: 0, script: .default)])
    }

    func testRunsTileTheStringExactly() {
        // Structural invariant every call site relies on when it converts a
        // run to an AttributedString range.
        for s in ["ԺԱ. ԺԲ", "1. ١٠", "abc", "", "០១x"] {
            var cursor = 0
            for r in S.segment(s) {
                XCTAssertEqual(r.start, cursor, "run starts where the previous ended in <\(s)>")
                cursor = r.end
            }
            XCTAssertEqual(cursor, s.utf16.count, "runs cover the whole string <\(s)>")
        }
    }

    // MARK: - needsFallback: the identity pre-check

    func testNeedsFallbackFalseForLatinOnly() {
        // The structural half of "the 327 baselines cannot move".
        XCTAssertFalse(S.needsFallback(""))
        XCTAssertFalse(S.needsFallback("Test passes if the two columns match."))
        XCTAssertFalse(S.needsFallback("1. 2. 3."))
        XCTAssertFalse(S.needsFallback("αβγ АБВ"))
    }

    func testNeedsFallbackTrueForEachTargetScript() {
        XCTAssertTrue(S.needsFallback("٠"))
        XCTAssertTrue(S.needsFallback("ա"))
        XCTAssertTrue(S.needsFallback("০"))
        XCTAssertTrue(S.needsFallback("א"))
        XCTAssertTrue(S.needsFallback("០"))
        XCTAssertTrue(S.needsFallback("row 20: Ժ."))
    }

    // MARK: - Registration + the script → face map

    func testAllFiveFacesRegisterFromBundleModule() {
        // The bundling contract: Package.swift declares the runtime's
        // Resources, ScriptFallbackFonts registers them with Core Text at
        // first use. If this fails the faces are not in Bundle.module and
        // every device capture would silently keep the platform cascade.
        XCTAssertEqual(ScriptFallbackFonts.registered.count, 5,
                       "all five bundled faces must register: \(ScriptFallbackFonts.registered)")
        for face in ScriptFallbackFonts.faces {
            XCTAssertTrue(ScriptFallbackFonts.registered.contains(face.postScriptName),
                          "\(face.postScriptName) did not register")
        }
    }

    func testEachTargetScriptMapsToItsOwnFace() {
        let names = [Script.arabic, .armenian, .bengali, .hebrew, .khmer]
            .map { ScriptFallbackFonts.faceName(for: $0) }
        names.forEach { XCTAssertNotNil($0) }
        XCTAssertEqual(Set(names.compactMap { $0 }).count, 5, "no two scripts share a face")
        XCTAssertEqual(ScriptFallbackFonts.faceName(for: .armenian), "NotoSansArmenian-Regular")
        XCTAssertEqual(ScriptFallbackFonts.faceName(for: .arabic), "NotoSansArabic-Regular")
    }

    func testDefaultClaimsNoFace() {
        // nil, not "Inter" — re-stating the primary on a default run would
        // OVERRIDE a document's own `font-family` declaration.
        XCTAssertNil(ScriptFallbackFonts.faceName(for: .default))
        XCTAssertNil(ScriptFallbackFonts.font(for: .default, size: 25))
    }

    #if canImport(UIKit)
    func testTheRegisteredFaceResolvesAsAUIFontAtTheAskedSize() {
        // The measurement lane depends on this: GreedyLineBreaker.measurer
        // writes these UIFonts into the fit-test string, so a nil here would
        // silently measure with the primary face and break where the render
        // does not.
        let f = ScriptFallbackFonts.uiFont(for: .armenian, size: 25)
        XCTAssertNotNil(f)
        XCTAssertEqual(f?.pointSize, 25)
    }
    #endif

    // MARK: - The measured platform verdict

    func testSubstitutionIsDisabledOnIOSByMeasurement() {
        // The lane's central finding, pinned so a later wave has to CHANGE a
        // test to flip it rather than flip it by accident: the iOS simulator
        // shares CoreText's fallback cascade with the Chromium-on-macOS
        // browser-ref, so substituting the bundled Noto faces replaces a
        // matching face with a non-matching one. Measured on the private
        // simulator over the 13 non-Latin-ink documents — net −1 test over
        // the 0.95 gate, 10 of 13 worse. The full table is in the constant's
        // own banner; this pin is the tripwire.
        XCTAssertFalse(ScriptFallbackFonts.substitutionEnabled)
    }

    func testTheMechanismStaysLiveEvenWithSubstitutionOff() {
        // The verdict gates the FACE SWAP only. Segmentation, registration
        // and the face map all stay live, so flipping the constant needs no
        // other change — and so the Compose twin's behaviour is reachable
        // here for a future re-measurement.
        XCTAssertEqual(ScriptFallbackFonts.registered.count, 5)
        XCTAssertNotNil(ScriptFallbackFonts.faceName(for: .armenian))
        XCTAssertNotNil(ScriptFallbackFonts.font(for: .armenian, size: 25))
        XCTAssertTrue(ScriptRunSegmenter.needsFallback("ԺԱ."))
    }

    // MARK: - The WPT gate + the installed attributes

    func testDisabledIsTheIdentity() {
        // Outside WPT capture the function returns its argument unchanged —
        // true regardless of the platform verdict.
        let base = AttributedString("ԺԱ.")
        XCTAssertEqual(ScriptFallbackFonts.apply(to: base, enabled: false, size: 25), base)
    }

    func testEnabledButLatinOnlyIsAlsoTheIdentity() {
        let base = AttributedString("Test passes if the two columns match.")
        XCTAssertEqual(ScriptFallbackFonts.apply(to: base, enabled: true, size: 25), base)
    }

    func testTheStringItselfIsNeverTouched() {
        // Whatever the verdict, this lane changes the FACE and never the
        // text — a changed string would be a wrong marker, not a font.
        XCTAssertEqual(String(ScriptFallbackFonts.annotate("ԺԱ.", enabled: true, size: 25).characters),
                       "ԺԱ.")
        XCTAssertEqual(String(ScriptFallbackFonts.annotate("ա٠০", enabled: true, size: 16).characters),
                       "ա٠০")
    }

    func testTheVerdictDecidesWhetherFontRunsAreInstalled() {
        // ONE assertion covering both worlds, so this pin keeps its meaning
        // if the constant is ever re-measured to true.
        let armenian = ScriptFallbackFonts.annotate("ԺԱ.", enabled: true, size: 25)
        let multi = ScriptFallbackFonts.annotate("ա٠০", enabled: true, size: 16)
        if ScriptFallbackFonts.substitutionEnabled {
            // Exactly the Armenian run carries a face; the FULL STOP does not
            // (NotoSansArmenian has no U+002E — see the segmenter pin).
            XCTAssertEqual(armenian.runs.filter { $0.font != nil }.count, 1)
            XCTAssertEqual(armenian.runs.filter { $0.font == nil }.count, 1)
            XCTAssertEqual(multi.runs.filter { $0.font != nil }.count, 3)
        } else {
            XCTAssertEqual(armenian.runs.filter { $0.font != nil }.count, 0)
            XCTAssertEqual(multi.runs.filter { $0.font != nil }.count, 0)
        }
    }

    func testKernAttributesSurviveTheFontOverlay() {
        // WordSpacingApplier.kernedRun may already have written `.kern`;
        // this lane writes ONLY `.font`, so both must coexist.
        var base = AttributedString("ԺԱ ԺԲ")
        let sp = base.index(base.startIndex, offsetByCharacters: 2)
        let spEnd = base.index(sp, offsetByCharacters: 1)
        base[sp..<spEnd].kern = 12
        let out = ScriptFallbackFonts.apply(to: base, enabled: true, size: 25)
        let kerned = out.runs.filter { $0.kern != nil }
        XCTAssertEqual(kerned.count, 1, "the pre-existing kern run survives")
        XCTAssertNil(kerned.first?.font, "the space keeps the primary face")
        XCTAssertEqual(out.runs.filter { $0.font != nil }.count,
                       ScriptFallbackFonts.substitutionEnabled ? 2 : 0,
                       "both Armenian runs carry the bundled face when enabled")
    }
}
