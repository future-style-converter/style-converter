// FlexWrapApplier.ts — emits a CSS declaration for the `flexWrap` property.
// Standard property — maps 1:1 to `CSSProperties['flexWrap']`.  Spec: CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-wrap.

import type { CSSProperties } from 'react';
import type { FlexWrapConfig } from './FlexWrapConfig';

export type FlexWrapStyles = Pick<CSSProperties, 'flexWrap'>;

export function applyFlexWrap(config: FlexWrapConfig): FlexWrapStyles {
  if (config.value === undefined) return {};                         // unset
  return { flexWrap: config.value } as FlexWrapStyles;                    // typed single-key
}
