// TopConfig.ts — CSS `top`.
// Edge-offset property; see _edge_shared for the IR value alphabet.
export interface TopConfig { value?: string; }
export const TOP_PROPERTY_TYPE = 'Top' as const;
export type TopPropertyType = typeof TOP_PROPERTY_TYPE;
