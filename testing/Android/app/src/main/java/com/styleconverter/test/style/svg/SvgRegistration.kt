package com.styleconverter.test.style.svg

// Phase 10 facade — claims every SVG presentation property under the `svg`
// owner on the PropertyRegistry. SvgExtractor already wires the core
// paint/stroke/marker family into SvgConfig; this facade also claims the
// SVG-specific geometry attributes (cx/cy/r/rx/ry/x/y/d), vector-effect,
// color-interpolation*, color-rendering, buffered-rendering,
// enable-background, flood-*, lighting-color, and stop-color — most of
// which only matter inside an actual <svg> subtree which our renderer does
// not yet support on Android.
//
// Parser-gap notes (see examples/properties/README-phase10.md):
//   * Cx/Cy/R/Rx/Ry/X/Y never fail — parser always returns a property
//     (raw fallback). Appliers must validate the stored value.
//   * D returns null only for the empty string; anything else is stored as
//     a raw path string.
//   * StrokeWidth uses LengthParser so unitless values are REJECTED
//     ("stroke-width: 3" is invalid here — fixture uses `em`/`px` only).
//   * Fill/Stroke have distinct None / ContextFill / ContextStroke /
//     UrlReference / ColorValue variants; Stroke additionally has a Raw
//     catch-all that never fails.
//   * FillOpacity / StrokeOpacity range-check 0..1 (no clamp; out-of-range
//     returns null).
//   * FloodOpacity is the only opacity parser that accepts `%`.
//   * ColorInterpolation / ColorInterpolationFilters / ColorRendering /
//     ShapeRendering accept the CSS-spec camelCase tokens (sRGB, linearRGB,
//     optimizeSpeed) because the parser lowercases first.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 34 Phase 10 SVG-category IR property names under the `svg`
 * owner on the PropertyRegistry.
 *
 * ## Property-family breakdown
 * - **fill / stroke paint family** (12) — wired via SvgExtractor.
 * - **stop + marker family** (6) — wired via SvgExtractor.
 * - **paint-order** (1) — wired.
 * - **SVG geometry attributes** (8) — cx, cy, r, rx, ry, x, y, d. Parse-only:
 *   our Android renderer doesn't interpret `<svg>` subtrees, so these sit
 *   on the config with no modifier binding.
 * - **rendering hints** (4) — ColorRendering / ShapeRendering already wired
 *   by rendering/RenderingExtractor.
 * - **vector-effect** (1) — parse-only.
 * - **color-interpolation\***  (2) — parse-only; SRGB vs linearRGB blend
 *   space is a GPU-pipeline concern that Compose doesn't expose.
 * - **buffered-rendering / enable-background** (2) — deprecated / SVG-only.
 * - **flood-color / flood-opacity / lighting-color** (3) — SVG filter
 *   primitives; parse-only on Compose.
 *
 * ## TODO applier work
 * Everything here is currently identity (config-only) unless rendered
 * inside a future `<svg>`-aware component. The Phase 10 goal is honest
 * registry coverage, not runtime rendering of SVG primitives. Mobile
 * customers that actually need SVG will round-trip through an Android
 * vector-drawable converter, not through ComponentRenderer.
 */
object SvgRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- paint (fill + stroke) ----
            "Fill", "FillOpacity", "FillRule",
            "Stroke", "StrokeOpacity", "StrokeWidth",
            "StrokeLinecap", "StrokeLinejoin", "StrokeMiterlimit",
            "StrokeDasharray", "StrokeDashoffset",
            // ---- stops + flood + lighting (filter primitives) ----
            "StopColor", "StopOpacity",
            "FloodColor", "FloodOpacity", "LightingColor",
            // ---- markers + paint-order ----
            "Marker", "MarkerStart", "MarkerMid", "MarkerEnd", "MarkerSide",
            "PaintOrder",
            // ---- rendering hints (ShapeRendering also claimed by
            //      rendering/RenderingExtractor via its own registration;
            //      idempotent claim here is fine) ----
            "ShapeRendering",
            "ColorRendering",
            "ColorInterpolation", "ColorInterpolationFilters",
            "BufferedRendering",
            "EnableBackground",
            "VectorEffect",
            // ---- geometry attributes (parse-only on Compose) ----
            // The CSS SVG-2 spec promotes these attrs to CSS properties, so
            // the parser emits them; they only render inside <svg> subtrees.
            "Cx", "Cy", "R", "Rx", "Ry", "X", "Y", "D",
            owner = "svg"
        )
    }
}
