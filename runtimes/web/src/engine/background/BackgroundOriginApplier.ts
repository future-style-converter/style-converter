// BackgroundOriginApplier.ts — emits { backgroundOrigin } from config.

import type { CSSProperties } from 'react';
import type { BackgroundOriginConfig } from './BackgroundOriginConfig';

// Scoped partial.
export type BackgroundOriginStyles = Pick<CSSProperties, 'backgroundOrigin'>;

// Pure emitter — comma-joins per-layer CSS fragments.
export function applyBackgroundOrigin(config: BackgroundOriginConfig): BackgroundOriginStyles {
  const out: BackgroundOriginStyles = {};                             // blank accumulator
  if (config.layers.length === 0) return out;                         // unset -> emit nothing
  out.backgroundOrigin = config.layers.map((l) => l.css).join(', ');  // multi-layer CSS value
  return out;
}
