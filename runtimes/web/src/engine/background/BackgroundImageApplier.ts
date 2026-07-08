// BackgroundImageApplier.ts — serialises a BackgroundImageConfig to
// { backgroundImage }.  Layers are comma-joined.

import type { CSSProperties } from 'react';
import type { BackgroundImageConfig } from './BackgroundImageConfig';

// Restricted partial CSSProperties scoped to the one field populated.
export type BackgroundImageStyles = Pick<CSSProperties, 'backgroundImage'>;

// Pure emitter — omits the field entirely when no layers were extracted.
export function applyBackgroundImage(config: BackgroundImageConfig): BackgroundImageStyles {
  const out: BackgroundImageStyles = {};                              // blank accumulator
  if (config.layers.length === 0) return out;                         // nothing to emit
  out.backgroundImage = config.layers.map((l) => l.css).join(', ');   // comma-join layers
  return out;
}
