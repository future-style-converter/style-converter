// InsetAreaConfig.ts — CSS `inset-area` (earlier name for `position-area`).
// IR shape: { type:'single', value:'x' } | { type:'combined', first:'x', second:'y' }.
// WHY widen: draft-level — the property was renamed to `position-area`; we
// still emit it because legacy Chromium builds accept `inset-area`.
// See https://developer.chrome.com/blog/anchor-positioning-api#inset-area.
export interface InsetAreaConfig { value?: string; }
export const INSET_AREA_PROPERTY_TYPE = 'InsetArea' as const;
export type InsetAreaPropertyType = typeof INSET_AREA_PROPERTY_TYPE;
