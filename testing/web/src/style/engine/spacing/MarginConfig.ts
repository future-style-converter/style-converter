// MarginConfig.ts — like PaddingConfig, but each side may also be 'auto'.
// CSS distinguishes margin-auto (which centers / absorbs free space) from
// percentages (which resolve against the containing block's inline size),
// so we retain LengthValue's full union and just special-case auto.

import type { LengthValue } from '../core/types/LengthValue';

// Margin value: the Phase-1 LengthValue for numeric/relative/calc shapes,
// plus a distinct 'auto' variant that CSS renders very differently.
// LengthValue itself already has a {kind:'auto'} member, so we simply pass
// the union through — this keeps MarginApplier's switch exhaustive.
export type MarginValue = LengthValue;

// Per-side record — same 8 sides as PaddingConfig.
export interface MarginConfig {
  top?: MarginValue;
  right?: MarginValue;
  bottom?: MarginValue;
  left?: MarginValue;
  blockStart?: MarginValue;
  blockEnd?: MarginValue;
  inlineStart?: MarginValue;
  inlineEnd?: MarginValue;
}

// IR property-type strings recognised as margin inputs.
export const MARGIN_PROPERTY_TYPES = [
  'MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft',
  'MarginBlockStart', 'MarginBlockEnd', 'MarginInlineStart', 'MarginInlineEnd',
] as const;

// Tuple literal type — used by the extractor for exhaustive dispatch.
export type MarginPropertyType = (typeof MARGIN_PROPERTY_TYPES)[number];
