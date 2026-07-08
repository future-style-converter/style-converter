// OffsetPositionConfig.ts — CSS `offset-position`.
// IR shapes: {type:'auto'|'normal'} | {type:'position', x, y}.
export interface OffsetPositionConfig { value?: string; }
export const OFFSET_POSITION_PROPERTY_TYPE = 'OffsetPosition' as const;
export type OffsetPositionPropertyType = typeof OFFSET_POSITION_PROPERTY_TYPE;
