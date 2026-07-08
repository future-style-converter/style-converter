// AnimationNameConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-name
// IR: list of {type:'none'} | {type:'identifier', name}.  Web emits the raw
// identifier list joined on ", " (native CSS since 2012).  `none` collapses
// to the keyword; multi-value lists pick `none` only when it's the sole entry.
export interface AnimationNameConfig { value?: string }
export const ANIMATION_NAME_PROPERTY_TYPE = 'AnimationName' as const;
