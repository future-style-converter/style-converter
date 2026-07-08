// AnimationTimelineConfig.ts — https://developer.mozilla.org/docs/Web/CSS/animation-timeline
// IR variants (AnimationTimelinePropertyParser.kt):
//   {type:'auto'|'none'}                          -> keyword
//   {type:'named', name:'--x'}                    -> named
//   {type:'scroll', scroller?, axis?}             -> scroll(...) L2 function
//   {type:'view', axis?, insetStart?, insetEnd?}  -> view(...) L2 function
// CSS Level 2 — csstype may not know the key, so applier widens.
export interface AnimationTimelineConfig { value?: string }
export const ANIMATION_TIMELINE_PROPERTY_TYPE = 'AnimationTimeline' as const;
