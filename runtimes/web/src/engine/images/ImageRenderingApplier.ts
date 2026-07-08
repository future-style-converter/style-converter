// ImageRenderingApplier.ts — emits { imageRendering }.  MDN: image-rendering.
import type { CSSProperties } from 'react';
import type { ImageRenderingConfig } from './ImageRenderingConfig';
export function applyImageRendering(c: ImageRenderingConfig): CSSProperties {
  return c.value === undefined ? {} : { imageRendering: c.value } as CSSProperties;
}
