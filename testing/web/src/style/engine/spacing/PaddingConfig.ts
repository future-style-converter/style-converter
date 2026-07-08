// PaddingConfig.ts — typed record of padding values, one entry per CSS side.
// Physical sides (top/right/bottom/left) and logical sides (block-*/inline-*)
// are kept separate; resolution to physical happens in PaddingApplier using
// writing-mode / direction.

// We reuse the Phase-1 LengthValue union so every downstream applier speaks
// the same alphabet (exact px, relative unit, auto, calc, intrinsic, fr, unknown).
import type { LengthValue } from '../core/types/LengthValue';

// Every field is optional — a component only populates sides explicitly set
// via CSS longhands (padding-* shorthand is expanded upstream).
export interface PaddingConfig {
  // Physical sides — take precedence if both a physical & logical are present.
  top?: LengthValue;
  right?: LengthValue;
  bottom?: LengthValue;
  left?: LengthValue;
  // Logical sides — resolved against LTR/RTL + horizontal/vertical writing-mode.
  blockStart?: LengthValue;
  blockEnd?: LengthValue;
  inlineStart?: LengthValue;
  inlineEnd?: LengthValue;
}

// IR property-type strings we recognise as padding inputs; used by both the
// registry and the extractor to gate legacy/migrated dispatch.
export const PADDING_PROPERTY_TYPES = [
  'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft',
  'PaddingBlockStart', 'PaddingBlockEnd', 'PaddingInlineStart', 'PaddingInlineEnd',
] as const;

// Tuple literal type — handy for switch exhaustiveness in extractor.
export type PaddingPropertyType = (typeof PADDING_PROPERTY_TYPES)[number];
