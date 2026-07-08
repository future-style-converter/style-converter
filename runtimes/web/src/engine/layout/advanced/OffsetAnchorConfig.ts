// OffsetAnchorConfig.ts — CSS `offset-anchor`.
// IR shapes: {type:'auto'} | {type:'position', x:{…}, y:{…}}.  positionPair()
// renders the latter; 'auto' is the default.
export interface OffsetAnchorConfig { value?: string; }
export const OFFSET_ANCHOR_PROPERTY_TYPE = 'OffsetAnchor' as const;
export type OffsetAnchorPropertyType = typeof OFFSET_ANCHOR_PROPERTY_TYPE;
