// OverflowXApplier.ts — native CSS `overflow-x`.
import type { CSSProperties } from 'react';
import type { OverflowXConfig } from './OverflowXConfig';

export type OverflowXStyles = Pick<CSSProperties, 'overflowX'>;

export function applyOverflowX(config: OverflowXConfig): OverflowXStyles {
  if (config.value === undefined) return {};
  return { overflowX: config.value } as OverflowXStyles;
}
