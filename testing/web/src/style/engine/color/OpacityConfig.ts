// OpacityConfig.ts — typed record for the CSS `opacity` IR property.
// IR shape seen in fixtures: { alpha: 0..1, original: { type:'number'|'percentage', value:N } }
// or bare number 0..1.  We normalise everything to a 0..1 float.

// Simple holder — optional alpha so "not set" is distinguishable from 0.
export interface OpacityConfig {
  alpha?: number;                                                     // 0..1 after clamping; absent if unset
}

// IR property type handled here.
export const OPACITY_PROPERTY_TYPE = 'Opacity' as const;
export type OpacityPropertyType = typeof OPACITY_PROPERTY_TYPE;
