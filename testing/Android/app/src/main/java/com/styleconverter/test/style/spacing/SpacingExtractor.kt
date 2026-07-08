package com.styleconverter.test.style.spacing

// Back-compat facade over the per-family extractors. The modular triplets
// (PaddingExtractor, MarginExtractor, GapExtractor) are the real work;
// callers that still reference SpacingExtractor route through here to keep
// Phase 2 a strictly additive migration at the call sites.
//
// LayoutFacade.extractConfig() is the only current caller; it invokes
// extractPaddingConfig / extractMarginConfig / extractGapConfig directly.

import kotlinx.serialization.json.JsonElement

object SpacingExtractor {

    /** Delegate to PaddingExtractor. Kept for the LayoutFacade call site. */
    fun extractPaddingConfig(properties: List<Pair<String, JsonElement?>>): PaddingConfig =
        PaddingExtractor.extract(properties)

    /** Delegate to MarginExtractor. */
    fun extractMarginConfig(properties: List<Pair<String, JsonElement?>>): MarginConfig =
        MarginExtractor.extract(properties)

    /** Delegate to GapExtractor. */
    fun extractGapConfig(properties: List<Pair<String, JsonElement?>>): GapConfig =
        GapExtractor.extract(properties)

    /** Predicate used by LayoutFacade.isLayoutProperty. */
    fun isSpacingProperty(propertyType: String): Boolean =
        PaddingExtractor.isPaddingProperty(propertyType) ||
            MarginExtractor.isMarginProperty(propertyType) ||
            GapExtractor.isGapProperty(propertyType)
}
