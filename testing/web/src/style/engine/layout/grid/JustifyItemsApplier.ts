// JustifyItemsApplier.ts — emits a CSS declaration for the `justifyItems` property.
// Standard property — maps 1:1 to `CSSProperties['justifyItems']`.  Spec: CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/justify-items.

import type { CSSProperties } from 'react';
import type { JustifyItemsConfig } from './JustifyItemsConfig';

export type JustifyItemsStyles = Pick<CSSProperties, 'justifyItems'>;

export function applyJustifyItems(config: JustifyItemsConfig): JustifyItemsStyles {
  if (config.value === undefined) return {};                         // unset
  return { justifyItems: config.value } as JustifyItemsStyles;                    // typed single-key
}
