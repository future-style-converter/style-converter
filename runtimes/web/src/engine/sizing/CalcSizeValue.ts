// CalcSizeValue.ts — css-values-5 §11 `calc-size()` typed wire decode.
//
// Wave 42 (lane W3). The converter now types the affine calc-size() family
// (CalcSizeParser.kt) instead of letting it ride the Generic envelope:
//   {"type":"calc-size","basis":"auto","factor":1,"offsetPx":20,
//    "original":"calc-size(auto, size + 20px)"}
// Before this decoder existed, that typed value would hit extractLength's
// unknown fall-through and the declaration would be DROPPED on web — while
// the whole point of the web runtime here is to hand the declaration to the
// browser, which implements calc-size() natively (Chromium 129+). The 15
// css-values/calc-size WPT cells that pass today pass BECAUSE the raw string
// reaches the browser; this module keeps that contract for the typed wire.
//
// The `original` field is the verbatim trimmed declaration the author wrote,
// carried specifically so this runtime can replay it byte-for-byte (the same
// fidelity the Generic envelope provided). Reconstruction from the numeric
// triple exists only as a fallback for hand-written IR that omits `original`.

// The typed calc-size payload — mirrors the converter's wire field-for-field.
export interface CalcSizeValue {
  basis: string;      // auto | min-content | max-content | fit-content | stretch | content
  factor: number;     // multiplier on the resolved basis size (`size` keyword)
  offsetPx: number;   // absolute px addend (may be negative)
  original?: string;  // verbatim declaration for byte-exact browser replay
}

// Decode the wire shape, or null when `data` is not a typed calc-size value.
// Null routes the caller back to its existing behavior (extractLength), so
// this decoder is purely additive — no value that decodes today can change.
export function extractCalcSize(data: unknown): CalcSizeValue | null {
  // Only object payloads can carry the discriminated shape.
  if (data === null || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  // Discriminator check — the converter always stamps type:"calc-size".
  if (obj.type !== 'calc-size') return null;
  // Basis is required (the runtimes resolve `size` against it); factor and
  // offset default to the identity expression `size` so a partially-formed
  // object degrades to the basis itself rather than to garbage.
  if (typeof obj.basis !== 'string') return null;
  return {
    basis: obj.basis,
    factor: typeof obj.factor === 'number' ? obj.factor : 1,
    offsetPx: typeof obj.offsetPx === 'number' ? obj.offsetPx : 0,
    original: typeof obj.original === 'string' && obj.original.length > 0
      ? obj.original
      : undefined,
  };
}

// Serialise a CalcSizeValue back to a CSS declaration value. The verbatim
// `original` wins whenever present (byte-exact replay); otherwise we
// reconstruct a canonical, equivalent calc-size() call from the triple.
export function toCalcSizeCss(v: CalcSizeValue): string {
  // Byte-exact replay path — the normal case for converter-emitted IR.
  if (v.original) return v.original;
  // Reconstruction: `size` scaled by factor, then the signed px offset.
  // factor 1 emits bare `size` (canonical); other factors emit `size * F`
  // (CSS multiplication needs no whitespace, but we keep it readable).
  const sizeTerm = v.factor === 1 ? 'size' : `size * ${v.factor}`;
  // A zero offset emits just the size term; signs fold into the operator
  // (css-values-4 §10.9 requires whitespace around + and -).
  const expr = v.offsetPx === 0
    ? sizeTerm
    : v.offsetPx > 0
      ? `${sizeTerm} + ${v.offsetPx}px`
      : `${sizeTerm} - ${Math.abs(v.offsetPx)}px`;
  return `calc-size(${v.basis}, ${expr})`;
}
