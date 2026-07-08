// AlignContentApplier.ts — emits a CSS declaration for the `alignContent` property.
// Standard property — maps 1:1 to `CSSProperties['alignContent']`.  Spec: CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-content.

import type { CSSProperties } from 'react';
import type { AlignContentConfig } from './AlignContentConfig';

export type AlignContentStyles = Pick<CSSProperties, 'alignContent'>;

export function applyAlignContent(config: AlignContentConfig): AlignContentStyles {
  if (config.value === undefined) return {};                         // unset
  return { alignContent: config.value } as AlignContentStyles;                    // typed single-key
}
