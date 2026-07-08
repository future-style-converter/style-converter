// AnimationDurationConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-duration
// IR is `AnimationDurationValue`: one of `Durations(list<IRTime>)`, `Keyword`
// (e.g. `auto` — new in L2), or `Expression` (verbatim calc/var).
export interface AnimationDurationConfig { value?: string }
export const ANIMATION_DURATION_PROPERTY_TYPE = 'AnimationDuration' as const;
