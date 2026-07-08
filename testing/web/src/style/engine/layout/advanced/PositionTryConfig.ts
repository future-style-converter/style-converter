// PositionTryConfig.ts — CSS `position-try` (Anchor Positioning L1 draft).
// IR shape: string[] of named idents (empty = unset; emit 'none' per parser
// semantics for the "none" keyword).
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try.
export interface PositionTryConfig { value?: string; }
export const POSITION_TRY_PROPERTY_TYPE = 'PositionTry' as const;
export type PositionTryPropertyType = typeof POSITION_TRY_PROPERTY_TYPE;
