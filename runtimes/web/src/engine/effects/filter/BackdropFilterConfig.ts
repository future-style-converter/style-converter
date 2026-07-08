// BackdropFilterConfig.ts — CSS `backdrop-filter` (Filter Effects 2).
// https://developer.mozilla.org/docs/Web/CSS/backdrop-filter
// Same grammar as `filter`; Safari still needs -webkit- prefix.
export interface BackdropFilterConfig { value?: string; }
export const BACKDROP_FILTER_PROPERTY_TYPE = 'BackdropFilter' as const;
export type BackdropFilterPropertyType = typeof BACKDROP_FILTER_PROPERTY_TYPE;
