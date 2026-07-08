// FlexBasisApplier.ts — emits `flex-basis`.  Native CSS.
// Spec: https://developer.mozilla.org/docs/Web/CSS/flex-basis.
// `content` keyword is Flexbox L1 and widely supported in modern engines —
// csstype types `flex-basis` as a broad union so no widening is needed here.

import type { CSSProperties } from 'react';
import type { FlexBasisConfig } from './FlexBasisConfig';

export type FlexBasisStyles = Pick<CSSProperties, 'flexBasis'>;

export function applyFlexBasis(config: FlexBasisConfig): FlexBasisStyles {
  if (config.value === undefined) return {};                                      // unset
  return { flexBasis: config.value } as FlexBasisStyles;                          // pass-through
}
