// AnimationDelayConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-delay
// IR: `delays: List<IRTime>` — flat time list.  Negative values allowed per spec.
export interface AnimationDelayConfig { value?: string }
export const ANIMATION_DELAY_PROPERTY_TYPE = 'AnimationDelay' as const;
