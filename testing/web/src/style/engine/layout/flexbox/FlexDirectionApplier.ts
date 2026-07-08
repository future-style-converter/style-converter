// FlexDirectionApplier.ts — emits a CSS declaration for the `flexDirection` property.
// Standard property — maps 1:1 to `CSSProperties['flexDirection']`.  Spec: CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-direction.

import type { CSSProperties } from 'react';
import type { FlexDirectionConfig } from './FlexDirectionConfig';

export type FlexDirectionStyles = Pick<CSSProperties, 'flexDirection'>;

export function applyFlexDirection(config: FlexDirectionConfig): FlexDirectionStyles {
  if (config.value === undefined) return {};                         // unset
  return { flexDirection: config.value } as FlexDirectionStyles;                    // typed single-key
}
