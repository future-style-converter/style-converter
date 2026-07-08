// BackgroundRepeatApplier.ts — emits { backgroundRepeat } from config.

import type { CSSProperties } from 'react';
import type { BackgroundRepeatConfig } from './BackgroundRepeatConfig';

// Scoped partial.
export type BackgroundRepeatStyles = Pick<CSSProperties, 'backgroundRepeat'>;

// Pure emitter — comma-joins per-layer fragments.
export function applyBackgroundRepeat(config: BackgroundRepeatConfig): BackgroundRepeatStyles {
  const out: BackgroundRepeatStyles = {};                             // blank accumulator
  if (config.layers.length === 0) return out;                         // unset -> emit nothing
  out.backgroundRepeat = config.layers.map((l) => l.css).join(', ');  // multi-layer CSS value
  return out;
}
