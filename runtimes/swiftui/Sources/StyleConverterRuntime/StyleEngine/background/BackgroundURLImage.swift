//
//  BackgroundURLImage.swift
//  StyleEngine/background — wave 8 (issue #36: url() raster layers).
//
//  Ends the "url() paints Color.clear BY DESIGN" gap: data-URI and local
//  file url() layers decode to a UIImage and paint through a Canvas that
//  honors background-size (cover/contain/px/%/auto), background-position
//  (keywords/percent/px) and background-repeat (repeat/no-repeat per
//  axis) via the pure BackgroundImageGeometry math.
//
//  Deliberate limits (all logged, never silent):
//    • http(s) URLs are a DEFINED NO-OP — a network fetch would make
//      captures race the connection (determinism contract,
//      docs/DYNAMIC_CAPTURE.md); an SDUI host that wants remote images
//      should pre-fetch and hand the runtime file/data URIs.
//    • undecodable payloads (bad base64, unsupported codecs) log once
//      and paint nothing — the same visual the browser reference shows
//      for a failed background load. NOTE the known converter quirk:
//      the CSS parser lowercases whole declaration values, which
//      corrupts case-sensitive base64 data URIs on the wire. FULLY
//      percent-encoded data URIs (lowercase hex) survive it — the
//      fixtures use that form until the parser preserves url() case.
//    • `space`/`round` repeat approximate to `repeat` (geometry file).
//

import SwiftUI
import UIKit

/// Decode + cache url() payloads. Namespace enum.
enum BackgroundURLImageResolver {

    /// Process-wide decode cache: a capture run resolves the same layer
    /// once per component render pass — decoding per frame would wreck
    /// the live animation clock.
    private static let cache = NSCache<NSString, UIImage>()

    /// Sentinel for negative caching (avoid re-parsing broken payloads).
    private static var failed = Set<String>()
    private static let failedLock = NSLock()

    /// UIImage for a url() layer string, or nil (logged) when the layer
    /// is remote / undecodable. Never throws, never blocks on network.
    static func image(for url: String) -> UIImage? {
        if let hit = cache.object(forKey: url as NSString) { return hit }
        failedLock.lock()
        let knownBad = failed.contains(url)
        failedLock.unlock()
        if knownBad { return nil }
        let decoded = decode(url)
        if let img = decoded {
            cache.setObject(img, forKey: url as NSString)
        } else {
            failedLock.lock(); failed.insert(url); failedLock.unlock()
        }
        return decoded
    }

    /// One-shot decode dispatch by scheme.
    private static func decode(_ url: String) -> UIImage? {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = trimmed.lowercased()
        // Remote URLs: defined no-op (capture determinism — header note).
        if lower.hasPrefix("http:") || lower.hasPrefix("https:") {
            PropertyTracker.logOnce(
                key: "bg-url-remote:\(trimmed)",
                message: "background-image url('\(trimmed.prefix(64))…') is remote — defined no-op at wave-8 v1 (deterministic captures; pre-fetch to a file/data URI)")
            return nil
        }
        // Data URIs: RFC 2397 `data:[mediatype][;base64],payload`.
        if lower.hasPrefix("data:") {
            guard let comma = trimmed.firstIndex(of: ","),
                  let data = dataURIPayload(header: String(trimmed[trimmed.index(trimmed.startIndex, offsetBy: 5)..<comma]),
                                            payload: String(trimmed[trimmed.index(after: comma)...])),
                  let img = UIImage(data: data) else {
                PropertyTracker.logOnce(
                    key: "bg-url-decode:\(trimmed.prefix(48))",
                    message: "background-image data URI failed to decode — painting nothing (known cause: the converter lowercases url() values, corrupting base64; use fully percent-encoded data URIs)")
                return nil
            }
            return img
        }
        // Local files: file:// URLs, absolute paths, or bundle resources.
        let path = lower.hasPrefix("file://")
            ? String(trimmed.dropFirst("file://".count))
            : trimmed
        if let img = UIImage(contentsOfFile: path) { return img }
        // Bundle-relative lookup (harness support/ assets ride the app
        // bundle the same way the web harness serves them statically).
        if let res = Bundle.main.path(forResource: path, ofType: nil),
           let img = UIImage(contentsOfFile: res) {
            return img
        }
        PropertyTracker.logOnce(
            key: "bg-url-file:\(trimmed)",
            message: "background-image url('\(trimmed.prefix(64))') not found as file or bundle resource — painting nothing")
        return nil
    }

    /// Payload bytes of a data URI. base64 marker in the header selects
    /// base64 (percent-decoded first — RFC 2397 allows %xx inside);
    /// otherwise the payload is percent-encoded raw bytes.
    static func dataURIPayload(header: String, payload: String) -> Data? {
        if header.lowercased().hasSuffix(";base64") || header.lowercased().contains(";base64;") {
            // Percent-unescape THEN base64-decode (browser order).
            let unescaped = payload.removingPercentEncoding ?? payload
            return Data(base64Encoded: unescaped, options: [.ignoreUnknownCharacters])
        }
        return percentDecode(payload)
    }

    /// Percent-decode to RAW BYTES. `String.removingPercentEncoding`
    /// round-trips through UTF-8 validation and destroys arbitrary
    /// binary (a PNG is not valid UTF-8), so this walks bytes manually.
    static func percentDecode(_ s: String) -> Data? {
        var out = Data()
        let bytes = Array(s.utf8)
        var i = 0
        while i < bytes.count {
            if bytes[i] == UInt8(ascii: "%"), i + 2 < bytes.count,
               let hi = hexValue(bytes[i + 1]), let lo = hexValue(bytes[i + 2]) {
                out.append(hi << 4 | lo)
                i += 3
            } else {
                out.append(bytes[i])
                i += 1
            }
        }
        return out
    }

    /// One hex nibble (case-insensitive — lowercase survives the
    /// converter's value lowercasing, which is the whole point).
    private static func hexValue(_ b: UInt8) -> UInt8? {
        switch b {
        case UInt8(ascii: "0")...UInt8(ascii: "9"): return b - UInt8(ascii: "0")
        case UInt8(ascii: "a")...UInt8(ascii: "f"): return b - UInt8(ascii: "a") + 10
        case UInt8(ascii: "A")...UInt8(ascii: "F"): return b - UInt8(ascii: "A") + 10
        default: return nil
        }
    }
}

/// The paint view for one url() layer: a Canvas that draws the tile
/// lattice from BackgroundImageGeometry. Sized by the parent
/// `.background(...)` slot, which is exactly the CSS painting area.
struct BackgroundURLImageView: View {
    /// Decoded raster.
    let uiImage: UIImage
    /// Per-layer background-size (nil = auto).
    let sizeLayer: BackgroundSizeLayer?
    /// Per-axis background-position (nil = 0%).
    let positionX: BackgroundAxisPosition?
    let positionY: BackgroundAxisPosition?
    /// Per-layer background-repeat (nil = repeat, the CSS initial).
    let repeatLayer: BackgroundRepeatLayer?

    var body: some View {
        Canvas { context, size in
            // Intrinsic pixel size: UIImage.size is in points — multiply
            // by scale so a decoded PNG's pixels are CSS px at 1×.
            let pixels = CGSize(width: uiImage.size.width * uiImage.scale,
                                height: uiImage.size.height * uiImage.scale)
            let plan = BackgroundImageGeometry.placement(
                imageSize: pixels, box: size, size: sizeLayer,
                positionX: positionX, positionY: positionY,
                repeatLayer: repeatLayer)
            let img = Image(uiImage: uiImage)
            // Dense lattices (tiny tiles over a big box) blow past the
            // rect cap — a tiled SHADING fills them exactly when the
            // scale is uniform (cover/contain/auto always are).
            let count = BackgroundImageGeometry.tileCount(placement: plan, box: size)
            let sx = plan.tileSize.width / max(pixels.width, 1)
            let sy = plan.tileSize.height / max(pixels.height, 1)
            if count > BackgroundImageGeometry.tileCap,
               plan.repeatX, plan.repeatY, abs(sx - sy) < 0.0001 {
                context.fill(Path(CGRect(origin: .zero, size: size)),
                             with: .tiledImage(img, origin: plan.origin,
                                               sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1),
                                               scale: sx))
            } else {
                // Enumerated lattice — the general path (per-axis repeat,
                // non-uniform explicit sizes). Capped defensively; the
                // cap only truncates pathological tiny-tile cases the
                // shading path above did not catch (logged).
                if count > BackgroundImageGeometry.tileCap {
                    PropertyTracker.logOnce(
                        key: "bg-url-cap",
                        message: "background-image tile lattice exceeds \(BackgroundImageGeometry.tileCap) rects with non-uniform scale — truncated (wave-8 v1)")
                }
                for rect in BackgroundImageGeometry.tileRects(placement: plan, box: size) {
                    context.draw(img, in: rect)
                }
            }
        }
    }
}
