// OffsetRotateConfig.ts — CSS `offset-rotate`.
// IR shapes from /tmp/layout_ir/offset-rotate:
//   { type:'auto' } | { type:'reverse' } | { type:'angle', deg:N } |
//   { type:'auto-angle', auto:true, reverse:false, angle:{deg:N} }
// CSS grammar: `<angle> | auto [<angle>]? | reverse [<angle>]?`.
export interface OffsetRotateConfig { value?: string; }
export const OFFSET_ROTATE_PROPERTY_TYPE = 'OffsetRotate' as const;
export type OffsetRotatePropertyType = typeof OFFSET_ROTATE_PROPERTY_TYPE;
