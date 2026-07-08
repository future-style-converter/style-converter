// VisibilityApplier.ts — native key.
import type { CSSProperties } from 'react';
import type { VisibilityConfig } from './VisibilityConfig';

export type VisibilityStyles = Pick<CSSProperties, 'visibility'>;

export function applyVisibility(config: VisibilityConfig): VisibilityStyles {
  if (config.value === undefined) return {};
  return { visibility: config.value } as VisibilityStyles;
}
