// GapApplier.ts — GapConfig → React inline-style partial.
// When both axes match, we emit the short `gap` form to keep the style object
// small; otherwise we emit the two longhands so mixed values survive.

import type { CSSProperties } from 'react';
import { toCssLength, type LengthValue } from '../core/types/LengthValue';
import type { GapConfig } from './GapConfig';

// Public output — `gap` may appear (shorthand) or the two longhands.
export type GapStyles = Pick<CSSProperties, 'gap' | 'rowGap' | 'columnGap'>;

// Structural equality for two LengthValues.  Cheap — values are tiny records.
function sameLength(a: LengthValue, b: LengthValue): boolean {
  if (a.kind !== b.kind) return false;                                // short-circuit on kind
  // Field-by-field compare per kind; ensures exhaustiveness via the switch.
  switch (a.kind) {
    case 'exact':     return a.px === (b as typeof a).px;
    case 'relative':  return a.value === (b as typeof a).value && a.unit === (b as typeof a).unit;
    case 'auto':      return true;                                    // no payload
    case 'none':      return true;                                    // no payload (Phase-3 variant)
    case 'intrinsic': return a.intrinsicKind === (b as typeof a).intrinsicKind;
    case 'fraction':  return a.fr === (b as typeof a).fr;
    case 'calc':      return a.expression === (b as typeof a).expression;
    case 'unknown':   return true;                                    // no payload
    default: {                                                        // compile-time guard
      const _never: never = a;
      return _never;
    }
  }
}

// Main applier — collapses to `gap` shorthand when row===column.
export function applyGap(config: GapConfig): GapStyles {
  const out: GapStyles = {};                                          // blank accumulator
  const { rowGap, columnGap } = config;                               // destructure for brevity
  // If both axes are present AND identical, emit the single-value shorthand.
  if (rowGap && columnGap && sameLength(rowGap, columnGap)) {
    out.gap = toCssLength(rowGap);                                    // one CSS property instead of two
    return out;
  }
  // Otherwise emit whichever axes are set.
  if (rowGap)    out.rowGap    = toCssLength(rowGap);                 // e.g. "10px"
  if (columnGap) out.columnGap = toCssLength(columnGap);              // e.g. "40px"
  return out;
}
