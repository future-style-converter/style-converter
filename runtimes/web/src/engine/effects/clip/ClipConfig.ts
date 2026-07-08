// ClipConfig.ts — legacy CSS `clip` (deprecated CSS 2.1 §11.1.2).
// https://developer.mozilla.org/docs/Web/CSS/clip — replaced by `clip-path`.
// We still emit it because some IR sources include it for back-compat with
// old UAs/screen-readers.
// IR shape (ClipPropertyParser.kt):
//   {type:'auto'}                                       -> 'auto'
//   {type:'rect', top, right, bottom, left}             -> 'rect(<T>, <R>, <B>, <L>)'
export interface ClipConfig { value?: string; }
export const CLIP_PROPERTY_TYPE = 'Clip' as const;
export type ClipPropertyType = typeof CLIP_PROPERTY_TYPE;
