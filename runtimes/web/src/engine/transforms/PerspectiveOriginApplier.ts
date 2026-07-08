// PerspectiveOriginApplier.ts — native CSS key.
import type { CSSProperties } from 'react';
import type { PerspectiveOriginConfig } from './PerspectiveOriginConfig';

export type PerspectiveOriginStyles = Pick<CSSProperties, 'perspectiveOrigin'>;

export function applyPerspectiveOrigin(config: PerspectiveOriginConfig): PerspectiveOriginStyles {
  if (config.value === undefined) return {};
  return { perspectiveOrigin: config.value };
}
