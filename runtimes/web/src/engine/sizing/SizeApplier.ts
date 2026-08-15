// SizeApplier.ts — turns a SizeConfig into a partial React CSSProperties.
// Web is the easiest target: browsers natively resolve px / % / em / vw / calc
// as well as auto / min-content / max-content / fit-content / none, so our
// job is pure serialisation via toCssLength.
//
// Logical sides are emitted as their camelCase CSS keys directly so the
// browser still flips them correctly under `direction: rtl` or a vertical
// writing-mode.

import type { CSSProperties } from 'react';
import { toCssLength } from '../core/types/LengthValue';
// css-values-5 calc-size() re-emission (wave 42 lane W3).
import { toCalcSizeCss } from './CalcSizeValue';
import type { SizeConfig } from './SizeConfig';

// Output type — a slice of CSSProperties so callers can Object.assign it
// into the running style object without losing type-safety.
export type SizeStyles = Pick<
  CSSProperties,
  | 'width' | 'height' | 'minWidth' | 'maxWidth' | 'minHeight' | 'maxHeight'
  | 'blockSize' | 'inlineSize'
  | 'minBlockSize' | 'maxBlockSize' | 'minInlineSize' | 'maxInlineSize'
  | 'aspectRatio'
>;

// Pure function — no side effects, zero allocation beyond the result object.
export function applySize(cfg: SizeConfig): SizeStyles {
  const out: SizeStyles = {};                                   // blank accumulator

  // Physical sizing — emit only when the caller populated that side to keep
  // the resulting inline style object minimal.
  if (cfg.width)      out.width      = toCssLength(cfg.width);
  if (cfg.height)     out.height     = toCssLength(cfg.height);
  if (cfg.minWidth)   out.minWidth   = toCssLength(cfg.minWidth);
  if (cfg.maxWidth)   out.maxWidth   = toCssLength(cfg.maxWidth);   // 'none' maps to 'none'
  if (cfg.minHeight)  out.minHeight  = toCssLength(cfg.minHeight);
  if (cfg.maxHeight)  out.maxHeight  = toCssLength(cfg.maxHeight);

  // Logical sizing — CSS resolves these against writing-mode/direction, so
  // we pass them straight through as the matching camelCase keys.
  if (cfg.blockSize)      out.blockSize      = toCssLength(cfg.blockSize);
  if (cfg.inlineSize)     out.inlineSize     = toCssLength(cfg.inlineSize);
  if (cfg.minBlockSize)   out.minBlockSize   = toCssLength(cfg.minBlockSize);
  if (cfg.maxBlockSize)   out.maxBlockSize   = toCssLength(cfg.maxBlockSize);
  if (cfg.minInlineSize)  out.minInlineSize  = toCssLength(cfg.minInlineSize);
  if (cfg.maxInlineSize)  out.maxInlineSize  = toCssLength(cfg.maxInlineSize);

  // css-values-5 calc-size() (wave 42 lane W3) — re-emit each typed slot as
  // its verbatim `calc-size(...)` declaration (toCalcSizeCss prefers the
  // wire's `original` field byte-for-byte). The browser implements
  // calc-size() natively, so serialisation IS the implementation here —
  // exactly the contract the Generic envelope provided before the converter
  // typed the value. A slot never appears both here and above (the extractor
  // routes exclusively), so this cannot overwrite a length emission.
  if (cfg.calcSize) {
    if (cfg.calcSize.width)     out.width     = toCalcSizeCss(cfg.calcSize.width);
    if (cfg.calcSize.height)    out.height    = toCalcSizeCss(cfg.calcSize.height);
    if (cfg.calcSize.minWidth)  out.minWidth  = toCalcSizeCss(cfg.calcSize.minWidth);
    if (cfg.calcSize.maxWidth)  out.maxWidth  = toCalcSizeCss(cfg.calcSize.maxWidth);
    if (cfg.calcSize.minHeight) out.minHeight = toCalcSizeCss(cfg.calcSize.minHeight);
    if (cfg.calcSize.maxHeight) out.maxHeight = toCalcSizeCss(cfg.calcSize.maxHeight);
  }

  // AspectRatio — "auto" round-trips; a concrete ratio emits as a single
  // number (CSS accepts `aspect-ratio: 1.7777...`).
  if (cfg.aspectRatio) {
    out.aspectRatio = cfg.aspectRatio.isAuto
      ? 'auto'                                                    // keyword round-trip
      : String(cfg.aspectRatio.ratio);                            // concrete ratio
  }

  return out;
}
