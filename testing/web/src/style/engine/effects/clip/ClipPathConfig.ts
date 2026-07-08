// ClipPathConfig.ts — CSS `clip-path` (Masking 1).
// https://developer.mozilla.org/docs/Web/CSS/clip-path
// IR shapes (ClipPathPropertyParser.kt):
//   'none'                                                    -> 'none'
//   '#elementRef'                                              -> raw passthrough
//   {type:'inset', t,r,b,l [, round]}                          -> inset(...)
//   {type:'circle', r?|px?, pos?:{x,y}}                        -> circle(...)
//   {type:'ellipse', rx, ry [, pos]}                           -> ellipse(...)
//   {type:'polygon', points:[{x,y}, ...]}                      -> polygon(...)
//   {type:'rect', t,r,b,l}                                     -> rect(...)
//   {type:'xywh', x,y,w,h [, round]}                           -> xywh(...)
//   {type:'path', d:'M …'}                                     -> path("…")
//   {'geometry-box':'border-box', shape?:<any of above>}       -> '<shape> <box>'
//                                                                  (box keyword alone if no shape)
export interface ClipPathConfig { value?: string; }                                // pre-serialised
export const CLIP_PATH_PROPERTY_TYPE = 'ClipPath' as const;
export type ClipPathPropertyType = typeof CLIP_PATH_PROPERTY_TYPE;
