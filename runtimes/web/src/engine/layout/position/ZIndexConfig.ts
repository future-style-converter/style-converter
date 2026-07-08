// ZIndexConfig.ts — CSS `z-index`.
// IR shape (see /tmp/layout_ir/z-index): { value:N, original:'auto' | {type:'integer',value:N} }.
// CSS accepts `auto` or an integer — both land in the same applier.
export interface ZIndexConfig { value?: number | 'auto'; }

export const Z_INDEX_PROPERTY_TYPE = 'ZIndex' as const;
export type ZIndexPropertyType = typeof Z_INDEX_PROPERTY_TYPE;
