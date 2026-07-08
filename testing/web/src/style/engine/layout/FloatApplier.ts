// FloatApplier.ts — emits a CSS declaration for the `float` property.
// Standard property — maps 1:1 to `CSSProperties['float']`.  Spec: CSS 2 — https://developer.mozilla.org/docs/Web/CSS/float.

import type { CSSProperties } from 'react';
import type { FloatConfig } from './FloatConfig';

export type FloatStyles = Pick<CSSProperties, 'float'>;

export function applyFloat(config: FloatConfig): FloatStyles {
  if (config.value === undefined) return {};                         // unset
  return { float: config.value } as FloatStyles;                    // typed single-key
}
