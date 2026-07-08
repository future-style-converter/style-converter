// DominantBaselineAdjustConfig.ts — typed config for the CSS `dominantBaselineAdjust` property.
// Family: dominant-baseline-adjust.  Mirrors the IR shape emitted by the Kotlin parser at
// src/main/kotlin/app/parsing/css/properties/longhands/typography/DominantBaselineAdjustPropertyParser.kt.

// CSS value carried verbatim — all typography properties map 1:1 to native CSS
// on the web, so the config just holds a ready-to-emit string.  Undefined means
// "property absent" (last-write-wins upstream decides when to overwrite).
export interface DominantBaselineAdjustConfig {
  value?: string | number;                                           // serialised CSS value
}

// IR property type this module recognises.  Exported so the registry / tests
// can assert the exact string without magic literals.
export const DOMINANT_BASELINE_ADJUST_PROPERTY_TYPE = 'DominantBaselineAdjust' as const;
export type DominantBaselineAdjustPropertyType = typeof DOMINANT_BASELINE_ADJUST_PROPERTY_TYPE;
