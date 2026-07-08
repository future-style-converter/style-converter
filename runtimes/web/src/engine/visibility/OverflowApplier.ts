// OverflowApplier.ts — native CSS `overflow` key.
import type { CSSProperties } from 'react';
import type { OverflowConfig } from './OverflowConfig';

export type OverflowStyles = Pick<CSSProperties, 'overflow'>;

export function applyOverflow(config: OverflowConfig): OverflowStyles {
  if (config.value === undefined) return {};
  return { overflow: config.value } as OverflowStyles;
}
