// OffsetPathApplier.ts — emits `offset-path`.  Native CSS Motion Path 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-path.

import type { CSSProperties } from 'react';
import type { OffsetPathConfig } from './OffsetPathConfig';

export type OffsetPathStyles = Pick<CSSProperties, 'offsetPath'>;

export function applyOffsetPath(config: OffsetPathConfig): OffsetPathStyles {
  if (config.value === undefined) return {};
  return { offsetPath: config.value } as OffsetPathStyles;
}
