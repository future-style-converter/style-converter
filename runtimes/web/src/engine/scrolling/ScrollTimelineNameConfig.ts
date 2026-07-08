// ScrollTimelineNameConfig.ts — https://developer.mozilla.org/docs/Web/CSS/scroll-timeline-name
// IR: {name: string} — parser stores `none` as the literal string, no sentinel.
export interface ScrollTimelineNameConfig { value?: string }
export const SCROLL_TIMELINE_NAME_PROPERTY_TYPE = 'ScrollTimelineName' as const;
