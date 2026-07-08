// ImageOrientationApplier.ts — emits { imageOrientation }.  MDN: image-orientation.
import type { CSSProperties } from 'react';
import type { ImageOrientationConfig } from './ImageOrientationConfig';
export function applyImageOrientation(c: ImageOrientationConfig): CSSProperties {
  return c.value === undefined ? {} : { imageOrientation: c.value } as CSSProperties;
}
