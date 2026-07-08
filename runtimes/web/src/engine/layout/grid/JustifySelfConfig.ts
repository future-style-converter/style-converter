// JustifySelfConfig.ts — CSS `justify-self`.
// IR wraps the value as { type: 'start'|'end'|'center'|'stretch' } per
// /tmp/layout_ir/grid-justify-self.  We keep AlignSelf outside this file
// because AlignSelf arrives as a bare enum (shared enum factory handles it).
export interface JustifySelfConfig { value?: string; }
export const JUSTIFY_SELF_PROPERTY_TYPE = 'JustifySelf' as const;
export type JustifySelfPropertyType = typeof JUSTIFY_SELF_PROPERTY_TYPE;
