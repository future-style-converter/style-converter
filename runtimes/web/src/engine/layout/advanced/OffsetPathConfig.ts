// OffsetPathConfig.ts — CSS `offset-path`.
// IR shapes from /tmp/layout_ir/offset-path:
//   {type:'none'} | {type:'path-string', path:'M …'} |
//   {type:'circle', radius:'…', position?:'center'} | {type:'ellipse', radiusX, radiusY} |
//   {type:'polygon', points:string[]} | {type:'ray', deg:N} | {type:'ray', angle:{deg:N}, size:'…'}
// Native CSS property — no widening.
export interface OffsetPathConfig { value?: string; }
export const OFFSET_PATH_PROPERTY_TYPE = 'OffsetPath' as const;
export type OffsetPathPropertyType = typeof OFFSET_PATH_PROPERTY_TYPE;
