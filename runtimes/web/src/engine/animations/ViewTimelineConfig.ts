// ViewTimelineConfig.ts — https://developer.mozilla.org/docs/Web/CSS/view-timeline
// IR: either a bare string (name-only) or {name, axis?} where axis is a
// SHOUTY_SNAKE enum (BLOCK|INLINE|X|Y).  CSS L2 — widened.
export interface ViewTimelineConfig { value?: string }
export const VIEW_TIMELINE_PROPERTY_TYPE = 'ViewTimeline' as const;
