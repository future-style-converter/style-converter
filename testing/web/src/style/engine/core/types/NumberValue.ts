// NumberValue.ts - Property-specific numeric extractors.
// Handles shapes documented in examples/primitives/numbers.json:
//   Opacity      -> { alpha: 0.5, ... }
//   LineHeight   -> { multiplier: 1.5, ... }    (unitless form only)
//   FlexGrow     -> { normalizedValue: 1.0, ... }
//   ZIndex       -> { value: 10, ... }
//   FontWeight   -> bare integer
//   FontSize     -> { px: 16, original: {...} }  (always a length, delegated)
// Quirk #8: Each numeric property uses its OWN envelope key — there is no universal "number" shape.

import { extractLength, type LengthValue } from './LengthValue';    // fontSize delegates here

// Uniform wrapper so callers can do `extractors.foo(data)?.value`.
export interface NumberValue { value: number }

// Read a specific numeric key out of an object, returning null if missing/wrong type.
function readKey(data: unknown, key: string): NumberValue | null {
  if (data === null || data === undefined) return null;
  if (typeof data !== 'object') return null;
  const v = (data as Record<string, unknown>)[key];
  return typeof v === 'number' ? { value: v } : null;
}

// Opacity: { alpha: 0.5 } — IRProperty Opacity exposes the float under 'alpha'.
function opacity(data: unknown): NumberValue | null {
  // Bare number tolerated for forward-compat; otherwise look up 'alpha'.
  if (typeof data === 'number') return { value: data };
  return readKey(data, 'alpha');
}

// LineHeight (unitless multiplier form): { multiplier: 1.5 }.
// NOTE: Length-based line-heights (e.g. "24px") use LengthValue instead — caller chooses.
function lineHeightMultiplier(data: unknown): NumberValue | null {
  if (typeof data === 'number') return { value: data };             // bare-number form seen in some IR dumps
  return readKey(data, 'multiplier');
}

// FlexGrow: { normalizedValue: 1.0 } — IRFlexGrow serialises the clamped float here.
function flexGrow(data: unknown): NumberValue | null {
  if (typeof data === 'number') return { value: data };
  return readKey(data, 'normalizedValue');
}

// ZIndex: { value: 10 } — IRZIndex stores the integer under 'value'.
function zIndex(data: unknown): NumberValue | null {
  if (typeof data === 'number') return { value: Math.round(data) };
  const raw = readKey(data, 'value');
  return raw ? { value: Math.round(raw.value) } : null;             // z-index is integer-valued
}

// FontWeight: bare integer per fixtures, but some CSS->IR paths wrap it in { weight: N } or { value: N }.
function fontWeight(data: unknown): NumberValue | null {
  if (typeof data === 'number') return { value: data };             // documented bare-int form
  // Defensive fallbacks for alternate shapes observed elsewhere in the IR.
  return readKey(data, 'weight') ?? readKey(data, 'value');
}

// FontSize: IR always wraps a length ({ px, original? }) — delegate straight into extractLength.
// Returns a LengthValue (not a NumberValue) because the unit matters for rendering.
function fontSize(data: unknown): LengthValue {
  return extractLength(data);
}

// Bundled adapter namespace so StyleBuilder can write `NumberExtractors.opacity(data)`.
export const NumberExtractors = {
  opacity,
  lineHeightMultiplier,
  flexGrow,
  zIndex,
  fontWeight,
  fontSize,
} as const;
