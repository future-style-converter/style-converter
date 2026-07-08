// FilterConfig.ts — CSS `filter` (Filter Effects 1).
// https://developer.mozilla.org/docs/Web/CSS/filter
// IR shape (FilterPropertyParser.kt):
//   'none'                                           -> 'none'
//   {url:'#mono'}                                    -> 'url("#mono")'
//   [ {fn:'blur', r:{px}}, {fn:'brightness', v:150}, …]
// Each filter-function variant is enumerated in the extractor.

export interface FilterConfig { value?: string; }                                  // pre-serialised
export const FILTER_PROPERTY_TYPE = 'Filter' as const;
export type FilterPropertyType = typeof FILTER_PROPERTY_TYPE;
