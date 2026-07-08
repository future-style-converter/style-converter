// BackfaceVisibilityApplier.ts — native key.
import type { CSSProperties } from 'react';
import type { BackfaceVisibilityConfig } from './BackfaceVisibilityConfig';

export type BackfaceVisibilityStyles = Pick<CSSProperties, 'backfaceVisibility'>;

export function applyBackfaceVisibility(config: BackfaceVisibilityConfig): BackfaceVisibilityStyles {
  if (config.value === undefined) return {};
  return { backfaceVisibility: config.value } as BackfaceVisibilityStyles;
}
