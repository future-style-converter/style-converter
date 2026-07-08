// AnchorNameConfig.ts — CSS `anchor-name` (Anchor Positioning L1 draft).
// IR shapes: {type:'none'} | {type:'single',name:'--x'} | {type:'multiple',names:string[]}.
// Parser accepts comma- and space-separated lists; we serialise as
// comma-separated (the CSSWG-preferred form per the fixture README).
// WHY widen: csstype has no `anchor-name` entry — https://drafts.csswg.org/css-anchor-position-1/#name-defining.
export interface AnchorNameConfig { value?: string; }
export const ANCHOR_NAME_PROPERTY_TYPE = 'AnchorName' as const;
export type AnchorNamePropertyType = typeof ANCHOR_NAME_PROPERTY_TYPE;
