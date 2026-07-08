// OpacityApplier.ts — turns an OpacityConfig into React inline-style { opacity }.

import type { CSSProperties } from 'react';
import type { OpacityConfig } from './OpacityConfig';

// Restricted partial — we only ever populate `opacity`.
export type OpacityStyles = Pick<CSSProperties, 'opacity'>;

// Pure emitter — omits `opacity` entirely when not set (avoids overriding 1).
export function applyOpacity(config: OpacityConfig): OpacityStyles {
  const out: OpacityStyles = {};                                      // blank accumulator
  if (config.alpha !== undefined) {                                   // only emit when set
    out.opacity = config.alpha;                                       // React accepts 0..1 numeric
  }
  return out;
}
