// BackgroundSizeApplier.ts — serialises BackgroundSizeConfig to { backgroundSize }.

import type { CSSProperties } from 'react';
import type { BackgroundSizeConfig } from './BackgroundSizeConfig';

// Partial CSSProperties scoped to the one populated field.
export type BackgroundSizeStyles = Pick<CSSProperties, 'backgroundSize'>;

// Pure emitter — comma-joins per-layer CSS fragments.
export function applyBackgroundSize(config: BackgroundSizeConfig): BackgroundSizeStyles {
  const out: BackgroundSizeStyles = {};                               // blank accumulator
  if (config.layers.length === 0) return out;                         // unset -> emit nothing
  out.backgroundSize = config.layers.map((l) => l.css).join(', ');    // multi-layer CSS value
  return out;
}
