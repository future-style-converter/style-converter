// PositionFallbackConfig.ts — CSS `position-fallback` (predecessor of
// `position-try` — kept for backward compat with older parser output).
// IR shape: { type:'none' } | { type:'named', name:'--x' }.
// WHY widen: draft-level spec (now superseded) — see
// https://drafts.csswg.org/css-anchor-position-1/#position-fallback.
export interface PositionFallbackConfig { value?: string; }
export const POSITION_FALLBACK_PROPERTY_TYPE = 'PositionFallback' as const;
export type PositionFallbackPropertyType = typeof POSITION_FALLBACK_PROPERTY_TYPE;
