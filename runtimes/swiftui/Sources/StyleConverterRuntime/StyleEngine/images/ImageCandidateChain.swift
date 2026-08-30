//
//  ImageCandidateChain.swift
//  StyleEngine/images — wave-49 lane A3
//
//  The css-images-4 §2.5 / css-images-3 `image()` CANDIDATE WALK, shared by
//  every image-list value that carries more than one source. Line-for-line
//  mirror of Kotlin images/ImageCandidateChain.kt.
//
//  ## Why this exists
//
//  `image("a.svg", "b.png", "c.gif")` is not "paint a.svg". §2.1 says the UA
//  tries each <image-src> IN AUTHOR ORDER and paints THE FIRST ONE THAT CAN BE
//  DISPLAYED; only when every candidate fails does the optional <color> paint.
//  Until this file existed both natives read srcs[0] and threw candidates 2..n
//  away at extract time — so a value whose first candidate is deliberately
//  missing (WPT css-image-fallbacks-and-annotations003/004 open with
//  `1x1-green.svg`, a path that does NOT exist beside those tests) could never
//  paint anything but the `background-color: red` the tests forbid, no matter
//  what the host delivered. MEASURED at the wave-48 gate: 003/004 iOS 0.9990 /
//  Android 0.9981 with `colorFailed` — pixel-perfect placement, 17% of the
//  canvas in pure red where the reference is `green` (0,128,0).
//
//  ## Why it is a separate, platform-free file
//
//  The walk is the ONE piece of §2.1 both natives must agree on exactly, and it
//  has nothing to do with SwiftUI: it is a fold over strings with a caller-
//  supplied decode probe. Keeping it pure lets the XCTest suite pin it with a
//  fake decoder, and keeps the Kotlin twin a mirror rather than a
//  re-derivation that can drift.
//
//  ## Where the loadability decision moved to, and why that is not a reversal
//
//  The wave-48 seam note on both natives said the fallback COLOUR wins whenever
//  it is present "because whether a url will load is unknowable at their
//  extraction time". That was true *of the extractor*. It is not true of the
//  PAINT path, which is the only place that can actually attempt a decode — so
//  the decision moves here, one layer down, and the §2.1 ordering it implements
//  is the same ordering web gets for free from the browser's own loader (the
//  web runtime lowers image() into a url stack — BackgroundImageExtractor.ts
//  `imageNotationCss`). For every corpus value that carries a colour the two
//  answers agree, because those colours pair with sources that cannot load;
//  what changes is image(<loadable-src>, <color>), where the colour previously
//  mis-painted OVER a usable source and now correctly loses to it.
//

import Foundation

enum ImageCandidateChain {

    /// The outcome of one walk.
    ///
    /// `declined` is not decoration: the house rule forbids a silent
    /// fallthrough, and a candidate that was tried and refused is exactly the
    /// thing an investigator needs in the capture log to tell "the host never
    /// delivered this asset" apart from "this platform cannot decode that
    /// container". Callers log it once per layer.
    struct Outcome<T> {
        /// The winning candidate's source string, or nil when every candidate
        /// was declined.
        let src: String?
        /// The winning candidate's decoded payload, or nil with `src`.
        let image: T?
        /// Every candidate tried and refused, in author order.
        let declined: [String]
    }

    /// Walk `srcs` in author order, returning the first candidate `decode`
    /// accepts.
    ///
    /// `decode` is the platform's REAL paint-time decoder, never a syntactic
    /// guess: on iOS that is `BackgroundURLImageResolver.image(for:)` (which
    /// caches positively AND negatively, so probing a candidate then painting
    /// it costs one decode), on Compose `SyncImageDecode.decodeDataUri`.
    /// Passing the genuine decoder is what makes this §2.1's "can be
    /// displayed" rather than a heuristic fitted to one corpus.
    ///
    /// A blank/whitespace-only candidate is skipped without being counted as a
    /// decline — it is not a source the author wrote, it is an empty wire slot,
    /// and reporting it would put noise in the log the honest declines live in.
    static func firstPaintable<T>(_ srcs: [String],
                                  decode: (String) -> T?) -> Outcome<T> {
        // Accumulate refusals so the caller can say what it tried; a plain
        // `first(where:)` would lose exactly the information the
        // no-silent-fallthrough rule exists to preserve.
        var declined: [String] = []
        for raw in srcs {
            let src = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            // Empty wire slot — not an author candidate; see the doc note.
            if src.isEmpty { continue }
            // §2.1 "the first one that can be displayed": stop at the winner,
            // never keep walking (a later candidate must not override it).
            if let decoded = decode(src) {
                return Outcome(src: src, image: decoded, declined: declined)
            }
            declined.append(src)
        }
        // Every candidate refused. The caller now paints the optional <color>
        // fallback, or nothing at all — both are §2.1 outcomes and both are
        // the caller's decision, not this fold's.
        return Outcome(src: nil, image: nil, declined: declined)
    }
}
