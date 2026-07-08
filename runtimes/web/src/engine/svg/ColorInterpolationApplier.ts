// ColorInterpolationApplier.ts — emits { colorInterpolation }.  MDN: color-interpolation.
import type { CSSProperties } from 'react';
import type { ColorInterpolationConfig } from './ColorInterpolationConfig';
export function applyColorInterpolation(c: ColorInterpolationConfig): CSSProperties {
  return c.value === undefined ? {} : { colorInterpolation: c.value } as CSSProperties;
}
