// TransformOriginApplier.ts — native key, csstype recognised.

import type { CSSProperties } from 'react';
import type { TransformOriginConfig } from './TransformOriginConfig';

export type TransformOriginStyles = Pick<CSSProperties, 'transformOrigin'>;

export function applyTransformOrigin(config: TransformOriginConfig): TransformOriginStyles {
  if (config.value === undefined) return {};                                       // absent
  return { transformOrigin: config.value };                                         // native CSS
}
