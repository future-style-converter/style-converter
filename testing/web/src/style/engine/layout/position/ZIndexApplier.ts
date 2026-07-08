// ZIndexApplier.ts — emits `z-index`.  Native CSS.
// Spec: https://developer.mozilla.org/docs/Web/CSS/z-index.

import type { CSSProperties } from 'react';
import type { ZIndexConfig } from './ZIndexConfig';

export type ZIndexStyles = Pick<CSSProperties, 'zIndex'>;

export function applyZIndex(config: ZIndexConfig): ZIndexStyles {
  if (config.value === undefined) return {};                                        // unset
  return { zIndex: config.value } as ZIndexStyles;                                  // number or 'auto'
}
