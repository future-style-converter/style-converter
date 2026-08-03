//
//  BackdropBlur.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  `backdrop-filter: blur(<length>)` over a cropped plate, via CoreImage.
//
//  Two things make this more than a one-line CIGaussianBlur call:
//
//  1. EDGE REPLICATION. CoreImage treats everything outside a CIImage's
//     extent as TRANSPARENT BLACK, so blurring a buffer pulls transparency
//     in from beyond its edges and its border fades out — a dark halo the
//     browser never paints. Fix: pad the input with REPLICATED edge pixels,
//     blur the padded image, then crop back to the original rect. The band
//     has to cover the kernel's SUPPORT — measured at ~3σ for
//     CIGaussianBlur, see `padding(forSigmaPixels:)` — so nothing outside
//     the padded image can reach a kept pixel.
//
//     WHERE THAT REPLICATION IS ALLOWED TO BITE (wave-26 skeptic fix #1).
//     This function is now handed the element's border box ALREADY GROWN by
//     3σ and clamped to the plate (BackdropSampleGeometry.sample, called
//     from BackdropImageOps.crop), and the caller cuts the border box back
//     out afterwards (BackdropImageOps.filtered). So the replicated band
//     sits 3σ away from every kept pixel EXCEPT where the grow ran into the
//     plate's own boundary — which is exactly the one place
//     filter-effects-2 §2 asks for edge duplication, and exactly what the
//     Compose twin's Shader.TileMode.CLAMP does past its bitmap's bounds.
//
//     Before that fix this file received the bare border box and replicated
//     the ELEMENT's own edge, so content just outside the box never fed the
//     Gaussian. Measured gap on a black|green plate split flush against a
//     blur(8px) box's left edge: crop-then-blur held (0,204,51) one pixel
//     inside the edge where the spec model gives (0,117,29).
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

    /// Replicate-padding width in pixels for a given σ — the guard band the
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

    /// Blur `image` by `sigmaPixels` standard deviations, with replicated
    /// edges. Returns an image with the SAME pixel dimensions as the input.
    ///
    /// A non-positive σ returns the input unchanged (identity blur — the
    /// planner already drops those, this is the defensive second gate).
    static func blur(_ image: CGImage, sigmaPixels: CGFloat) -> CGImage? {
        guard sigmaPixels > 0 else { return image }
        let pad = padding(forSigmaPixels: sigmaPixels)
        // Step 1 — grow the crop with replicated edge pixels.
        guard let padded = replicatePad(image, pad: pad) else { return nil }
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

    /// Grow `image` by `pad` pixels on every side, filling the new band by
    /// replicating the outermost row/column/corner pixel — the "clamp to
    /// edge" rule a browser's backdrop sampling gets for free by owning the
    /// whole canvas.
    ///
    /// All coordinates below are spelled out because the two APIs disagree
    /// about which way is up: `CGImage.cropping(to:)` is IMAGE space (row 0
    /// at the TOP) while `CGContext.draw(_:in:)` is USER space (y grows
    /// UPWARD from the bottom). Each strip's destination rect is therefore
    /// derived from the top-down band it must fill.
    static func replicatePad(_ image: CGImage, pad: Int) -> CGImage? {
        let w = image.width, h = image.height
        // Degenerate source — nothing to replicate from.
        guard w > 0, h > 0, pad > 0 else { return image }
        guard let ctx = BackdropImageOps.workingContext(like: image,
                                                        width: w + 2 * pad,
                                                        height: h + 2 * pad)
        else { return nil }
        // Nearest-neighbour so a 1-px source strip is copied, not smoothed.
        ctx.interpolationQuality = .none
        let p = CGFloat(pad), fw = CGFloat(w), fh = CGFloat(h)
        // Centre — the original pixels, inset by the pad on every side.
        ctx.draw(image, in: CGRect(x: p, y: p, width: fw, height: fh))
        // Edge sources, in image space.
        let topRow    = image.cropping(to: CGRect(x: 0, y: 0, width: w, height: 1))
        let bottomRow = image.cropping(to: CGRect(x: 0, y: h - 1, width: w, height: 1))
        let leftCol   = image.cropping(to: CGRect(x: 0, y: 0, width: 1, height: h))
        let rightCol  = image.cropping(to: CGRect(x: w - 1, y: 0, width: 1, height: h))
        // Top band fills the padded image's TOP rows, which in user space
        // sit ABOVE the centre (y from p+h to p+h+p).
        if let topRow { ctx.draw(topRow, in: CGRect(x: p, y: p + fh, width: fw, height: p)) }
        // Bottom band — the padded image's LAST rows, user-space y 0…p.
        if let bottomRow { ctx.draw(bottomRow, in: CGRect(x: p, y: 0, width: fw, height: p)) }
        // Left/right bands span the centre's rows; no vertical ambiguity.
        if let leftCol { ctx.draw(leftCol, in: CGRect(x: 0, y: p, width: p, height: fh)) }
        if let rightCol { ctx.draw(rightCol, in: CGRect(x: p + fw, y: p, width: p, height: fh)) }
        // Corners — one source pixel each, stretched into a pad×pad square.
        // Image-space (0,0) is the TOP-left pixel, so it fills the top-left
        // square, whose user-space origin is (0, p+h).
        let corners: [(CGRect, CGRect)] = [
            (CGRect(x: 0, y: 0, width: 1, height: 1),
             CGRect(x: 0, y: p + fh, width: p, height: p)),          // top-left
            (CGRect(x: w - 1, y: 0, width: 1, height: 1),
             CGRect(x: p + fw, y: p + fh, width: p, height: p)),     // top-right
            (CGRect(x: 0, y: h - 1, width: 1, height: 1),
             CGRect(x: 0, y: 0, width: p, height: p)),               // bottom-left
            (CGRect(x: w - 1, y: h - 1, width: 1, height: 1),
             CGRect(x: p + fw, y: 0, width: p, height: p)),          // bottom-right
        ]
        for (src, dst) in corners {
            // A nil crop can only mean a degenerate source rect, already
            // excluded by the w/h guard above; skip rather than fail the
            // whole pad so a pathological image still blurs its interior.
            if let pixel = image.cropping(to: src) { ctx.draw(pixel, in: dst) }
        }
        return ctx.makeImage()
    }
}
