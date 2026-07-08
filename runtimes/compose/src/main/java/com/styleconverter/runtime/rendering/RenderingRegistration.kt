package com.styleconverter.runtime.rendering

// Phase 10 facade — claims the `rendering` category IR properties. The
// existing RenderingExtractor already produces a RenderingConfig for
// ColorRendering / ImageRendering / ShapeRendering / TextRendering, and
// ZoomExtractor produces a ZoomConfig for `zoom`. This facade adds every
// other rendering-related IR property that the parser emits (field-sizing,
// forced-color-adjust, print-color-adjust, image-orientation/resolution,
// input-security, interpolate-size, color-interpolation*, content-
// visibility) so they show up on PropertyRegistry.allRegistered().
//
// Most of these are ACROSS-PHASE properties: color-interpolation lives in
// svg/SvgRegistration, content-visibility in performance + interactions,
// forced-color-adjust + print-color-adjust + field-sizing + input-security
// in interactions/forms. Idempotent first-write-wins registration means
// the `owner` column shows whichever facade loaded first — that's fine
// for coverage auditing.
//
// Parser-gap notes:
//   * ImageOrientation: `<angle> flip?` OR `none | from-image`. `flip`
//     alone (no angle) means 0deg flip.
//   * ImageResolution: DPI + DPCM are normalized to DPPX internally.
//   * Zoom: Normal / Reset / Percentage / Number (no length form).
//   * ForcedColorAdjust has a Raw catch-all; appliers must validate.
//   * ContentVisibility / FieldSizing / InputSecurity / InterpolateSize /
//     PrintColorAdjust are strict enums.

import com.styleconverter.runtime.PropertyRegistry

/**
 * Registers 12 rendering-category IR property names under the `rendering`
 * owner. Cross-phase overlaps are noted in comments — first-write-wins
 * means the owner string reflects load order, which is acceptable for
 * coverage audits.
 */
object RenderingRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- image rendering / orientation / resolution (3) ----
            "ImageRendering",
            "ImageOrientation",
            "ImageResolution",
            // ---- color-rendering + interpolation (3) ----
            // Overlap: ColorInterpolation* + ColorRendering also claimed
            // by svg/SvgRegistration. First-write wins.
            "ColorRendering",
            "ColorInterpolation",
            "ColorInterpolationFilters",
            // ---- shape rendering hint ----
            // TextRendering is owned by typography/ (Phase 6 tripwire).
            "ShapeRendering",
            // ---- zoom / interpolate-size / content-visibility ----
            "Zoom", "InterpolateSize",
            "ContentVisibility",
            // ---- color-adjust variants ----
            // Overlap: also claimed by interactions/forms/FormStylingExtractor.
            "ForcedColorAdjust", "PrintColorAdjust",
            // ---- field-sizing / input-security (form-adjacent but the
            //      parser emits them under rendering category) ----
            "FieldSizing", "InputSecurity",
            // CSS Color HDR 1 §3.1 — Compose has no HDR/SDR clamping
            // primitive, so we claim the IR property for coverage and
            // leave the applier as identity (parity-only). Lives under
            // rendering since it's a colour-pipeline hint.
            "DynamicRangeLimit",
            owner = "rendering"
        )
    }
}
