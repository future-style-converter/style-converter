//
//  DocumentImageRegistry.swift
//  StyleEngine/images — wave-39 lane A2
//
//  Runtime resolution of a document's REPLACED-ELEMENT image sources
//  (`meta.attrs.src`; schema/spec/04-metadata-fields.md).
//
//  ## Why this exists, and why it is not a property triplet
//
//  Everything else in this folder is a per-PROPERTY triplet: one IR property
//  in, one SwiftUI modifier out. A replaced element's CONTENT is neither — it
//  is a document-level RESOURCE reached through a path only the HOST can turn
//  into bytes. So, exactly like its @font-face twin (DocumentFontRegistry in
//  StyleEngine/typography/font/), the registry is a document-scoped object the
//  host fills in (the harness's inbox path calls `configure` right after the
//  IRDocument decode) and the paint path merely CONSULTS it. Byte-parallel
//  twin of Kotlin images/DocumentImageRegistry.kt.
//
//  ## What it closes
//
//  wave-36 lane M1 put the source on the wire and taught the WEB harness to
//  resolve it through its /wpt-image/ route. Both natives got the path and
//  nothing to open — IRAttrs.src's own comment said so — so every replaced
//  element painted an EMPTY box where the browser ref paints an image.
//  MEASURED (wave38-final, css-writing-modes img-intrinsic-size-contribution-
//  001/002): web 0.9623 PASS against iOS 0.8654 fail, the ref's 200×100 blue
//  raster simply absent from the native capture.
//
//  ## The file channel
//
//  The wire carries a corpus-relative PATH, never a payload. The feeder
//  (tools/titan/feed-ios.mjs) copies each referenced file into the app's own
//  sandbox at `<container>/Documents/images/<src>`, preserving the relative
//  path VERBATIM — so resolution here is a plain URL append with no name
//  mangling and no escaping rule that could drift from the host's. A `data:`
//  source carries its own bytes and needs no hop at all.
//
//  ## Degradation is loud, never silent
//
//  A source that is absent, unreadable, or in a container ImageIO cannot parse
//  is DECLINED: `resolve` answers nil, the paint path renders the empty box it
//  rendered before this channel existed, and the decline is logged AND counted
//  in `lastReport`. That matters most for SVG — 19 of the 28 depth-48
//  replaced-source tests are SVG, and ImageIO ships no SVG decoder, so the
//  honest gap is a stamped decline and never a plausible-looking wrong raster.
//

import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Process-scoped registry of the current document's replaced-element images.
public final class DocumentImageRegistry {

    /// The one instance the harness configures and the paint path reads. A
    /// singleton for DocumentFontRegistry's reason inverted: the decode CACHE
    /// is what must be document-scoped, and pretending two registries were
    /// independent would let a second one serve rasters the first had already
    /// been told to forget.
    public static let shared = DocumentImageRegistry()

    /// One decoded replaced-element image: the raster plus the INTRINSIC size
    /// CSS 2.1 §10.3.2 / css-images-3 §5.2 size the box from.
    public struct DecodedImage {
        #if canImport(UIKit)
        public let image: UIImage
        #endif
        /// Intrinsic dimensions in CSS px. `UIImage.size` is in POINTS, so the
        /// pixel size is `size × scale` — the same conversion
        /// BackgroundURLImage's `intrinsicPixelSize` makes, and load-bearing
        /// for the same reason: a file decoded at a non-1.0 scale would report
        /// a size the CSS box model never heard of. CSS px == pt at the
        /// capture scale, which is the identity every geometry constant in
        /// this runtime assumes.
        public let intrinsicWidthPx: Double
        public let intrinsicHeightPx: Double

        /// The css-images-3 §5.2 intrinsic ASPECT RATIO (width ÷ height), or
        /// nil when either axis is degenerate — the caller then falls back to
        /// the intrinsic size rather than dividing by zero.
        public var aspectRatio: Double? {
            guard intrinsicWidthPx > 0, intrinsicHeightPx > 0 else { return nil }
            return intrinsicWidthPx / intrinsicHeightPx
        }
    }

    /// Container formats ImageIO (which backs every `UIImage` file decode) can
    /// actually parse — and, critically, NOT the ones it cannot.
    ///
    /// SVG is the load-bearing absence. iOS has supported SVG since 13 ONLY
    /// through asset catalogs (`UIImage(named:)` on a compiled catalog);
    /// there is no public API that turns an arbitrary SVG FILE into a
    /// `UIImage`, and `CGImageSource` does not advertise the SVG UTI. So an
    /// SVG reaching the decode returns nil — a decline this table turns into a
    /// NAMED one instead of a mystery.
    ///
    /// Deliberately asymmetric with the FEEDER's table, which admits SVG: the
    /// host channel's job is to deliver what the wire named, and the decision
    /// about what this PLATFORM can render belongs here, where it can be
    /// stamped. Exactly the layering DocumentFontRegistry uses for WOFF —
    /// and note the table is WIDER than Compose's twin (ico/tiff are ImageIO
    /// formats BitmapFactory has no decoder for), which is the honest shape:
    /// mirroring Android's list would decline a file iOS can genuinely render.
    private static let decodableExtensions: Set<String> = [
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif",
        "ico", "tif", "tiff",
    ]

    /// Outcome of the current document's resolutions — surfaced so the harness
    /// can log one line per document and a test can assert delivery without
    /// reaching into ImageIO.
    public struct Report: Equatable {
        public let requested: Int
        public let decoded: Int
        public let declined: [String]
        public init(requested: Int = 0, decoded: Int = 0, declined: [String] = []) {
            self.requested = requested
            self.decoded = decoded
            self.declined = declined
        }
    }

    /// The directory the host copied the image FILES under; the wire's `src` is
    /// appended verbatim. nil ⇒ nothing on disk can resolve (a `data:` source
    /// still can — it carries its own bytes).
    private var baseDirectory: URL?

    /// Decode cache for the CURRENT document, keyed by the wire `src`.
    ///
    /// Not an optimisation so much as a correctness convenience: one support
    /// image is routinely painted by many boxes (css-grid's abspos family
    /// paints colors-8x16.png from 41 components), and re-decoding per box
    /// would multiply capture time by the component count. A cached nil VALUE
    /// is a cached DECLINE — it stops the log repeating once per box.
    private var cache: [String: DecodedImage?] = [:]

    public private(set) var lastReport = Report()

    private init() {}

    /// Point the registry at this document's image sandbox, clearing the
    /// previous document's cache and report.
    ///
    /// REPLACE, not merge — the inbox harness renders many documents in one
    /// process, and a raster cached under a path document N also uses would
    /// paint document N-1's picture even though N's own delivery failed. The
    /// feeders wipe the on-device images directory per run for the same reason.
    public func configure(baseDirectory dir: URL?) {
        baseDirectory = dir
        cache.removeAll()
        lastReport = Report()
    }

    /// Forget the base directory and every cached raster.
    public func clear() { configure(baseDirectory: nil) }

    /// The decoded image for one wire `src`, or nil when this document cannot
    /// deliver it.
    ///
    /// nil is the contract, not a fallback: the caller paints the empty box it
    /// painted before this channel existed. Inventing a placeholder raster
    /// here would put pixels on screen no stylesheet asked for and make an
    /// undelivered asset look like a renderer bug.
    public func resolve(_ src: String?) -> DecodedImage? {
        let key = (src ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return nil }
        // Cached, INCLUDING cached declines — the double optional is what
        // distinguishes "not attempted" from "attempted and declined", so a
        // decline is not re-attempted (and re-logged) once per painting box.
        if let hit = cache[key] { return hit }
        let decoded = decodeSource(key)
        cache[key] = decoded
        lastReport = Report(
            requested: lastReport.requested + 1,
            decoded: lastReport.decoded + (decoded != nil ? 1 : 0),
            declined: decoded == nil ? lastReport.declined + [key] : lastReport.declined
        )
        return decoded
    }

    /// Decode one source: a `data:` URI from its own bytes, anything else from
    /// the host-delivered file. Split out so `resolve` owns only the cache and
    /// the report.
    private func decodeSource(_ src: String) -> DecodedImage? {
        #if canImport(UIKit)
        if src.lowercased().hasPrefix("data:") { return decodeDataURI(src) }
        guard let base = baseDirectory else {
            log("declined image '\(src)': no images directory configured — the host never pointed this document at a sandbox; painting the empty box")
            return nil
        }
        // `appendingPathComponent` on a multi-segment relative path keeps the
        // segments, which is what the feeder's verbatim contract needs.
        let url = base.appendingPathComponent(src)
        guard FileManager.default.fileExists(atPath: url.path) else {
            // Name the path AND the source so an investigator can tell a
            // missing feeder hop from a bad path.
            log("declined image '\(src)': no file at \(url.path) — the feeder hop did not deliver it; painting the empty box")
            return nil
        }
        // Format gate BEFORE the decode: an SVG does not throw, it returns nil,
        // so without this the log would say "did not decode" where the truth is
        // "this platform has no rasteriser for that container".
        let ext = url.pathExtension.lowercased()
        guard Self.decodableExtensions.contains(ext) else {
            log("declined image '\(src)': '\(ext)' is a container ImageIO cannot parse (iOS decodes SVG only from a compiled asset catalog, never from a file) — painting the empty box rather than a wrong raster")
            return nil
        }
        guard let img = UIImage(contentsOfFile: url.path) else {
            log("declined image '\(src)': UIImage returned no raster for \(url.lastPathComponent)")
            return nil
        }
        return wrap(img)
        #else
        // No UIKit (a pure-Foundation host): every source declines, loudly.
        log("declined image '\(src)': no UIKit on this platform, so nothing can be decoded")
        return nil
        #endif
    }

    #if canImport(UIKit)
    /// Decode a `data:` URI's own payload. The extractor forwards an authored
    /// data: source VERBATIM (tools/titan/extract-fixture.mjs
    /// resolveReplacedSrc), so these bytes never ride the feeder hop and are
    /// the one source that works with no sandbox at all.
    ///
    /// Reuses BackgroundURLImageResolver's RFC 2397 payload decoder rather than
    /// growing a second one: `background-image: url(data:…)` and
    /// `<img src="data:…">` are the same bytes in the same encoding, and two
    /// implementations of that would be two places for a base64/percent
    /// ordering bug to hide.
    private func decodeDataURI(_ src: String) -> DecodedImage? {
        guard let comma = src.firstIndex(of: ",") else {
            log("declined image: malformed data: URI (no comma)")
            return nil
        }
        let headerStart = src.index(src.startIndex, offsetBy: 5)  // past "data:"
        let header = String(src[headerStart..<comma])
        let payload = String(src[src.index(after: comma)...])
        guard let data = BackgroundURLImageResolver.dataURIPayload(header: header, payload: payload),
              let img = UIImage(data: data) else {
            log("declined image: data: URI failed to decode (bad base64 / unsupported codec — typically an SVG payload, which ImageIO cannot rasterise)")
            return nil
        }
        return wrap(img)
    }

    /// Wrap a decoded UIImage with its INTRINSIC PIXEL size.
    private func wrap(_ img: UIImage) -> DecodedImage? {
        // points × scale = pixels. A file with no @Nx suffix decodes at scale
        // 1.0, so this is an identity for every corpus support image — but it
        // is the conversion that stays correct if one ever isn't.
        let w = Double(img.size.width * img.scale)
        let h = Double(img.size.height * img.scale)
        guard w > 0, h > 0 else {
            log("declined image: decoded raster has a degenerate size (\(w)×\(h))")
            return nil
        }
        return DecodedImage(image: img, intrinsicWidthPx: w, intrinsicHeightPx: h)
    }
    #endif

    /// Single logging seam, matching DocumentFontRegistry's (these lines are
    /// scraped out of the simulator log by the section runner).
    private func log(_ message: String) {
        print("[StyleConverter] DocumentImageRegistry: \(message)")
    }
}
