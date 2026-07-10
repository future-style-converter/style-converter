// BoxOrientConfig.ts — legacy pre-flexbox `-webkit-box-orient`
// (https://developer.mozilla.org/docs/Web/CSS/box-orient). Still shipped by
// every engine because line-clamp depends on it. Issue #38 audit: the old
// registry note claimed "we emit it through the legacy path", but membership
// in migratedProperties made the legacy path SKIP it — nothing was emitted.
export interface BoxOrientConfig { value?: string }
export const BOX_ORIENT_PROPERTY_TYPE = 'BoxOrient' as const;
export type BoxOrientPropertyType = typeof BOX_ORIENT_PROPERTY_TYPE;
