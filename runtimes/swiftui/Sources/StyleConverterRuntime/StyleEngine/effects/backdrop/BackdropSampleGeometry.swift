//
//  BackdropSampleGeometry.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  "Which backdrop pixels may this element read?" — the byte-parallel twin of
//  runtimes/compose/.../effects/backdrop/BackdropSampleGeometry.kt.
//
//  WHY THIS FILE EXISTS (wave-26 skeptic fix #1). The lane originally cropped
//  the element's border box out of the plate FIRST and then blurred that crop
//  with its OWN edge pixels replicated. That feeds the Gaussian pixels that do
//  not exist in the document: content just outside the border box — the very
//  content a browser's backdrop blur pulls in — was replaced by a smear of the
//  box's own edge. Measured gap against Compose on a black|green plate split
//  flush against a blur(8px) box's left edge: (0,204,51) one pixel inside the
//  edge, where the browser and the Compose twin give (0,117,29).
//
//  The spec model (filter-effects-2 §2), which BOTH natives now implement:
//    • the filter reads the Backdrop Root Image, so the sample rect is the
//      border box EXPANDED by the blur's 3σ support;
//    • that rect is CLAMPED to the image — the canvas has no pixels outside
//      itself — and only there does edge duplication kick in (Compose:
//      Shader.TileMode.CLAMP; here: BackdropBlur's replicate pad, now applied
//      at the PADDED crop's boundary, which coincides with the canvas edge
//      exactly where the clamp above bit);
//    • the filtered result is cropped BACK to the border box afterwards.
//
//  Everything here is integer pixel arithmetic with no CoreGraphics types, so
//  the numbers can be diffed against the Kotlin table function-for-function
//  (BackdropMathTests' cross-native pin table).
//

// Foundation for ceil/max/min only — deliberately no CoreGraphics import, so
// nothing in this file can drift into point space.
import Foundation

/// One crop of the pass-A plate, in two coordinate spaces.
///
/// - `srcLeft`/`srcTop`/`width`/`height`: the rectangle to read from the plate
///   (plate pixel space, ALWAYS inside the plate's bounds).
/// - `dstLeft`/`dstTop`: where that crop's origin sits relative to the
///   element's BORDER BOX origin. Zero for an unpadded, fully-inside sample;
///   negative once the blur padding reaches left/up, which is the whole point —
///   pixels from OUTSIDE the box are in the buffer the Gaussian runs over, and
///   the border-box crop that follows the filter cuts them back off.
struct BackdropSample: Equatable {
    let srcLeft: Int
    let srcTop: Int
    let width: Int
    let height: Int
    let dstLeft: Int
    let dstTop: Int
}

enum BackdropSampleGeometry {

    /// How far outside the border box a blur of standard deviation `sigma`
    /// can still pull visible energy from: 3σ, the conventional Gaussian
    /// truncation (>99.7% of the kernel mass), rounded UP so the sample is
    /// never a pixel short of what the kernel reads. 0 for a chain with no
    /// blur, which collapses the sample to the border box exactly.
    ///
    /// Identical to `BackdropSampleGeometry.blurPadPx` on Compose — NOT to
    /// `BackdropBlur.padding(forSigmaPixels:)`, which is the separate
    /// replicate band applied INSIDE the blur and carries one extra pixel of
    /// slack for CoreImage's σ→kernel rounding.
    static func blurPadPx(_ sigmaPixels: Double) -> Int {
        sigmaPixels <= 0 ? 0 : Int(ceil(3.0 * sigmaPixels))
    }

    /// Compute the crop for an element whose border box sits at
    /// (`elemLeft`, `elemTop`) with size `elemWidth`×`elemHeight` in the
    /// plate's pixel space, padded by `padPx` on every side and clamped into a
    /// `srcWidth`×`srcHeight` plate.
    ///
    /// Returns nil when there is nothing to sample — a degenerate element
    /// (zero width/height: `backdrop-filter-zero-size.html`), a degenerate
    /// plate, or a border box entirely off the canvas. Nil is a REFUSAL, not a
    /// fallback: the applier draws no backplate and the element paints exactly
    /// as it would without this lane.
    static func sample(
        elemLeft: Int,
        elemTop: Int,
        elemWidth: Int,
        elemHeight: Int,
        padPx: Int,
        srcWidth: Int,
        srcHeight: Int
    ) -> BackdropSample? {
        // Degenerate inputs: no box to filter, or no plate to read.
        guard elemWidth > 0, elemHeight > 0 else { return nil }
        guard srcWidth > 0, srcHeight > 0 else { return nil }

        // Requested (padded) rect in plate space, then clamped into the plate.
        // The clamp is what makes an element at the canvas edge legal: we read
        // the pixels that exist, and the blur's replicate band extends them —
        // the spec's edge-duplication rule, and Compose's TileMode.CLAMP.
        let left = max(0, elemLeft - padPx)
        let top = max(0, elemTop - padPx)
        let right = min(srcWidth, elemLeft + elemWidth + padPx)
        let bottom = min(srcHeight, elemTop + elemHeight + padPx)

        // Fully off-canvas (or clamped to nothing) → nothing to sample.
        guard right > left, bottom > top else { return nil }

        return BackdropSample(
            srcLeft: left,
            srcTop: top,
            width: right - left,
            height: bottom - top,
            // Element-local placement of the crop: how far the crop's origin
            // sits from the border box's origin.
            dstLeft: left - elemLeft,
            dstTop: top - elemTop
        )
    }

    /// The part of the border box that the (clamped) crop actually covers,
    /// expressed in CROP-LOCAL pixels — the rect the filtered buffer is cut
    /// back to once the chain has run.
    ///
    /// Split out from `sample` because it is a second, independent clamp: the
    /// padded rect can be clipped by the plate on the outside (handled above)
    /// AND the border box itself can hang off the canvas, in which case only
    /// part of it exists in the crop. Returns nil when the two do not overlap
    /// at all, which `sample`'s own guards already exclude — the check is kept
    /// so the function is total rather than relying on a caller invariant.
    static func keepRect(
        _ sample: BackdropSample,
        elemWidth: Int,
        elemHeight: Int
    ) -> (left: Int, top: Int, width: Int, height: Int)? {
        // The border box's origin inside the crop is the negation of the
        // crop's origin relative to the box.
        let boxLeft = -sample.dstLeft
        let boxTop = -sample.dstTop
        // Intersect the box with the crop's own bounds.
        let left = max(0, boxLeft)
        let top = max(0, boxTop)
        let right = min(sample.width, boxLeft + elemWidth)
        let bottom = min(sample.height, boxTop + elemHeight)
        guard right > left, bottom > top else { return nil }
        return (left: left, top: top, width: right - left, height: bottom - top)
    }
}
