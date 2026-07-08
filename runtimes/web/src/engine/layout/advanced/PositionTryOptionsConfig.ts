// PositionTryOptionsConfig.ts — CSS `position-try-options` (L1 draft).
// IR shape: string[] of enum tokens ('FLIP_BLOCK','FLIP_INLINE','FLIP_START').
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try-options.
export interface PositionTryOptionsConfig { value?: string; }
export const POSITION_TRY_OPTIONS_PROPERTY_TYPE = 'PositionTryOptions' as const;
export type PositionTryOptionsPropertyType = typeof POSITION_TRY_OPTIONS_PROPERTY_TYPE;
