// ViewTransitionGroupConfig.ts — https://developer.mozilla.org/docs/Web/CSS/view-transition-group
// IR: {type:'normal'|'nearest'|'contain'|'root'} or {type:'Raw', value} for
// unknown idents (parser keeps case).
export interface ViewTransitionGroupConfig { value?: string }
export const VIEW_TRANSITION_GROUP_PROPERTY_TYPE = 'ViewTransitionGroup' as const;
