// OverflowYApplier.ts — native CSS `overflow-y`.
import type { CSSProperties } from 'react';
import type { OverflowYConfig } from './OverflowYConfig';

export type OverflowYStyles = Pick<CSSProperties, 'overflowY'>;

export function applyOverflowY(config: OverflowYConfig): OverflowYStyles {
  if (config.value === undefined) return {};
  return { overflowY: config.value } as OverflowYStyles;
}
