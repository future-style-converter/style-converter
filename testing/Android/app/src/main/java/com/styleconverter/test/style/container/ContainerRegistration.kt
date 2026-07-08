package com.styleconverter.test.style.container

// Phase 10 facade — ContainerQueryExtractor handles container-name and
// container-type, driving a ContainerQueryConfig. The `container`
// shorthand is also parsed (name/type combinations) but has no Compose
// analogue — Compose doesn't have CSS Container Queries.
//
// Parser-gap note:
//   * `container` shorthand: bare `size`/`inline-size`/`normal` means
//     type-only (name=null); bare other ident means name-only
//     (type=normal); `name/type` form requires both sides.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 3 CSS Container Queries IR properties under the `container`
 * owner. Applier is TODO — Compose has no direct analogue; container
 * query evaluation requires a two-pass layout pipeline that
 * ComponentRenderer does not currently perform.
 */
object ContainerRegistration {

    init {
        PropertyRegistry.migrated(
            "Container",
            "ContainerName",
            "ContainerType",
            owner = "container"
        )
    }
}
