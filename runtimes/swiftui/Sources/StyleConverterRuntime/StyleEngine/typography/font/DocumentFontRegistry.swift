//
//  DocumentFontRegistry.swift
//  StyleConverterRuntime — wave-35 lane B2
//
//  Runtime registration of the document's `@font-face` declarations
//  (schema/spec/01-envelope.md §5).
//
//  ## Why this exists, and why it is not a property triplet
//
//  Everything else in this folder is a per-PROPERTY triplet: one IR property
//  in, one SwiftUI modifier out. A font FACE is neither — it is a
//  document-level RESOURCE that must be registered with CoreText BEFORE any
//  Text measures, and registering it means knowing where the HOST put the
//  file. So the registry is a document-scoped object the host fills in (the
//  harness's inbox path calls `register` right after the IRDocument decode)
//  and the per-property path merely CONSULTS it. Exactly the split the web
//  side uses between apps/web-harness/src/sdui/useFontFaces.ts (mount) and the
//  engine (read), and the Compose side uses between its own
//  DocumentFontRegistry and CssFontFamilyResolver.
//
//  ## What it closes
//
//  Until wave 35 both natives DECODED `fontFaces` and ignored it (IRModels'
//  own comment said so): a test declaring
//  `@font-face { font-family: test; src: url(X.woff) }` plus
//  `body { font: 36px test }` shaped with the bundled Inter while the browser
//  ref painted the author's outlines. css-text/boundary-shaping-001…008 are
//  the measured case — they assert on "fi"/"ffi" LIGATURES only the declared
//  LinLibertine face carries.
//
//  ## The CoreText hop, and the NAME problem it creates
//
//  `CTFontManagerRegisterFontsForURL` makes the file's own faces available to
//  the process — under the FILE's family name ("Linux Libertine"), not the
//  CSS family name ("test"). SwiftUI's `.custom(_:size:)` and `UIFont(name:)`
//  both take the platform name, so registration alone changes nothing: the
//  CSS name still resolves to nothing and falls back. That is why this file
//  keeps a MAPPING — CSS family (case-insensitively) → the PostScript name
//  CoreText reported for the file it just registered — and why the mapping is
//  read off the registered descriptors rather than guessed from the filename.
//
//  ## Degradation is loud, never silent
//
//  A face whose file is missing, unreadable or unregisterable is DECLINED: no
//  mapping is added, `resolvedName` answers nil, and the existing fallback
//  walk runs exactly as it did in wave 34. Every decline is logged with the
//  family AND the path, because the failure this guards against — plausible
//  text in the WRONG face — is invisible in a screenshot.
//

import Foundation
import CoreText
#if canImport(UIKit)
import UIKit
#endif

/// Process-wide registry of the current document's `@font-face` faces.
public final class DocumentFontRegistry {

    /// The one instance the harness fills and the font path reads. A
    /// singleton because CoreText registration is itself process-scoped:
    /// a second registry could not undo the first's registrations, so
    /// pretending they are independent would be a lie about the platform.
    public static let shared = DocumentFontRegistry()

    /// Outcome of the most recent `register` call — surfaced so the harness
    /// can log one line per document and a test can assert delivery without
    /// reaching into CoreText.
    public struct Report: Equatable {
        public let declared: Int
        public let registered: Int
        public let declined: [String]
        public init(declared: Int = 0, registered: Int = 0, declined: [String] = []) {
            self.declared = declared
            self.registered = registered
            self.declined = declined
        }
    }

    /// CSS family (lowercased, unquoted) → the platform font name to hand to
    /// `.custom(_:size:)`.
    private var mapping: [String: String] = [:]

    /// URLs this registry handed to CoreText, so `clear()` can hand them back.
    /// Without this a face from document N-1 stays registered for the whole
    /// process and shadows document N's same-named family — "looks like text,
    /// shaped from the wrong file", the exact failure this channel ends.
    private var registeredURLs: [URL] = []

    public private(set) var lastReport = Report()

    /// wave-47 lane Z4 — registration epoch: bumped by every [clear] (and so
    /// by every [register], which clears first). ChUnitMetrics folds it into
    /// its memoized-advance key so a '0' advance cached for document N's face
    /// can never answer for document N+1 — the registry replaces its mapping
    /// wholesale per document, and two documents may bind one PostScript name
    /// to different files.
    public private(set) var epoch: Int = 0

    private init() {}

    /// css-fonts-4 §4.2 family identity: ASCII case-insensitive, quotes
    /// stripped. Mirrors the Compose registry's `familyKey` and the web
    /// harness's family handling so a name that matches on one surface
    /// matches on all three.
    private static func familyKey(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
            .lowercased()
    }

    /// Register a document's faces, REPLACING any previous document's.
    ///
    /// - Parameters:
    ///   - faces: decoded `IRDocument.fontFaces` (nil/empty ⇒ clear only).
    ///   - baseDirectory: the directory the host copied the font FILES under.
    ///     The entry's `src` is appended verbatim (the feeder preserves the
    ///     corpus-relative path exactly so there is no escaping rule to drift
    ///     between the four codebases). nil ⇒ every face is declined.
    /// - Returns: the number of CSS families that now resolve.
    @discardableResult
    public func register(_ faces: [IRFontFace]?, baseDirectory: URL?) -> Int {
        clear()
        guard let faces, !faces.isEmpty else { return 0 }
        var declined: [String] = []
        for face in faces {
            guard let base = baseDirectory else {
                log("declined @font-face '\(face.family)': no base directory for \(face.src)")
                declined.append(face.family)
                continue
            }
            // `appendingPathComponent` on a multi-segment relative path keeps
            // the segments — which is what the verbatim contract needs.
            let url = base.appendingPathComponent(face.src)
            guard FileManager.default.fileExists(atPath: url.path) else {
                log("declined @font-face '\(face.family)': no file at \(url.path)")
                declined.append(face.family)
                continue
            }
            // Read the file's own descriptors BEFORE registering: a file
            // CoreText cannot describe is one it cannot register either, and
            // failing here costs nothing to undo.
            guard let platformName = Self.platformFontName(of: url) else {
                log("declined @font-face '\(face.family)': CoreText could not read a font name from \(url.lastPathComponent)")
                declined.append(face.family)
                continue
            }
            var error: Unmanaged<CFError>?
            // `.process` scope: visible to this app only, and — crucially —
            // UNREGISTERABLE later, which `clear()` depends on.
            let ok = CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
            if !ok {
                // Already-registered is the one benign failure: the same file
                // fed twice in one process (two documents, one face) is fine
                // and the mapping below is still correct.
                let code = (error?.takeRetainedValue()).map { CFErrorGetCode($0) }
                if code != CTFontManagerError.alreadyRegistered.rawValue {
                    log("declined @font-face '\(face.family)': registration failed for \(url.lastPathComponent) (code \(code.map(String.init) ?? "?"))")
                    declined.append(face.family)
                    continue
                }
            } else {
                registeredURLs.append(url)
            }
            // Last declaration of a family wins — css-fonts-4 §4.1's rule for
            // two faces occupying the same slot, and the same rule the section
            // pipeline's combined-fixture merge enforces upstream.
            mapping[Self.familyKey(face.family)] = platformName
        }
        lastReport = Report(declared: faces.count, registered: mapping.count, declined: declined)
        if !mapping.isEmpty {
            log("registered \(mapping.count) @font-face famil\(mapping.count == 1 ? "y" : "ies"): " +
                mapping.map { "\($0.key)→\($0.value)" }.sorted().joined(separator: ", "))
        }
        return mapping.count
    }

    /// The platform font name for a CSS family, or nil when this document
    /// declared no such face.
    ///
    /// nil is the contract, not a fallback: the caller is walking a
    /// css-fonts-4 §5.2 fallback list and must stay free to continue to the
    /// next name. Answering a default here would stop the walk at the first
    /// name and re-introduce the wrong-face bug this file exists to end.
    public func resolvedName(for cssFamily: String) -> String? {
        mapping[Self.familyKey(cssFamily)]
    }

    /// True when this document registered no face — the cheap guard the font
    /// path uses to stay byte-for-byte on its wave-34 behaviour for the
    /// (overwhelmingly common) face-free document.
    public var isEmpty: Bool { mapping.isEmpty }

    /// Unregister everything and forget the mapping.
    public func clear() {
        for url in registeredURLs {
            var error: Unmanaged<CFError>?
            // Best-effort: a face still referenced by a live layout can refuse
            // to unregister. The mapping is dropped either way, so the stale
            // face can no longer be NAMED even if it lingers in CoreText.
            _ = CTFontManagerUnregisterFontsForURL(url as CFURL, .process, &error)
        }
        registeredURLs.removeAll()
        mapping.removeAll()
        lastReport = Report()
        // The document boundary: everything memoized against the old faces
        // (ChUnitMetrics' advance cache) expires with them — see `epoch`.
        epoch += 1
    }

    /// The name `.custom(_:size:)` / `UIFont(name:)` will accept for the first
    /// face in a font file, read from CoreText's own descriptors.
    ///
    /// PostScript name, not family name: `UIFont(name:)` accepts either, but
    /// the PostScript name is unique per FACE, so a file carrying several
    /// faces cannot resolve to an ambiguous family string.
    static func platformFontName(of url: URL) -> String? {
        guard let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
              let first = descriptors.first else { return nil }
        let name = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String
        if let name, !name.isEmpty { return name }
        // Fall back to the family attribute: a few older faces omit the
        // PostScript name. Still a real name from the file, never a guess.
        let family = CTFontDescriptorCopyAttribute(first, kCTFontFamilyNameAttribute) as? String
        return (family?.isEmpty == false) ? family : nil
    }

    /// Single logging seam. `print` matches the harness's other TITAN
    /// diagnostics (they are scraped out of the simulator log), and keeping it
    /// in one place means a future switch to os_log touches one line.
    private func log(_ message: String) {
        print("[StyleConverter] DocumentFontRegistry: \(message)")
    }
}
