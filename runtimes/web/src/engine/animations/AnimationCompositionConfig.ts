// AnimationCompositionConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-composition
// IR: tagged union — {type:'replace'|'add'|'accumulate'} OR
// {type:'list', values:string[]}.  CSS Level 2; csstype may lack this key,
// so applier widens to Record<string,string>.
export interface AnimationCompositionConfig { value?: string }
export const ANIMATION_COMPOSITION_PROPERTY_TYPE = 'AnimationComposition' as const;
