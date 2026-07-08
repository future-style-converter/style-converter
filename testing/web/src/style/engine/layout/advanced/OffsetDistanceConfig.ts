// OffsetDistanceConfig.ts — CSS `offset-distance`.
// IR shape: { type:'length', px:N } | { type:'percentage', value:N }.
// Native CSS Motion Path 1 — no widening.
export interface OffsetDistanceConfig { value?: string; }
export const OFFSET_DISTANCE_PROPERTY_TYPE = 'OffsetDistance' as const;
export type OffsetDistancePropertyType = typeof OFFSET_DISTANCE_PROPERTY_TYPE;
