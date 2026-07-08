// PositionAreaConfig.ts — CSS `position-area` (Anchor Positioning L1 draft).
// IR shape: { row: {type:'…'}, column: {type:'…'} }.  The type values are
// spec keywords ('top','left','span-all', …); we emit "row column" per spec.
// WHY widen: csstype has no `position-area` — https://drafts.csswg.org/css-anchor-position-1/#position-area.
export interface PositionAreaConfig { value?: string; }
export const POSITION_AREA_PROPERTY_TYPE = 'PositionArea' as const;
export type PositionAreaPropertyType = typeof POSITION_AREA_PROPERTY_TYPE;
