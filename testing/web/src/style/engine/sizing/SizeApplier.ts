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

  // AspectRatio — "auto" round-trips; a concrete ratio emits as a single
  // number (CSS accepts `aspect-ratio: 1.7777...`).
  if (cfg.aspectRatio) {
    out.aspectRatio = cfg.aspectRatio.isAuto
      ? 'auto'                                                    // keyword round-trip
      : String(cfg.aspectRatio.ratio);                            // concrete ratio
  }

  return out;
}
