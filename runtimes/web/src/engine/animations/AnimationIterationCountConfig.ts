// AnimationIterationCountConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-iteration-count
// IR uses a custom serializer (see AnimationIterationCountProperty.kt): each
// entry is EITHER the primitive string "infinite" OR an IRNumber object.
export interface AnimationIterationCountConfig { value?: string }
export const ANIMATION_ITERATION_COUNT_PROPERTY_TYPE = 'AnimationIterationCount' as const;
