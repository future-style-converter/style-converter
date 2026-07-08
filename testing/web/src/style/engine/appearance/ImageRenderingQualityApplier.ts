// ImageRenderingQualityApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/image-rendering-quality.
import type { CSSProperties } from 'react';
import type { ImageRenderingQualityConfig } from './ImageRenderingQualityConfig';
export function applyImageRenderingQuality(c: ImageRenderingQualityConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ imageRenderingQuality: c.value } as unknown as CSSProperties) as Record<string, string>;
}
