// AnimationTimingFunctionConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-timing-function
// IR: list of timing-function records (cubic-bezier, steps, or linear stops).
// The top-level emission is native CSS (animation-timing-function ships in
// csstype).  Linear() stops are CSS L2 — browsers accept the longer form.
export interface AnimationTimingFunctionConfig { value?: string }
export const ANIMATION_TIMING_FUNCTION_PROPERTY_TYPE = 'AnimationTimingFunction' as const;
