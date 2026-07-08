// BottomConfig.ts — CSS `bottom`.
// Edge-offset property; see _edge_shared for the IR value alphabet.
export interface BottomConfig { value?: string; }
export const BOTTOM_PROPERTY_TYPE = 'Bottom' as const;
export type BottomPropertyType = typeof BOTTOM_PROPERTY_TYPE;
