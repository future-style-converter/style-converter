// PaddingApplier.ts — turns a PaddingConfig into a React inline-style partial.
// CSS natively resolves %/em/vw/auto/calc so our job is purely serialisation:
// take each LengthValue, hand it to toCssLength, emit the matching CSS key.
//
// Logical sides are emitted as-is (paddingBlockStart etc.) rather than
// resolved to physical, because modern browsers support the logical CSS
// longhands directly and this keeps `direction: rtl` working automatically.

import type { CSSProperties } from 'react';
import { toCssLength } from '../core/types/LengthValue';
import type { PaddingConfig } from './PaddingConfig';

// Output type — partial CSSProperties so callers can spread into parent styles.
export type PaddingStyles = Pick<
  CSSProperties,
  | 'paddingTop' | 'paddingRight' | 'paddingBottom' | 'paddingLeft'
  | 'paddingBlockStart' | 'paddingBlockEnd'
  | 'paddingInlineStart' | 'paddingInlineEnd'
>;

// Main applier — pure function, no side effects, easy to unit test.
export function applyPadding(config: PaddingConfig): PaddingStyles {
  const out: PaddingStyles = {};                                      // blank accumulator
  // Physical sides — emit only if the caller set them (keeps the style object minimal).
  if (config.top)    out.paddingTop    = toCssLength(config.top);     // 20px / 10% / 2em / ...
  if (config.right)  out.paddingRight  = toCssLength(config.right);   // ...
  if (config.bottom) out.paddingBottom = toCssLength(config.bottom);  // ...
  if (config.left)   out.paddingLeft   = toCssLength(config.left);    // ...
  // Logical sides — CSS resolves these against writing-mode/direction at layout time.
  if (config.blockStart)  out.paddingBlockStart  = toCssLength(config.blockStart);
  if (config.blockEnd)    out.paddingBlockEnd    = toCssLength(config.blockEnd);
  if (config.inlineStart) out.paddingInlineStart = toCssLength(config.inlineStart);
  if (config.inlineEnd)   out.paddingInlineEnd   = toCssLength(config.inlineEnd);
  return out;
}
