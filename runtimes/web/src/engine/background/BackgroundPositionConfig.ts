// BackgroundPositionConfig.ts — typed record for the pair
// (BackgroundPositionX, BackgroundPositionY).  The IR never emits the combined
// `BackgroundPosition` longhand after shorthand expansion, so we recombine the
// X/Y tuple here and let the applier pick between CSS `backgroundPosition`
// (when both axes are set) and the per-axis longhands (when one is missing).

// Per-axis CSS fragment such as 'center', '20px', '30%'.  We keep it pre-
// rendered because every shape we see collapses nicely to a CSS token.
export type AxisValue = string;

// Config holder — either axis is optional so partial declarations round-trip.
export interface BackgroundPositionConfig {
  x?: AxisValue;                                                      // horizontal axis CSS token
  y?: AxisValue;                                                      // vertical axis CSS token
}

// IR property types recognised.
export const BACKGROUND_POSITION_X = 'BackgroundPositionX' as const;
export const BACKGROUND_POSITION_Y = 'BackgroundPositionY' as const;
// Logical-axis equivalents. The parser now passes these through (they
// were previously filtered out as "invalid" properties); we fold them
// onto the physical axes assuming LTR horizontal writing-mode (block→Y,
// inline→X). Vertical / RTL modes will need a swap when wired in.
export const BACKGROUND_POSITION_BLOCK = 'BackgroundPositionBlock' as const;
export const BACKGROUND_POSITION_INLINE = 'BackgroundPositionInline' as const;
export type BackgroundPositionPropertyType =
  | typeof BACKGROUND_POSITION_X
  | typeof BACKGROUND_POSITION_Y
  | typeof BACKGROUND_POSITION_BLOCK
  | typeof BACKGROUND_POSITION_INLINE;
