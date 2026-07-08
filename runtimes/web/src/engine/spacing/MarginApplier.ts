// MarginApplier.ts — MarginConfig → React inline-style partial.
// Like PaddingApplier, but must preserve `auto` (toCssLength handles this).
// We intentionally keep logical sides separate so CSS handles RTL resolution.

import type { CSSProperties } from 'react';
import { toCssLength } from '../core/types/LengthValue';
import type { MarginConfig } from './MarginConfig';

// Public output type.
export type MarginStyles = Pick<
  CSSProperties,
  | 'marginTop' | 'marginRight' | 'marginBottom' | 'marginLeft'
  | 'marginBlockStart' | 'marginBlockEnd'
  | 'marginInlineStart' | 'marginInlineEnd'
>;

// Pure function — no side effects; called once per component.
export function applyMargin(config: MarginConfig): MarginStyles {
  const out: MarginStyles = {};                                       // blank accumulator
  // Physical sides — emit only if set.  toCssLength serialises 'auto' too.
  if (config.top)    out.marginTop    = toCssLength(config.top);      // e.g. "10px" or "auto"
  if (config.right)  out.marginRight  = toCssLength(config.right);    // ...
  if (config.bottom) out.marginBottom = toCssLength(config.bottom);   // ...
  if (config.left)   out.marginLeft   = toCssLength(config.left);     // ...
  // Logical sides — CSS resolves against writing-mode/direction at runtime.
  if (config.blockStart)  out.marginBlockStart  = toCssLength(config.blockStart);
  if (config.blockEnd)    out.marginBlockEnd    = toCssLength(config.blockEnd);
  if (config.inlineStart) out.marginInlineStart = toCssLength(config.inlineStart);
  if (config.inlineEnd)   out.marginInlineEnd   = toCssLength(config.inlineEnd);
  return out;
}
