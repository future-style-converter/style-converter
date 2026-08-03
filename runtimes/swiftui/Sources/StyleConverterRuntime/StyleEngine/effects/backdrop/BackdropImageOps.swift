//
//  BackdropImageOps.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  Pass-B pixel work: crop the element's border box — GROWN by the blur's
//  3σ support — out of the pass-A plate, run the planned ops over those
//  pixels, then cut the result back to the border box.
//
//  The grow/cut-back pair is wave-26 skeptic fix #1 and is what makes this
//  file byte-parallel with the Compose twin: the integer rects come from the
//  shared BackdropSampleGeometry, and only the plate's own boundary triggers
//  edge duplication (BackdropBlur's replicate pad ≡ Shader.TileMode.CLAMP).
//
//  Everything here is a PURE CGImage → CGImage transform (no SwiftUI, no
//  view tree), which is what lets the unit suite pin the arithmetic
//  device-free instead of eyeballing a capture.
//
//  Working buffer convention (must match the Compose twin's, since the two
//  platforms are compared pixel-for-pixel): 8 bits per channel, RGB in
//  memory order with an IGNORED trailing byte (`noneSkipLast`). The plate
//  is opaque by construction — it is an ImageRenderer capture of a canvas
//  that always paints a solid stage colour — so dropping alpha costs
//  nothing and, more importantly, guarantees the channel bytes are
//  STRAIGHT (never premultiplied), so the invert math below is the spec's
//  math and not a math on premultiplied values.
//

// CoreGraphics for CGImage/CGContext; CoreImage rides in via BackdropBlur.
import CoreGraphics
import Foundation

/// A PADDED crop of the plate, the border box inside it, and where the kept
/// pixels belong in the element's own coordinates.
///
/// Three rects, because the pipeline has three coordinate spaces and the
/// wave-26 fix depends on not confusing them:
///  - `image` covers the border box GROWN by the blur's 3σ support, clamped
///    to the plate. That growth is the fix: the Gaussian must see the real
///    document pixels next to the box, not a smear of the box's own edge.
///  - `keepRect` is the border box's own extent INSIDE that padded buffer,
///    in crop-local pixels — where the filtered result is cut back to.
///  - `localRect` is where the kept pixels land in the element's local POINT
///    space. Non-zero origin / smaller size only when the border box hangs
///    off the canvas, in which case the uncovered strip stays unpainted
///    instead of being smeared by a stretched draw.
struct BackdropCrop {
    /// The padded, clamped plate pixels — the filter chain's input.
    let image: CGImage
    /// The border box inside `image`, in crop-local pixels (top-left origin).
    let keepRect: CGRect
    /// Where the kept pixels belong, in points, relative to the element's
    /// top-left corner.
    let localRect: CGRect
}

enum BackdropImageOps {

    // MARK: - crop

    /// Crop `plate` to the element's border box GROWN by `padPixels` on every
    /// side (filter-effects-2 §2 — the filter reads the backdrop root image,
    /// not a pre-cut box), clamped into the plate.
    ///
    /// `elementFrame` is the element's frame in the backdrop-canvas
    /// coordinate space (what GeometryReader reports), i.e. the SAME space
    /// the plate was rendered in — so the mapping is a pure scale, with no
    /// offset term. CGImage cropping is in image space (origin top-left),
    /// which is also the canvas space's origin, so no y-flip either.
    ///
    /// `padPixels` is 0 for a blur-free chain, which collapses this back to
    /// the plain border-box crop (identical pixels to the pre-fix behaviour
    /// for every invert-only fixture).
    static func crop(_ plate: BackdropPlate,
                     elementFrame: CGRect,
                     padPixels: Int = 0) -> BackdropCrop? {
        // Points → plate pixels. `.integral` expands to whole-pixel bounds
        // so a fractional layout frame never drops the edge pixel column
        // that the element actually covers.
        let box = CGRect(x: elementFrame.minX * plate.scale,
                         y: elementFrame.minY * plate.scale,
                         width: elementFrame.width * plate.scale,
                         height: elementFrame.height * plate.scale).integral
        // Hand the integer arithmetic to the shared geometry, so this crop and
        // Compose's BackdropSampleGeometry.sample agree pixel for pixel.
        guard let sample = BackdropSampleGeometry.sample(
            elemLeft: Int(box.minX),
            elemTop: Int(box.minY),
            elemWidth: Int(box.width),
            elemHeight: Int(box.height),
            padPx: padPixels,
            srcWidth: plate.image.width,
            srcHeight: plate.image.height
        ) else { return nil }
        // Which part of the border box that (clamped) crop actually covers.
        guard let keep = BackdropSampleGeometry.keepRect(
            sample, elemWidth: Int(box.width), elemHeight: Int(box.height)
        ) else { return nil }
        // Fully off-canvas cases are already nil above; a cropping failure
        // here can only be a CoreGraphics refusal, and nil is still the honest
        // answer — the applier then draws no backplate at all.
        guard let cropped = plate.image.cropping(to: CGRect(
            x: sample.srcLeft, y: sample.srcTop,
            width: sample.width, height: sample.height)) else { return nil }
        // Back to points, relative to the element's own origin, so the applier
        // can place the image inside its local GeometryReader space. The kept
        // region's absolute plate-pixel origin is the crop's plus the keep
        // offset inside it.
        let localRect = CGRect(
            x: CGFloat(sample.srcLeft + keep.left) / plate.scale - elementFrame.minX,
            y: CGFloat(sample.srcTop + keep.top) / plate.scale - elementFrame.minY,
            width: CGFloat(keep.width) / plate.scale,
            height: CGFloat(keep.height) / plate.scale)
        return BackdropCrop(
            image: cropped,
            keepRect: CGRect(x: keep.left, y: keep.top,
                             width: keep.width, height: keep.height),
            localRect: localRect)
    }

    // MARK: - filter-then-cut-back

    /// Run `ops` over a padded crop and cut the result back to the border box.
    ///
    /// This is the order filter-effects-2 §2 specifies and the order the
    /// Compose twin executes (sample a padded rect → RenderEffect chain →
    /// clip to the border box). Doing it the other way round — the lane's
    /// original crop-then-blur — replaces the document pixels just outside the
    /// box with a replica of the box's own edge, which is visibly wrong over
    /// any non-uniform backdrop.
    ///
    /// Returns nil if any step fails, so the applier paints no backplate
    /// rather than a partially-filtered one.
    static func filtered(_ crop: BackdropCrop,
                         ops: [BackdropOp],
                         scale: CGFloat) -> CGImage? {
        guard let out = apply(ops, to: crop.image, scale: scale) else { return nil }
        // Blur-free chains sample exactly the border box, so the cut-back is
        // the identity — skip the allocation rather than round-trip a CGImage.
        if Int(crop.keepRect.width) == out.width, Int(crop.keepRect.height) == out.height,
           crop.keepRect.minX == 0, crop.keepRect.minY == 0 {
            return out
        }
        return out.cropping(to: crop.keepRect)
    }

    // MARK: - the op fold

    /// Run a plan's ops over `image`, in declared order (CSS filters
    /// compose left-to-right — filter-effects-1 §7). `scale` is the plate's
    /// pixels-per-point, needed because blur sigma is declared in CSS
    /// lengths but executed in pixels.
    ///
    /// A failing step returns nil rather than silently yielding a
    /// half-filtered image: the applier then paints no backplate, which is
    /// the pre-lane rendering, instead of an undisclosed partial one.
    static func apply(_ ops: [BackdropOp], to image: CGImage, scale: CGFloat) -> CGImage? {
        var current = image
        for op in ops {
            switch op {
            case .invert(let amount):
                guard let next = invert(current, amount: amount) else { return nil }
                current = next
            case .blur(let sigma):
                guard let next = BackdropBlur.blur(current, sigmaPixels: sigma * scale)
                else { return nil }
                current = next
            }
        }
        return current
    }

    // MARK: - invert

    /// Per-channel CSS invert on ONE 8-bit channel — filter-effects-1 §8.6:
    ///
    ///     out = c·(1 − amount) + (255 − c)·amount
    ///         = c + (255 − 2c)·amount            (the form used here)
    ///
    /// evaluated in the sRGB byte domain (NOT linearised): that is what
    /// browsers do for `invert()` on non-linear filter input, and it is the
    /// exact arithmetic the Compose twin runs on its ARGB int channels, so
    /// the two natives agree byte-for-byte before any SSIM is computed.
    /// Half-up rounding, then a defensive clamp for a malformed amount.
    static func invertByte(_ c: UInt8, amount: Double) -> UInt8 {
        let v = Double(c) + (255.0 - 2.0 * Double(c)) * amount
        return UInt8(min(255.0, max(0.0, v.rounded())))
    }

    /// Whole-image invert. Redraws into the straight-RGB working buffer
    /// described in the file header, then walks it byte by byte.
    static func invert(_ image: CGImage, amount: Double) -> CGImage? {
        guard let ctx = workingContext(like: image) else { return nil }
        // Redraw the source into the known-layout buffer. Identity draw:
        // same pixel dimensions, origin-aligned, no resampling.
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        guard let base = ctx.data else { return nil }
        let bytes = base.bindMemory(to: UInt8.self, capacity: ctx.bytesPerRow * ctx.height)
        // Row-major walk. bytesPerRow is honoured (CoreGraphics pads rows to
        // its own alignment, so height × width × 4 is NOT a safe stride).
        for y in 0..<ctx.height {
            let row = y * ctx.bytesPerRow
            for x in 0..<ctx.width {
                let px = row + x * 4
                // R, G, B. The fourth byte is the skipped alpha slot and is
                // deliberately left alone — inverting it would turn an
                // opaque plate transparent on any device whose context
                // reports the byte as meaningful.
                bytes[px]     = invertByte(bytes[px],     amount: amount)
                bytes[px + 1] = invertByte(bytes[px + 1], amount: amount)
                bytes[px + 2] = invertByte(bytes[px + 2], amount: amount)
            }
        }
        return ctx.makeImage()
    }

    // MARK: - working buffer

    /// An 8-bpc, RGBX (`noneSkipLast`) context matching `image`'s pixel
    /// dimensions. Shared by invert and by the blur's replicate-padding so
    /// both halves of the pipeline agree on layout and colour space.
    ///
    /// `width`/`height` default to the source's; the blur passes the padded
    /// size. The colour space follows the source when it is RGB-based
    /// (ImageRenderer hands back sRGB) and falls back to sRGB otherwise —
    /// never DeviceRGB, which would silently re-interpret the values.
    static func workingContext(like image: CGImage,
                               width: Int? = nil,
                               height: Int? = nil) -> CGContext? {
        let space: CGColorSpace = {
            if let cs = image.colorSpace, cs.model == .rgb { return cs }
            return CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        }()
        return CGContext(data: nil,
                         width: width ?? image.width,
                         height: height ?? image.height,
                         bitsPerComponent: 8,
                         // 0 = let CoreGraphics choose an aligned stride.
                         bytesPerRow: 0,
                         space: space,
                         bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
    }
}
