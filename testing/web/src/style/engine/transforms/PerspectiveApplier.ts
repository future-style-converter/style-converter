// PerspectiveApplier.ts — native CSS key.
import type { CSSProperties } from 'react';
import type { PerspectiveConfig } from './PerspectiveConfig';

export type PerspectiveStyles = Pick<CSSProperties, 'perspective'>;

export function applyPerspective(config: PerspectiveConfig): PerspectiveStyles {
  if (config.value === undefined) return {};
  return { perspective: config.value };
}
