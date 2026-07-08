// OffsetDistanceApplier.ts — emits `offset-distance`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-distance.

import type { CSSProperties } from 'react';
import type { OffsetDistanceConfig } from './OffsetDistanceConfig';

export type OffsetDistanceStyles = Pick<CSSProperties, 'offsetDistance'>;

export function applyOffsetDistance(config: OffsetDistanceConfig): OffsetDistanceStyles {
  if (config.value === undefined) return {};
  return { offsetDistance: config.value } as OffsetDistanceStyles;
}
