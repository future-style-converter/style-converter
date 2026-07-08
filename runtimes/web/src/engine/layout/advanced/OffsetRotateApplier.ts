// OffsetRotateApplier.ts — emits `offset-rotate`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-rotate.

import type { CSSProperties } from 'react';
import type { OffsetRotateConfig } from './OffsetRotateConfig';

export type OffsetRotateStyles = Pick<CSSProperties, 'offsetRotate'>;

export function applyOffsetRotate(config: OffsetRotateConfig): OffsetRotateStyles {
  if (config.value === undefined) return {};
  return { offsetRotate: config.value } as OffsetRotateStyles;
}
