// JustifyContentApplier.ts — emits a CSS declaration for the `justifyContent` property.
// Standard property — maps 1:1 to `CSSProperties['justifyContent']`.  Spec: CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/justify-content.

import type { CSSProperties } from 'react';
import type { JustifyContentConfig } from './JustifyContentConfig';

export type JustifyContentStyles = Pick<CSSProperties, 'justifyContent'>;

export function applyJustifyContent(config: JustifyContentConfig): JustifyContentStyles {
  if (config.value === undefined) return {};                         // unset
  return { justifyContent: config.value } as JustifyContentStyles;                    // typed single-key
}
