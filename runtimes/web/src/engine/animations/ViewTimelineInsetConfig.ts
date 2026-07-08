// ViewTimelineInsetConfig.ts — https://developer.mozilla.org/docs/Web/CSS/view-timeline-inset
// IR: {start, end} where each is {type:'auto'} | {type:'length', px} | {type:'percentage', value}.
export interface ViewTimelineInsetConfig { value?: string }
export const VIEW_TIMELINE_INSET_PROPERTY_TYPE = 'ViewTimelineInset' as const;
