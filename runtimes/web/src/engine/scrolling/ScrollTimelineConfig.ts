// ScrollTimelineConfig.ts — https://developer.mozilla.org/docs/Web/CSS/scroll-timeline
// IR: {name:{name:'--...'}, axis:'BLOCK'|'INLINE'|'X'|'Y'}.  CSS L2, widened.
export interface ScrollTimelineConfig { value?: string }
export const SCROLL_TIMELINE_PROPERTY_TYPE = 'ScrollTimeline' as const;
