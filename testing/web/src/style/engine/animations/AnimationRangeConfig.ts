// AnimationRangeConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-range
// IR: {start, end?} where each field is either:
//   - number   -> percent
//   - string   -> pre-serialised ("normal", "entry 0%", ...)
//   - {px:...} -> absolute length
// CSS L2, csstype-widened.
export interface AnimationRangeConfig { value?: string }
export const ANIMATION_RANGE_PROPERTY_TYPE = 'AnimationRange' as const;
