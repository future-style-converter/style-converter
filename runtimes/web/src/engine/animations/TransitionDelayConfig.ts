// TransitionDelayConfig.ts — https://developer.mozilla.org/docs/Web/CSS/transition-delay
// IR: list of IRTime (negative values allowed per spec).
export interface TransitionDelayConfig { value?: string }
export const TRANSITION_DELAY_PROPERTY_TYPE = 'TransitionDelay' as const;
