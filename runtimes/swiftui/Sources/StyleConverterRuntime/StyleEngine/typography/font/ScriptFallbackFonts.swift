//
//  ScriptFallbackFonts.swift
//  StyleEngine/typography/font — wave 34, lane F1.
//
//  The bundled per-script fallback faces, their Core Text registration, and
//  the two functions that install them (the iOS half of the Rule-43 closing
//  move; twin of runtimes/compose/…/typography/font/ScriptFallbackFonts.kt).
//
//  ## The faces
//  Noto Sans <Script> Regular, OFL 1.1, from the notofonts.github.io mirror
//  the Rule-43 banner records, sha256-verified at download time against the
//  banner's recorded digests:
//
//    script    file                           upstream sha256  bundled sha256
//    Arabic    NotoSansArabic-Regular.ttf     bdff3e56…aaef48  2ed68535…87770a
//    Armenian  NotoSansArmenian-Regular.ttf   720df88c…bfce1e  fa4e8909…eb02c4
//    Bengali   NotoSansBengali-Regular.ttf    b55c62ee…7a3193  cb34cf04…c6e796
//    Hebrew    NotoSansHebrew-Regular.ttf     cdefaf8e…5e02ee  02f7a83d…42094d
//    Khmer     NotoSansKhmer-Regular.ttf      e66675f2…b5e605  fb2b756d…eb8302
//
//  ~537 KB total, bundled as SwiftPM resources (Package.swift declares
//  `resources: [.process("Resources")]` on the runtime target, so they ship
//  inside `Bundle.module`). Compose bundles the BYTE-IDENTICAL five files
//  under res/font — verified with `cmp` at bundling time — which is what
//  makes the two natives resolve the SAME outlines, and therefore the same
//  advances, for the same string.
//
//  ## Why the bundled bytes differ from upstream: METRIC-COMPATIBLE FALLBACK
//  The bundled files are the upstream ones with their VERTICAL metrics
//  rewritten to Inter's em-normalised values. `hmtx` and `glyf` are
//  untouched, so every advance width and every outline is bit-identical to
//  upstream; only the line-box metrics move. Reproducible in one fontTools
//  pass per file:
//
//      f['hhea'].ascender, f['hhea'].descender, f['hhea'].lineGap = 969, -241, 0
//      os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap    = 969, -241, 0
//      os2.usWinAscent, os2.usWinDescent                          = 969, 241
//      os2.fsSelection |= 128                      # USE_TYPO_METRICS
//
//  (969/-241 = Inter-Regular's 1984/-494 at unitsPerEm 2048, scaled to these
//  faces' unitsPerEm 1000.)
//
//  MEASURED, and this is why it is not optional. Upstream Noto Sans Arabic
//  declares hhea ascender/descender 1374/-738 — a 2.112 em line against
//  Inter's 1.210 — so a per-run face swap moved the baseline as well as the
//  advances. Composed WPT capture of css-counter-styles/arabic-indic/
//  css3-counter-styles-102 on Android: first ink band y=[101,115) pre-lane →
//  y=[105,122) with the raw upstream face, against the browser-ref's
//  y=[94,112), while the HORIZONTAL half converged in the same run (row 9:
//  ref 85 px wide, pre-lane 73, raw-upstream 85 — exact). The raw face
//  bought the right advances and paid for them in baseline placement
//  (SSIM 0.7791 → 0.7546). Normalising keeps the advances, drops the shift.
//
//  ## Registration, not Info.plist
//  The runtime is a LIBRARY: it has no Info.plist of its own and cannot ask
//  a host app to list `UIAppFonts`. So the faces register themselves with
//  Core Text on first use via `CTFontManagerRegisterFontsForURL(.process)` —
//  the same mechanism the harness app already uses for Inter
//  (apps/ios-harness/…/StyleConverterTestApp.swift `registerBundledFonts`),
//  moved inside the runtime so a consumer gets the fallback faces without
//  any integration step. `registered` is a `static let`, so Swift's
//  lazy-global initialisation runs it exactly once, thread-safely, at the
//  first `font(for:size:)` call — that IS the engine-init hook.
//
//  ## Regular only, deliberately
//  One face per script, at the regular weight. Every Rule-43 corpus document
//  paints its non-Latin text at the default weight, and shipping four
//  weights per script would have quadrupled the payload for coverage nothing
//  exercises. A bold non-Latin run therefore renders at regular weight
//  (CoreText will not synthesise emboldening for a `.custom` family that has
//  no bold member) — the ONE known gap in this lane, stated rather than
//  hidden. Compose's twin takes Compose's synthetic emboldening there, so
//  the two natives can disagree on bold non-Latin text; no corpus document
//  exercises it today.
//
//  ## Scope gate
//  Nothing here runs outside WPT capture: every call site passes the ambient
//  `wptCaptureMode`, which is `false` on the whole product/baseline path
//  (ScreenshotCaptureView sets it only for the TITAN inbox/composed capture),
//  so the committed 327-pair dark-stage baselines cannot move. The second,
//  stronger guarantee is structural: `apply(to:enabled:size:)` returns its
//  input unchanged whenever the string has no target-script scalar.
//

import SwiftUI
import CoreText
#if canImport(UIKit)
import UIKit
#endif

public enum ScriptFallbackFonts {

    /// Is bundled-face SUBSTITUTION a measured improvement on this platform?
    ///
    /// **FALSE on iOS, and this is the lane's central measurement.** The
    /// simulator resolves non-Latin text through CoreText's system cascade —
    /// the SAME cascade Chromium-on-macOS uses to rasterise the browser-ref.
    /// That accidental parity is better than anything we can bundle, so
    /// substituting Noto here replaces a MATCHING face with a non-matching
    /// one. Measured — composed WPT capture, private simulator
    /// `wave34-laneF1` (iPhone 17 Pro, iOS 26.2), the 13 documents that carry
    /// non-Latin ink, scored with `diffComposedVsRef` against the frozen
    /// `white-black-ink-font-lh-imgpad-htmlpins` refs (wave33-final on the
    /// left, this lane's faces ENABLED on the right):
    ///
    ///   arabic-indic 101  0.9788 → 0.9777  (−0.001)
    ///   arabic-indic 102  0.8783 → 0.8646  (−0.014)
    ///   arabic-indic 103  0.9921 → 0.9939  (+0.002)
    ///   armenian     006  0.9544 → 0.9171  (−0.037, LOSES a 0.95 pass)
    ///   armenian     007  0.8178 → 0.7256  (−0.092)
    ///   armenian     008  0.9378 → 0.9114  (−0.026)
    ///   armenian     009  0.9863 → 0.9802  (−0.006)
    ///   bengali      116  0.9624 → 0.9632  (+0.001)
    ///   bengali      117  0.8400 → 0.8374  (−0.003)
    ///   bengali      118  0.9902 → 0.9933  (+0.003)
    ///   cambodian    158  0.9631 → 0.9616  (−0.002)
    ///   cambodian    159  0.8651 → 0.7537  (−0.111)
    ///   css-text bidi-lines-001  0.9926 → 0.9662  (−0.026)
    ///
    /// Net −1 test over the 0.95 gate (8/13 → 7/13), 10 of 13 worse, sum of
    /// deltas −0.28. The two failure modes were separated by measuring row
    /// extents rather than trusting the SSIM:
    ///  * ARABIC — pure glyph SHAPE. bidi-lines-001's ink bands are
    ///    byte-for-byte the same rectangles before and after (y=[108,354),
    ///    x=[16,361] in both), so nothing moved; only the letterforms
    ///    changed, away from the ref's. That is the Rule-43 banner's item
    ///    (d) confirmed from the native side.
    ///  * ARMENIAN — vertical. Row pitch went 31/32 px (matching the ref
    ///    exactly, tops 94/125/157/188/…) to a uniform 35 px, while the row
    ///    WIDTHS stayed within 1 px of the pre-lane numbers. The advances
    ///    were already right; the per-run face only cost line rhythm.
    ///
    /// TRUE on the Compose twin, where the emulator's own faces are NOT the
    /// ref's and the same substitution gains a pass. This constant is the ONE
    /// line where the two twins deliberately differ, and it is a measurement,
    /// not a preference. Everything else here — the segmentation, the
    /// registration, the attributed-run application, the measurement lane —
    /// is live on both, so flipping this to `true` needs no other change.
    public static let substitutionEnabled = false

    /// PostScript name → resource basename, in the order the segmenter's
    /// `TextScript` cases are declared. The PostScript name is what
    /// `Font.custom(_:size:)` and `UIFont(name:size:)` resolve against after
    /// Core Text registration (verified with fontTools: name ID 6 of each
    /// file is exactly the string on the left).
    static let faces: [(script: ScriptRunSegmenter.TextScript, postScriptName: String)] = [
        (.arabic, "NotoSansArabic-Regular"),
        (.armenian, "NotoSansArmenian-Regular"),
        (.bengali, "NotoSansBengali-Regular"),
        (.hebrew, "NotoSansHebrew-Regular"),
        (.khmer, "NotoSansKhmer-Regular"),
    ]

    /// Register every bundled face with Core Text, once per process.
    ///
    /// `.process` scope (not `.persistent`) keeps the registration private
    /// to this app — exactly what the harness's Inter registration does, and
    /// the only scope that is safe for a library to claim on a user device.
    /// An already-registered error (kCTFontManagerErrorAlreadyRegistered =
    /// 105) is benign and swallowed: the harness app may itself have
    /// registered the same URL.
    ///
    /// Returns the set of scripts whose face actually resolved, so a missing
    /// resource degrades to "no span for that script" (i.e. the pre-lane
    /// platform cascade) instead of a crash or an invisible `.notdef`.
    public static let registered: Set<String> = {
        var ok = Set<String>()
        for face in faces {
            // Bundle.module is the SwiftPM resource bundle; `.process`
            // flattens Resources/Fonts/*.ttf to the bundle root, so the
            // lookup is by basename with no subdirectory.
            guard let url = Bundle.module.url(forResource: face.postScriptName,
                                              withExtension: "ttf") else {
                // No silent fallthrough (repo rule): say which face is
                // missing, once, and keep rendering without it.
                PropertyTracker.logOnce(
                    key: "script-fallback-missing-\(face.postScriptName)",
                    message: "per-script fallback face not in Bundle.module: "
                        + "\(face.postScriptName).ttf — that script keeps the "
                        + "platform cascade")
                continue
            }
            var error: Unmanaged<CFError>?
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
            ok.insert(face.postScriptName)
        }
        return ok
    }()

    /// The PostScript name that supplies `script`, or nil for `.default` —
    /// nil meaning "no attribute, keep whatever face the caller's font
    /// already resolved", which is how a default run stays byte-identical
    /// instead of being re-stated as Inter (re-stating it would OVERRIDE a
    /// document's own `font-family` declaration, a regression this lane has
    /// no business causing).
    public static func faceName(for script: ScriptRunSegmenter.TextScript) -> String? {
        guard script != .default else { return nil }
        guard let face = faces.first(where: { $0.script == script }) else { return nil }
        // Only claim a face Core Text actually took (see `registered`).
        return registered.contains(face.postScriptName) ? face.postScriptName : nil
    }

    #if canImport(UIKit)
    /// The UIKit face for `script` at `size`, or nil to keep the caller's
    /// own font. Used by BOTH the measurement lane (GreedyLineBreaker's
    /// measurer) and — via `Font(_: CTFont)` — nothing else: the render
    /// lane goes through `font(for:size:)` so SwiftUI owns the Text.
    public static func uiFont(for script: ScriptRunSegmenter.TextScript,
                              size: CGFloat) -> UIFont? {
        guard let name = faceName(for: script) else { return nil }
        return UIFont(name: name, size: size)
    }
    #endif

    /// The SwiftUI face for `script` at `size`, or nil to keep the caller's
    /// own font.
    public static func font(for script: ScriptRunSegmenter.TextScript,
                            size: CGFloat) -> Font? {
        guard let name = faceName(for: script) else { return nil }
        return .custom(name, size: size)
    }

    /// Install a per-script `.font` attribute over every non-default run of
    /// `base` — the layout-level stand-in for per-character fallback.
    ///
    /// Additive by construction: the attribute this writes is `.font` alone,
    /// so the `.kern` word-spacing / letter-spacing attributes
    /// `WordSpacingApplier.kernedRun` may already have written survive
    /// untouched.
    ///
    /// - Parameters:
    ///   - base: the string as the caller already built it.
    ///   - enabled: the ambient WPT-capture flag. False ⇒ identity, so the
    ///     product renderer and the 327 baselines never see this code.
    ///   - size: the run's resolved font size in points — the fallback face
    ///     must be built at the SAME size as the primary or the substituted
    ///     glyphs would paint at the system default.
    /// - Returns: `base` itself when there is nothing to do (disabled, or no
    ///   target-script scalar) — value-identical, so the "cannot move" claim
    ///   is structural rather than a comparison.
    public static func apply(to base: AttributedString,
                             enabled: Bool,
                             size: CGFloat) -> AttributedString {
        // Both gates, in cheapest-first order: the platform's measured
        // verdict, then the caller's ambient WPT-capture flag.
        guard substitutionEnabled, enabled else { return base }
        let plain = String(base.characters)
        guard ScriptRunSegmenter.needsFallback(plain) else { return base }
        var out = base
        for run in ScriptRunSegmenter.segment(plain) {
            guard let f = font(for: run.script, size: size) else { continue }
            // UTF-16 offsets → AttributedString indices. The segmenter emits
            // UTF-16 offsets precisely so this conversion is exact for
            // non-BMP scalars too.
            guard let range = utf16Range(in: out, plain: plain,
                                         from: run.start, to: run.end) else { continue }
            out[range].font = f
        }
        return out
    }

    /// String convenience for the `::marker` call sites, which hand `Text` a
    /// raw `String`. Same gate, same identity guarantee — a marker with no
    /// target-script glyph comes back as a plain `AttributedString` carrying
    /// no attributes at all, which `Text` lays out identically to the
    /// `String` overload.
    public static func annotate(_ text: String,
                               enabled: Bool,
                               size: CGFloat) -> AttributedString {
        apply(to: AttributedString(text), enabled: enabled, size: size)
    }

    /// UTF-16 offset pair → `AttributedString` range, or nil when the offsets
    /// fall outside the string (defensive: a caller that hands us offsets from
    /// a DIFFERENT string must degrade to "no span", never trap).
    private static func utf16Range(in s: AttributedString,
                                   plain: String,
                                   from start: Int,
                                   to end: Int) -> Range<AttributedString.Index>? {
        // The bridge goes through the PLAIN string, not `AttributedString`'s
        // own `.utf16` view: that view is iOS 26+, and this target's floor is
        // iOS 16 (Package.swift `platforms: [.iOS(.v16)]`). `Range(_:in:)`
        // does the UTF-16-offset → String.Index conversion, and
        // `AttributedString.Index(_:within:)` (iOS 15+) lifts it — exact for
        // non-BMP scalars, and nil rather than a trap if the offsets ever
        // came from a different string.
        guard start >= 0, end >= start,
              let r = Range(NSRange(location: start, length: end - start), in: plain),
              let lower = AttributedString.Index(r.lowerBound, within: s),
              let upper = AttributedString.Index(r.upperBound, within: s)
        else { return nil }
        return lower..<upper
    }
}
