// TransformBoxApplier.ts — native key, csstype recognised.
import type { CSSProperties } from 'react';
import type { TransformBoxConfig } from './TransformBoxConfig';

export type TransformBoxStyles = Pick<CSSProperties, 'transformBox'>;

export function applyTransformBox(config: TransformBoxConfig): TransformBoxStyles {
  if (config.value === undefined) return {};
  return { transformBox: config.value } as TransformBoxStyles;                     // kebab keyword
}
