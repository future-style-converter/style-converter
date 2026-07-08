// TransitionBehaviorConfig.ts — https://developer.mozilla.org/docs/Web/CSS/transition-behavior
// IR: singleton {type:'normal'|'allow-discrete'} or {type:'list', values:[]}.
// CSS L2 (Chromium 117+), csstype-widened.
export interface TransitionBehaviorConfig { value?: string }
export const TRANSITION_BEHAVIOR_PROPERTY_TYPE = 'TransitionBehavior' as const;
