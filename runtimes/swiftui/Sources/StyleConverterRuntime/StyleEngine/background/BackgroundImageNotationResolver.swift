//
//  BackgroundImageNotationResolver.swift
//  StyleEngine/background — wave-49 lane A3
//
//  Collapses a `.imageNotation` layer to the layer that actually paints
//  (css-images-4 §2.5). The WALK itself lives in the platform-free
//  StyleEngine/images/ImageCandidateChain.swift (Kotlin twin:
//  images/ImageCandidateChain.kt); this file supplies the PROBE and turns the
//  outcome back into an ordinary `BackgroundImageLayer`, so every downstream
//  path — the raster tile view, the gradient renderer, cross-fade arguments —
//  keeps working with no image()-specific branch at all.
//
//  Split into its own file rather than added to BackgroundImageApplier because
//  BOTH renderers need it: BackgroundImageApplier.render (the live background
//  stack) and GradientApplier.render (the stack-index / cross-fade-argument
//  path). A single resolver is what keeps the two from drifting apart on the
//  same wire.
//

import Foundation

extension BackgroundImageLayer {

    /// The layer an `image()` value actually paints, per css-images-4 §2.5.
    ///
    /// The probe is `BackgroundURLImageResolver.image(for:)` — this runtime's
    /// REAL background raster decoder, and therefore also its real capability
    /// boundary: data: payloads, file:// paths, absolute paths and bundle
    /// resources decode; remote schemes are the documented deterministic no-op
    /// (see BackgroundURLImage.swift). So "can be displayed" here means exactly
    /// what this runtime can display, not a guess fitted to one corpus. The
    /// resolver caches positively AND negatively, so probing a candidate and
    /// then painting it costs one decode.
    ///
    /// Outcomes — all three §2.1 branches, none silent:
    ///   * a candidate decodes   → `.url` of that candidate;
    ///   * all declined + colour → `.color`, the same layer the pre-wave-49
    ///     extractor produced for `image(<src>, <color>)`, so those captures
    ///     stay byte-identical;
    ///   * all declined, no colour → `.none`, the browser's failed-load visual
    ///     (nothing painted; the background-color shows through).
    static func resolveImageNotation(srcs: [String],
                                     fallback: ColorValue?) -> BackgroundImageLayer {
        let outcome = ImageCandidateChain.firstPaintable(srcs) { src in
            BackgroundURLImageResolver.image(for: src)
        }
        // Loud, once per distinct candidate list: name what was refused and
        // what replaced it, so an investigator can tell "the host never
        // delivered this asset" from "this platform cannot decode it".
        if !outcome.declined.isEmpty {
            let next: String
            if let winner = outcome.src {
                next = "the next candidate '\(winner)'"
            } else if fallback != nil {
                next = "the fallback <color>"
            } else {
                next = "nothing (no fallback <color> on the wire)"
            }
            PropertyTracker.logOnce(
                key: "bg-image-notation:\(outcome.declined.joined(separator: ","))",
                message: "background-image: image() declined \(outcome.declined.count) "
                    + "candidate(s) [\(outcome.declined.joined(separator: ", "))] — "
                    + "not decodable as a file, bundle resource or data: payload; "
                    + "css-images-4 §2.5 falls through to \(next)")
        }
        // §2.1, in order: winner, then the optional colour, then nothing.
        if let winner = outcome.src { return .url(winner) }
        if let colour = fallback { return .color(colour) }
        return BackgroundImageLayer.none
    }
}
