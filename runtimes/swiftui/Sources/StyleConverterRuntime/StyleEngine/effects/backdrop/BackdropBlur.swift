//
//  BackdropBlur.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  `backdrop-filter: blur(<length>)` over a cropped plate, via CoreImage.
//
//  Two things make this more than a one-line CIGaussianBlur call:
//
//  1. EDGE EXTENSION. CoreImage treats everything outside a CIImage's
//     extent as TRANSPARENT BLACK, so blurring a buffer pulls transparency
//     in from beyond its edges and its border fades out — a dark halo the
//     browser never paints. Fix: pad the input with MIRRORED edge pixels,
//     blur the padded image, then crop back to the original rect. The band
//     has to cover the kernel's SUPPORT — measured at ~3σ for
//     CIGaussianBlur, see `padding(forSigmaPixels:)` — so nothing outside
//     the padded image can reach a kept pixel.
//
//     WHY MIRROR AND NOT REPLICATE (wave-34 lane B). This function is handed
//     exactly the element's BORDER BOX (BackdropSampleGeometry.sample, via
//     BackdropImageOps.crop) because filter-effects-2 §2 clips the backdrop
//     to the border box before the filter runs — so the band IS the spec's
//     edge behaviour, on every pixel of every edge, not just at the canvas
//     boundary. Measured against the Chrome 151 ref for
//     backdrop-filter-boundary (per-tile MAE, blur 3…96): replicate gives
//     1.6 / 2.5 / 77.5 / 10.6 / 22.3 / 89.8, mirror gives
//     1.5 / 1.8 / 72.6 / 0.8 / 0.3 / 76.7. The WPT reference file
//     (support/simulate-backdrop-blur.js) builds its expectation from
//     `scale(-1)` copies of the same crop — Skia's SkTileMode::kMirror,
//     which is what the Compose twin now asks RenderEffect for.
//
//     Wave 26 instead grew the crop by 3σ so the Gaussian saw the document
//     pixels next to the box. That is what the corpus refuted: the six
//     blurred tiles of backdrop-filter-boundary imported the lime page
//     background the test explicitly forbids, and the iOS capture's error
//     row reproduced the grown-sample model term for term.
//
//  2. COLOUR SPACE. CSS filter functions operate in sRGB —
//     filter-effects-1 pins `color-interpolation-filters: sRGB` for the
//     shorthand functions, which is why Chrome's blur mixes gamma-encoded
//     values. CoreImage defaults to a LINEAR working space, which blurs
//     visibly lighter across a dark→light edge. The shared context below
//     therefore pins BOTH the working and output space to sRGB.
//

// CoreImage for the Gaussian; CoreGraphics for the CGImage plumbing.
import CoreGraphics
import CoreImage
import Foundation

enum BackdropBlur {

    /// CIGaussianBlur's `inputRadius` per unit of CSS standard deviation.
    ///
    /// CSS `blur(Npx)` means a Gaussian with stdDeviation = N
    /// (filter-effects-1 §8.2 → SVG feGaussianBlur). CIGaussianBlur's
    /// parameter is named "radius", and this constant is the MEASURED
    /// conversion between the two: BackdropMathTests
    /// (testBlurRecoversDeclaredSigmaFromAStepEdge) recovers the effective σ
    /// from a blurred step edge and fails if this factor drifts, so the
    /// number is pinned by measurement rather than by folklore. Measured at
    /// 1.0 on Xcode 26 — CIGaussianBlur's radius IS the σ.
    static let ciRadiusPerSigma: CGFloat = 1.0

    /// Mirror-padding width in pixels for a given σ — the guard band the
    /// header describes.
    ///
    /// MEASURED, not assumed. The band has to cover CIGaussianBlur's KERNEL
    /// SUPPORT, not the visually-significant part of the Gaussian: any
    /// transparent-black pixel the kernel can still reach contaminates the
    /// kept region. A 2σ band (the "tail under 2.3%" figure) measurably
    /// failed — a uniform 24×24 crop blurred at σ=5 came back 193/200 at
    /// the corner and 196/200 at the edge midpoints
    /// (BackdropMathTests.testBlurKeepsUniformCropUniformAtTheBorder), i.e.
    /// CoreImage's kernel reaches about 3σ. The band is therefore the
    /// standard 3σ truncation plus one pixel of slack for the σ→kernel
    /// rounding, and the test above is the regression guard: shrink this
    /// and the border fades again.
    static func padding(forSigmaPixels sigma: CGFloat) -> Int {
        max(1, Int(ceil(3 * sigma)) + 1)
    }

    /// The shared sRGB-pinned CoreImage context (see header note 2).
    /// One instance because CIContext creation is expensive and the object
    /// is documented as thread-safe and reusable.
    private static let ciContext: CIContext = {
        let srgb = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        return CIContext(options: [.workingColorSpace: srgb, .outputColorSpace: srgb])
    }()

    /// Blur `image` by `sigmaPixels` standard deviations, with mirrored
    /// edges. Returns an image with the SAME pixel dimensions as the input.
    ///
    /// A non-positive σ returns the input unchanged (identity blur — the
    /// planner already drops those, this is the defensive second gate).
    static func blur(_ image: CGImage, sigmaPixels: CGFloat) -> CGImage? {
        guard sigmaPixels > 0 else { return image }
        let pad = padding(forSigmaPixels: sigmaPixels)
        // Step 1 — grow the crop with mirrored edge pixels.
        guard let padded = mirrorPad(image, pad: pad) else { return nil }
        // Step 2 — Gaussian over the padded image.
        guard let filter = CIFilter(name: "CIGaussianBlur") else { return nil }
        filter.setValue(CIImage(cgImage: padded), forKey: kCIInputImageKey)
        filter.setValue(sigmaPixels * ciRadiusPerSigma, forKey: kCIInputRadiusKey)
        guard let output = filter.outputImage else { return nil }
        // Step 3 — crop back to the original rect. The pad is symmetric, so
        // this rect is identical under CoreImage's bottom-left origin and
        // CoreGraphics' top-left one; no flip bookkeeping is needed. Note
        // CIGaussianBlur's output extent is INFINITE-ish (it grows by the
        // kernel), so an explicit `from:` rect is mandatory.
        let keep = CGRect(x: CGFloat(pad), y: CGFloat(pad),
                          width: CGFloat(image.width), height: CGFloat(image.height))
        return ciContext.createCGImage(output, from: keep)
    }

    /// How many mirrored copies are needed on each side to cover `pad` pixels
    /// of band given a tile of extent `extent` — `ceil(pad / extent)`, floored
    /// at 1 so a band narrower than one tile still gets its reflection.
    ///
    /// Pure integer arithmetic, split out so the unit suite can pin it without
    /// a raster. Mirrors the WPT reference file's `copiesX`/`copiesY`
    /// (css/filter-effects/support/simulate-backdrop-blur.js), which is the
    /// oracle this whole edge model was measured against.
    static func mirrorCopies(pad: Int, extent: Int) -> Int {
        guard extent > 0, pad > 0 else { return 0 }
        // Integer ceiling division — (pad + extent − 1) / extent.
        return max(1, (pad + extent - 1) / extent)
    }

    /// True when the tile at grid index `i` (0 = the centre tile) is drawn
    /// flipped on that axis: every ODD step away from the centre reflects.
    ///
    /// Pure, and exported for the unit pins for the same reason as above: the
    /// parity rule IS the mirror model, so it is worth asserting directly
    /// rather than inferring it from a blurred raster.
    static func mirrorFlips(_ i: Int) -> Bool { abs(i) % 2 == 1 }

    /// Grow `image` by `pad` pixels on every side, filling the new band with
    /// MIRRORED copies of the image — Skia's `SkTileMode::kMirror`, which is
    /// the edge behaviour Chrome hands a backdrop-filter's clipped backdrop
    /// (see the file header for the per-tile measurement, and the Compose
    /// twin's `Shader.TileMode.MIRROR`).
    ///
    /// A band wider than the image needs more than one reflection, exactly
    /// like the WPT reference file's copy grid; `mirrorCopies` sizes it.
    ///
    /// The vertical bookkeeping the replicate version needed is gone: every
    /// tile is a whole-image draw, so `CGContext.draw(_:in:)`'s user-space
    /// (y-up) convention applies identically to all of them and the flip is
    /// taken about each tile's own centre. Whether the grid index counts up
    /// or down is therefore immaterial — the parity pattern is symmetric.
    static func mirrorPad(_ image: CGImage, pad: Int) -> CGImage? {
        let w = image.width, h = image.height
        // Degenerate source — nothing to mirror from.
        guard w > 0, h > 0, pad > 0 else { return image }
        guard let ctx = BackdropImageOps.workingContext(like: image,
                                                        width: w + 2 * pad,
                                                        height: h + 2 * pad)
        else { return nil }
        // Nearest-neighbour: every draw is 1:1 (possibly flipped), never
        // resampled, so interpolation could only blur the copy.
        ctx.interpolationQuality = .none
        let p = CGFloat(pad), fw = CGFloat(w), fh = CGFloat(h)
        let cx = mirrorCopies(pad: pad, extent: w)
        let cy = mirrorCopies(pad: pad, extent: h)
        for j in -cy...cy {
            for i in -cx...cx {
                // Tile (i,j)'s destination, with the centre tile inset by the
                // pad on every side. Tiles that fall outside the context are
                // clipped away by CoreGraphics, so no bounds test is needed.
                let dst = CGRect(x: p + CGFloat(i) * fw, y: p + CGFloat(j) * fh,
                                 width: fw, height: fh)
                let fx: CGFloat = mirrorFlips(i) ? -1 : 1
                let fy: CGFloat = mirrorFlips(j) ? -1 : 1
                if fx == 1, fy == 1 {
                    // Unflipped tile — the centre and every even step out.
                    ctx.draw(image, in: dst)
                    continue
                }
                // Flip about the tile's own centre so the reflection shares
                // its edge column/row with the neighbour it mirrors (kMirror
                // duplicates the edge pixel; a flip about the EDGE would drop
                // it and shift the whole band by one).
                ctx.saveGState()
                ctx.translateBy(x: dst.midX, y: dst.midY)
                ctx.scaleBy(x: fx, y: fy)
                ctx.translateBy(x: -dst.midX, y: -dst.midY)
                ctx.draw(image, in: dst)
                ctx.restoreGState()
            }
        }
        return ctx.makeImage()
    }
}
