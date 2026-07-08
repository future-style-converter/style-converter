// AlignItemsApplier.ts — emits a CSS declaration for the `alignItems` property.
// Standard property — maps 1:1 to `CSSProperties['alignItems']`.  Spec: CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-items.

import type { CSSProperties } from 'react';
import type { AlignItemsConfig } from './AlignItemsConfig';

export type AlignItemsStyles = Pick<CSSProperties, 'alignItems'>;

export function applyAlignItems(config: AlignItemsConfig): AlignItemsStyles {
  if (config.value === undefined) return {};                         // unset
  return { alignItems: config.value } as AlignItemsStyles;                    // typed single-key
}
