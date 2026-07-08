// LeftConfig.ts — CSS `left`.
// Edge-offset property; see _edge_shared for the IR value alphabet.
export interface LeftConfig { value?: string; }
export const LEFT_PROPERTY_TYPE = 'Left' as const;
export type LeftPropertyType = typeof LEFT_PROPERTY_TYPE;
