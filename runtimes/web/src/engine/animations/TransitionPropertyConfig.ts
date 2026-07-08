// TransitionPropertyConfig.ts — https://developer.mozilla.org/docs/Web/CSS/transition-property
// IR: list of {type:'all'|'none'|'property-name', name?:string}.
export interface TransitionPropertyConfig { value?: string }
export const TRANSITION_PROPERTY_PROPERTY_TYPE = 'TransitionProperty' as const;
