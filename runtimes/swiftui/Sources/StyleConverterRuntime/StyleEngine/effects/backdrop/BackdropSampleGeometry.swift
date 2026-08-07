//
//  BackdropSampleGeometry.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  "Which backdrop pixels may this element read?" — the byte-parallel twin of
//  runtimes/compose/.../effects/backdrop/BackdropSampleGeometry.kt.
//
//  THE SAMPLE RECT IS THE BORDER BOX — IT NEVER GROWS WITH σ (wave-34 lane B).
//
//  filter-effects-2 §2 (Backdrop Filter Algorithm) clips the Backdrop Root
//  Image to the element's border box BEFORE the filter runs, and only then
//  asks the filter for its edge behaviour. Content outside the border box is
//  therefore NEVER an input: a blurred backdrop must not import the pixels
//  next to the box, only extensions of the box's own edge.
//
//  Wave 26 shipped the opposite reading (grow the sample by the blur's 3σ
//  support, then cut back). Wave 34 refuted it with the corpus's own boundary
//  case — css/filter-effects/backdrop-filter-boundary.html, whose six `.fg`
//  boxes sit 5px inside a 160x90 `.bg` on a lime page and whose WPT assertion
//  is literally "No lime green should be brought in to the blurred regions".
//  Measured on the frozen wave33-final captures (mean absolute error per tile
//  against the Chrome 151 ref PNG, blur 3/6/12/24/48/96, `_diag34/laneB`):
//
//    grow-by-3σ model     1.6 / 3.6 / 80.5 / 19.7 / 25.6 / 99.3   ← wave 26
//    border box + clamp   1.6 / 2.5 / 77.5 / 10.6 / 22.3 / 89.8
//    border box + MIRROR  1.5 / 1.8 / 72.6 /  0.8 /  0.3 / 76.7   ← this file
//    iOS capture          1.6 / 3.5 / 79.9 / 19.5 / 25.3 / 99.2
//
//  The iOS row reproduces the grow-by-3σ row term for term, which is what
//  identifies the model as the cause; the web capture (Chrome's own
//  `backdrop-filter`) is byte-identical to the ref on four of the six tiles,
//  so the ref IS the browser's behaviour and not an artefact of the WPT
//  reference file's simulation. Tiles 3 and 6 carry a residual common to all
//  three platforms (the ref frame clips content at x=374, our composed canvas
//  at 390) that no filter model can move.
//
//  The extension model is MIRROR, not clamp: the numbers above separate the
//  two, and the WPT reference file (`support/simulate-backdrop-blur.js`)
//  builds its expectation from `scale(-1)` copies of the element's own
//  border-box crop — Skia's `SkTileMode::kMirror`, which is what Chrome hands
//  the backdrop image. Compose gets it from `Shader.TileMode.MIRROR`; here it
//  is BackdropBlur's `mirrorPad`.
//
//  What remains here: the border box CLAMPED to the plate — the canvas has no
//  pixels outside itself, so an element hanging off it samples only the strip
//  that exists and `keepRect` says which part of the box that covers.
//
//  Everything here is integer pixel arithmetic with no CoreGraphics types, so
//  the numbers can be diffed against the Kotlin table function-for-function
//  (BackdropMathTests' cross-native pin table).
//

// Foundation for max/min only — deliberately no CoreGraphics import, so
// nothing in this file can drift into point space.
import Foundation

/// One crop of the pass-A plate, in two coordinate spaces.
///
/// - `srcLeft`/`srcTop`/`width`/`height`: the rectangle to read from the plate
///   (plate pixel space, ALWAYS inside the plate's bounds).
/// - `dstLeft`/`dstTop`: where that crop's origin sits relative to the
///   element's BORDER BOX origin. Zero whenever the border box is fully on the
///   canvas — which is the normal case, since the sample rect IS the border
///   box — and positive only when the box overhangs the canvas's top/left edge
///   and the clamp had to start the crop inside it.
struct BackdropSample: Equatable {
    let srcLeft: Int
    let srcTop: Int
    let width: Int
    let height: Int
    let dstLeft: Int
    let dstTop: Int
}

enum BackdropSampleGeometry {

    /// Compute the crop for an element whose border box sits at
    /// (`elemLeft`, `elemTop`) with size `elemWidth`×`elemHeight` in the
    /// plate's pixel space, clamped into a `srcWidth`×`srcHeight` plate.
    ///
    /// There is deliberately NO pad parameter: filter-effects-2 §2 clips the
    /// backdrop to the border box before filtering, so the sample rect is the
    /// border box for every chain — see the file header for the measurement
    /// that refuted the wave-26 grow-by-3σ reading.
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
        srcWidth: Int,
        srcHeight: Int
    ) -> BackdropSample? {
        // Degenerate inputs: no box to filter, or no plate to read.
        guard elemWidth > 0, elemHeight > 0 else { return nil }
        guard srcWidth > 0, srcHeight > 0 else { return nil }

        // The border box in plate space, clamped into the plate. The clamp is
        // what makes an element at the canvas edge legal: we read the pixels
        // that exist, and the blur's mirror band extends them — the spec's
        // edge behaviour, and Compose's TileMode.MIRROR.
        let left = max(0, elemLeft)
        let top = max(0, elemTop)
        let right = min(srcWidth, elemLeft + elemWidth)
        let bottom = min(srcHeight, elemTop + elemHeight)

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
    /// border box can hang off the canvas, in which case only part of it
    /// exists in the crop and the uncovered strip must stay unpainted rather
    /// than be smeared by a stretched draw. Returns nil when the two do not overlap
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
