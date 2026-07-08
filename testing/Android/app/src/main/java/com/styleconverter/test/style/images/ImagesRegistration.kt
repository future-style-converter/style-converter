package com.styleconverter.test.style.images

// Phase 10 facade — ObjectFitExtractor + ObjectFitApplier handle
// `object-fit` + `object-position` (wired through the background /
// content-alignment pipeline). This facade claims those two names plus
// object-view-box and the image-rendering variants the parser emits.
//
// Parser-gap notes:
//   * ImageOrientation: <angle> flip? OR `none | from-image`.
//   * ImageResolution: DPI/DPCM normalized to DPPX.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 5 images-category IR property names under the `images` owner.
 * ImageRendering / ImageOrientation / ImageResolution overlap with
 * rendering/RenderingRegistration (first-write-wins).
 */
object ImagesRegistration {

    init {
        PropertyRegistry.migrated(
            "ObjectFit", "ObjectPosition", "ObjectViewBox",
            "ImageRendering",
            "ImageOrientation", "ImageResolution",
            owner = "images"
        )
    }
}
