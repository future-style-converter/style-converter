// PositionTryFallbacksConfig.ts — CSS `position-try-fallbacks` (L1 draft).
// IR shape: string[] of named idents (see /tmp/layout_ir/position-try).
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try-fallbacks.
export interface PositionTryFallbacksConfig { value?: string; }
export const POSITION_TRY_FALLBACKS_PROPERTY_TYPE = 'PositionTryFallbacks' as const;
export type PositionTryFallbacksPropertyType = typeof POSITION_TRY_FALLBACKS_PROPERTY_TYPE;
