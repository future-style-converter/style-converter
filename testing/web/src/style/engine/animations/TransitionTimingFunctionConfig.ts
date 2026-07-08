// TransitionTimingFunctionConfig.ts — https://developer.mozilla.org/docs/Web/CSS/transition-timing-function
// IR: list of timing-function records (SAME shape as AnimationTimingFunction
// but the parser does NOT accept `linear(stops)` for transitions).
export interface TransitionTimingFunctionConfig { value?: string }
export const TRANSITION_TIMING_FUNCTION_PROPERTY_TYPE = 'TransitionTimingFunction' as const;
