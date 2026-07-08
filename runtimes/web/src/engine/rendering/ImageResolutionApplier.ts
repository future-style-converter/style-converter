// ImageResolutionApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/image-resolution.
import type { CSSProperties } from 'react';
import type { ImageResolutionConfig } from './ImageResolutionConfig';
export function applyImageResolution(c: ImageResolutionConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ imageResolution: c.value } as unknown as CSSProperties) as Record<string, string>;
}
