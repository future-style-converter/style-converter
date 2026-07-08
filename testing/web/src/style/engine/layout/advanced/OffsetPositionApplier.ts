// OffsetPositionApplier.ts — emits `offset-position`.  Native CSS Motion Path.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-position.

import type { CSSProperties } from 'react';
import type { OffsetPositionConfig } from './OffsetPositionConfig';

export type OffsetPositionStyles = Pick<CSSProperties, 'offsetPosition'>;

export function applyOffsetPosition(config: OffsetPositionConfig): OffsetPositionStyles {
  if (config.value === undefined) return {};
  return { offsetPosition: config.value } as OffsetPositionStyles;
}
