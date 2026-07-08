package com.styleconverter.test.style.regions

// Phase 10 facade — CSS Regions was removed from standards-track in 2015
// and never implemented on any mobile platform. RegionFlowExtractor
// already handles FlowInto / FlowFrom / RegionFragment; this facade also
// claims the Continue / CopyInto / Wrap* longhands the parser recognises.
// Applier is a no-op on every platform.
//
// Parser-gap notes:
//   * FlowInto / FlowFrom accept anything (non-`none` wrapped in Named).
//   * CopyInto stores raw string unchecked.
//   * Continue / RegionFragment / WrapFlow / WrapThrough / WrapBefore /
//     WrapAfter / WrapInside are strict enums.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers all 10 CSS Regions-module IR property names under the
 * `regions` owner. All parse-only on every platform target.
 */
object RegionsRegistration {

    init {
        PropertyRegistry.migrated(
            "FlowInto", "FlowFrom",
            "RegionFragment",
            "Continue",
            "CopyInto",
            "WrapFlow", "WrapThrough",
            "WrapBefore", "WrapAfter", "WrapInside",
            owner = "regions"
        )
    }
}
