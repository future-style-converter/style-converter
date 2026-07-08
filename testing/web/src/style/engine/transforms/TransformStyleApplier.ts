// TransformStyleApplier.ts — native key.
import type { CSSProperties } from 'react';
import type { TransformStyleConfig } from './TransformStyleConfig';

export type TransformStyleStyles = Pick<CSSProperties, 'transformStyle'>;

export function applyTransformStyle(config: TransformStyleConfig): TransformStyleStyles {
  if (config.value === undefined) return {};
  return { transformStyle: config.value } as TransformStyleStyles;
}
