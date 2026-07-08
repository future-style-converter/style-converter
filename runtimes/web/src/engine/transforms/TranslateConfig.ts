// TranslateConfig.ts — CSS `translate` longhand (CSS Transforms L2).
// https://developer.mozilla.org/docs/Web/CSS/translate
// IR shape (TranslatePropertyParser.kt):
//   {type:'none'}                                   -> 'none'
//   {type:'length', length:{px}}                    -> '<x>'
//   {type:'percentage', percentage:N}               -> '<N>%'
//   {type:'2d', x:{…len…}, y:{…len…}}               -> '<x> <y>'
//   {type:'3d', x:{…len…}, y:{…len…}, z:{…len…}}    -> '<x> <y> <z>'

export interface TranslateConfig { value?: string; }
export const TRANSLATE_PROPERTY_TYPE = 'Translate' as const;
export type TranslatePropertyType = typeof TRANSLATE_PROPERTY_TYPE;
