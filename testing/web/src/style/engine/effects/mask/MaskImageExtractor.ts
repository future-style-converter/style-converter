// MaskImageExtractor.ts — reuses the BackgroundImage layer serialiser because
// mask-image has byte-identical CSS grammar (Masking L1 §4.1 defers to
// <image>). Bare string 'mask.png' in the IR is treated as an unquoted url().
import { foldLast, type IRPropertyLike } from '../_shared';
import { layerCss } from '../../background/BackgroundImageExtractor';               // shared serialiser
import type { MaskImageConfig } from './MaskImageConfig';
import { MASK_IMAGE_PROPERTY_TYPE } from './MaskImageConfig';

// Treat bare filename strings as url() references — MaskImage fixtures emit
// 'mask.png' literally, which isn't legal CSS on its own.
function wrapBareString(s: string): string | null {
  if (s === 'none') return 'none';                                                  // sentinel
  return `url("${s}")`;                                                              // wrap bare filename
}

function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                        // absent
  const arr = Array.isArray(data) ? data : [data];                                  // normalise
  if (arr.length === 0) return 'none';                                              // empty list fallback
  const tokens: string[] = [];                                                       // accumulator
  for (const entry of arr) {                                                         // per-layer
    if (typeof entry === 'string') {                                                 // bare string path
      const w = wrapBareString(entry);
      if (w) tokens.push(w);
      continue;
    }
    const css = layerCss(entry);                                                     // delegate to shared
    if (css !== null) tokens.push(css);
  }
  return tokens.length ? tokens.join(', ') : 'none';                                 // CSS comma-join
}

export function extractMaskImage(properties: IRPropertyLike[]): MaskImageConfig {
  return { value: foldLast(properties, MASK_IMAGE_PROPERTY_TYPE, parseOne) };
}
