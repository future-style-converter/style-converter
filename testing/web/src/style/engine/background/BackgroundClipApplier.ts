// BackgroundClipApplier.ts — emits { backgroundClip } from config.
// Note: for the 'text' value many browsers additionally require
// `-webkit-background-clip: text` when clipping gradients to glyphs; React's
// CSSProperties type doesn't include that vendor prefix, so callers that rely
// on text-clip effects can add the vendor fallback at the component level.

import type { CSSProperties } from 'react';
import type { BackgroundClipConfig } from './BackgroundClipConfig';

// Scoped partial.
export type BackgroundClipStyles = Pick<CSSProperties, 'backgroundClip'>;

// Pure emitter — comma-joins per-layer tokens.
export function applyBackgroundClip(config: BackgroundClipConfig): BackgroundClipStyles {
  const out: BackgroundClipStyles = {};                               // blank accumulator
  if (config.layers.length === 0) return out;                         // unset -> emit nothing
  out.backgroundClip = config.layers.map((l) => l.css).join(', ');    // multi-layer CSS value
  return out;
}
