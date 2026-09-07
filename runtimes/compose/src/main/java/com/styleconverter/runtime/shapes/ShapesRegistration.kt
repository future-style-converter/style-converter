package com.styleconverter.runtime.shapes

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

import com.styleconverter.runtime.PropertyRegistry

/**
 * Registers the 5 CSS Shapes / Exclusions IR properties under the
 * `shapes` owner. Float-wrap-around does not exist in Compose, so shape-
 * outside is PARSE-ONLY: nothing reads the extracted config. (Retro sweep
 * P2a: the "ShapeApplier only emits a clip-path approximation" caveat named
 * a 482-line object with no caller, now deleted. `clip-path` itself is
 * rendered by effects/clip/ClipPathApplier, which is unrelated to this
 * registration.)
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
