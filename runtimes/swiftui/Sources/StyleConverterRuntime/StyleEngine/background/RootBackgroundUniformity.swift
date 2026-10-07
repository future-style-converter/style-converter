//
//  RootBackgroundUniformity.swift
//  StyleEngine/background — wave-53 lane L3 (item A), split out of
//  RootBackgroundPropagation.swift at the wave-53 fix pass (PLAN §0 size rule;
//  bodies moved verbatim).
//
//  The F2 "uniform BY CONSTRUCTION" predicates of the root canvas background:
//  a stack may cover the 16-px capture frame only when every layer paints one
//  sRGBA AND repeats on both axes (capture-browser-ref.mjs padColorFor :728
//  fills the frame with the ring colour only for a uniform ring). Twins: web
//  engine/background/RootBackgroundUniformity.ts, Compose
//  background/RootBackgroundUniformity.kt.
//

import Foundation

enum RootBackgroundUniformity {

    /// F2 for a whole stack: every layer one colour and every repeat entry `repeat` on both axes.
    static func stackIsUniform(_ layers: [IRValue], repeats: [IRValue]) -> Bool {
        layers.allSatisfy(layerIsUniform) && repeats.allSatisfy(entryRepeatsBoth)   // no gap, no second colour
    }

    /// One stop's sRGBA key: nil = no colour (hint/shape word), "?" = not static sRGB.
    private static func stopKey(_ stop: IRValue) -> String? {
        guard let c = stop["color"] else { return nil }          // colour-less entry
        if case .null = c { return nil }                         // an explicit colour-less stop
        guard let s = c["srgb"], let r = s["r"]?.doubleValue, let g = s["g"]?.doubleValue,
              let b = s["b"]?.doubleValue else { return "?" }    // currentColor / var(): unknown
        return "\(r),\(g),\(b),\(s["a"]?.doubleValue ?? 1)"      // alpha defaults to opaque
    }

    /// css-images-3 §3: every stop shares one sRGBA ⇒ the gradient paints one colour.
    private static func gradientIsUniform(_ o: IRValue) -> Bool {
        guard let stops = o["stops"]?.arrayValue else { return false }   // malformed → not uniform
        let keys = stops.compactMap(stopKey)                     // colour-less entries dropped
        return !keys.isEmpty && keys.allSatisfy { $0 != "?" && $0 == keys[0] }   // one static sRGBA
    }

    /// RFC 2397 payload bytes (base64 or percent-encoded), or nil.
    private static func dataURIBytes(_ url: String) -> [UInt8]? {
        guard url.lowercased().hasPrefix("data:"), let comma = url.firstIndex(of: ",") else { return nil }
        let header = url[url.index(url.startIndex, offsetBy: 5)..<comma].lowercased()   // mediatype[;base64]
        let body = String(url[url.index(after: comma)...])       // the payload
        if header.hasSuffix(";base64") { return Data(base64Encoded: body).map { [UInt8]($0) } }   // bad base64 → nil
        // Percent-encoding, byte by byte (a binary PNG is not valid UTF-8).
        var out: [UInt8] = []; var i = body.utf8.startIndex; let u = body.utf8
        while i < u.endIndex {                                   // %XX → one byte, anything else verbatim
            if u[i] == UInt8(ascii: "%"), let a = u.index(i, offsetBy: 2, limitedBy: u.index(before: u.endIndex)),
               let v = UInt8(String(decoding: u[u.index(after: i)...a], as: UTF8.self), radix: 16) {
                out.append(v); i = u.index(after: a)
            } else { out.append(u[i]); i = u.index(after: i) }   // a literal byte
        }
        return out                                               // the raw PNG bytes
    }

    /// PNG (ISO 15948 §5.2/§11.2.2): signature, then IHDR width/height — true iff 1×1.
    static func dataPngIs1x1(_ url: String) -> Bool {
        let lower = url.lowercased()                             // the scheme/mediatype is case-insensitive
        guard lower.hasPrefix("data:image/png,") || lower.hasPrefix("data:image/png;"),
              let b = dataURIBytes(url), b.count >= 24 else { return false }   // PNG data URIs with a full IHDR
        guard Array(b[0..<8]) == [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a],   // the PNG signature
              Array(b[12..<16]) == Array("IHDR".utf8) else { return false }       // the first chunk is IHDR
        let be32 = { (o: Int) in b[o..<(o + 4)].reduce(UInt32(0)) { $0 << 8 | UInt32($1) } }   // big-endian
        return be32(16) == 1 && be32(20) == 1                    // width, height
    }

    /// One layer paints a single colour across its tile (F2's per-layer test).
    private static func layerIsUniform(_ l: IRValue) -> Bool {
        if RootBackgroundPropagation.isNone(l) { return true }   // transparent everywhere
        if let url = l["url"]?.stringValue { return dataPngIs1x1(url) }   // a 1×1 raster tile
        guard let t = l["type"]?.stringValue else { return false }   // a bare URL string: unknown pixels
        return t.hasSuffix("gradient") && gradientIsUniform(l)   // linear/radial/conic, plain or repeating
    }

    /// §3.7: one entry is `repeat` on both axes (string tokens or an {x,y} pair).
    private static func entryRepeatsBoth(_ e: IRValue) -> Bool {
        if let s = e.stringValue {                               // `repeat` / `repeat repeat`
            return s.split(whereSeparator: { $0.isWhitespace }).allSatisfy { $0.lowercased() == "repeat" }
        }
        // The axis-pair shape (any other keyword leaves a gap or a single tile).
        return e["x"]?.stringValue?.lowercased() == "repeat" && e["y"]?.stringValue?.lowercased() == "repeat"
    }
}
