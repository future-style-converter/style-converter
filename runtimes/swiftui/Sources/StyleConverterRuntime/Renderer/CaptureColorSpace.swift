//
//  CaptureColorSpace.swift
//  StyleConverterRuntime
//
//  The colour-space policy for every rasterised capture the comparison
//  pipeline consumes.
//
//  WHY THIS EXISTS
//
//  `ImageRenderer.uiImage` picks its destination colour space from the
//  CONTENT. For anything that stays inside [0,1] it picks sRGB; for a
//  render whose result leaves that range it promotes to a wide-gamut,
//  extended-range context — Display P3 on the iOS simulator,
//  extended sRGB under Mac Catalyst — and ImageIO then writes
//  `iCCP` + `cICP` colour chunks into the PNG.
//
//  Nothing downstream is colour-managed. `pngjs`, which every host-side
//  metric decodes with, registers chunk handlers for
//  IHDR/IEND/IDAT/PLTE/tRNS/gAMA only: it DISCARDS a profile rather than
//  applying it, so those wide-gamut numbers would be scored as if they
//  were sRGB. That is a silent wrong answer, not a visible failure, which
//  is why tools/visual/png-color-space.mjs refuses such a file outright
//  and the comparator exits 3.
//
//  It reached a committed 359-capture run undetected because it fires on
//  exactly ONE component — `filter: brightness(1.5)`, the only declaration
//  in that corpus whose result exceeds 1.0. (The SwiftUI applier maps it to
//  the additive `.brightness(+0.5)`; over #2ecc71 that lands green at 1.30.
//  See StyleEngine/effects/filter/FilterApplier.swift — the filter
//  ARITHMETIC is a separate, still-open divergence, documented in
//  docs/STATUS.md. This file fixes the ENCODING only.)
//
//  WHY THE FIX IS NARROW
//
//  Re-rendering EVERY capture through an explicitly-constructed sRGB
//  CGContext also yields sRGB — but it is a different rasterisation.
//  Measured on the 359-component control corpus it moved 355 of 359
//  captures: mostly 1–2 LSB on antialiased edges, but up to 202 on
//  gradients, shadows and blends. The harness is byte-deterministic (two
//  runs of identical code produced 359/359 pixel-identical captures), so
//  that drift would be a real regression against every committed baseline
//  — a large blast radius to fix one file.
//
//  So: keep `uiImage`'s own raster whenever the framework already chose a
//  space we can ship, and re-render ONLY when it promoted. `isWideGamutRGB`
//  is the property that names the promotion, and it is true for both
//  flavours observed (P3 on-device, extended sRGB under Catalyst).
//
//  Lives in the runtime rather than in the harness's ScreenshotManager so
//  the documented XCTest suite can pin it — the app-hosted test target is
//  not part of that suite.
//

import SwiftUI
import UIKit
import CoreGraphics

public enum CaptureColorSpace {

    /// The one colour space a capture may be encoded in.
    ///
    /// Measured with CGImageDestination on this toolchain: a CGImage in
    /// named sRGB (and in deviceRGB) encodes to `IHDR sRGB eXIf IDAT IEND`,
    /// while one in Display P3 encodes to `IHDR iCCP cICP …`. The host-side
    /// tripwire disqualifies `iCCP`/`cICP` and explicitly ALLOWS a plain
    /// `sRGB` chunk, so this choice is what makes the capture shippable.
    ///
    /// Named sRGB rather than deviceRGB because deviceRGB's meaning is
    /// platform-defined, and the invariant being asserted is a specific
    /// colour space, not a device default.
    public static let sRGB: CGColorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

    /// True when `image` is in a space the pipeline cannot ship — i.e. the
    /// framework promoted the render.
    ///
    /// Exposed (not private) because it is the exact predicate the fix
    /// keys on, and a test that only checked the resulting PNG chunks could
    /// pass for the wrong reason.
    public static func isPromoted(_ image: UIImage) -> Bool {
        image.cgImage?.colorSpace?.isWideGamutRGB == true
    }

    /// `ImageRenderer.uiImage`, with the colour space of the result checked
    /// rather than assumed.
    ///
    /// Returns `uiImage`'s own raster untouched in the common case, so
    /// captures stay byte-identical to the committed baselines. Returns a
    /// fresh sRGB rasterisation when — and only when — the framework
    /// promoted.
    ///
    /// nil propagates `uiImage`'s contract: callers skip the capture. A
    /// failed re-render also yields nil rather than falling back to the
    /// promoted image, because that fallback would re-admit the tag; a
    /// missing PNG is loud, a mis-tagged one is not.
    @MainActor
    public static func capture<V: View>(_ renderer: ImageRenderer<V>,
                                        scale: CGFloat) -> UIImage? {
        guard let image = renderer.uiImage else { return nil }
        guard isPromoted(image) else { return image }
        return rasterizeSRGB(renderer, scale: scale)
    }

    /// Rasterise into `sRGB` with the destination context spelled out.
    ///
    /// `render(rasterizationScale:)` hands back the content size in POINTS
    /// plus a draw function, so the context is sized in pixels and the CTM
    /// scaled to match — reproducing the geometry `uiImage` would have
    /// produced at the same scale.
    ///
    /// Re-rendering the VIEW rather than converting the promoted raster is
    /// deliberate: the out-of-range channels then clamp once, in the
    /// destination space, instead of being quantised into a wide-gamut
    /// 8-bit buffer and converted a second time.
    @MainActor
    public static func rasterizeSRGB<V: View>(_ renderer: ImageRenderer<V>,
                                              scale: CGFloat) -> UIImage? {
        var out: UIImage?
        renderer.render(rasterizationScale: scale) { size, draw in
            // Points → pixels, rounded UP, because `uiImage` ceils: a
            // fractional content size still has to be covered by whole
            // pixels. Measured — with round-to-nearest here, five captures
            // in the control corpus (Sizing_AspectRatio,
            // Typography_LineHeight and their controls) came out exactly
            // 1 px shorter than the committed geometry.
            let pxW = Int((size.width  * scale).rounded(.up))
            let pxH = Int((size.height * scale).rounded(.up))
            // A degenerate surface has nothing to encode; bail before
            // CGContext rejects the dimensions itself.
            guard pxW > 0, pxH > 0 else { return }
            guard let ctx = CGContext(
                data: nil,
                width: pxW,
                height: pxH,
                bitsPerComponent: 8,
                bytesPerRow: 0,          // 0 → CoreGraphics picks the stride
                space: sRGB,
                // The native iOS bitmap layout (BGRA) — what CoreAnimation
                // renders into anyway, so the draw path takes no extra
                // conversion. Capture surfaces are opaque, so
                // premultiplication is a no-op on the pixels baselines pin.
                bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                    | CGBitmapInfo.byteOrder32Little.rawValue
            ) else { return }
            // The draw function works in points; the context is in pixels.
            ctx.scaleBy(x: scale, y: scale)
            draw(ctx)
            guard let cg = ctx.makeImage() else { return }
            // `scale` keeps points↔pixels consistent for any caller reading
            // `.size`; `pngData()` writes the pixel grid either way.
            out = UIImage(cgImage: cg, scale: scale, orientation: .up)
        }
        return out
    }
}
