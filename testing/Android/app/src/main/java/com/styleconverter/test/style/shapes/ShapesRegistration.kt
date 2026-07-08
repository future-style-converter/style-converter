package com.styleconverter.test.style.shapes

// Phase 10 facade — ShapeExtractor produces a ShapeConfig for
// shape-outside / shape-margin / shape-image-threshold. This facade adds
// shape-inside + shape-padding (CSS Exclusions), which are parse-only on
// Compose mobile.
//
// Parser-gap notes:
//   * ShapeOutside has Keyword / Raw / None / shape-box (4 variants) /
//     ImageUrl / BasicShape variants — the basic-shape content is NOT
//     parsed, just detected and stored as a raw string.
//   * ShapeMargin has a Raw catch-all (never fails) — length or % or Raw.
//   * ShapeInside / ShapePadding are strict.
//   * ShapeImageThreshold range-checked 0..1.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers the 5 CSS Shapes / Exclusions IR properties under the
 * `shapes` owner. Float-wrap-around does not exist in Compose, so shape-
 * outside is parse-only in practice (ShapeApplier only emits a clip-path
 * approximation when the renderer happens to support it).
 */
object ShapesRegistration {

    init {
        PropertyRegistry.migrated(
            "ShapeOutside",
            "ShapeMargin",
            "ShapePadding",
            "ShapeInside",
            "ShapeImageThreshold",
            owner = "shapes"
        )
    }
}
