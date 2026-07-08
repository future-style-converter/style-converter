// ColorRenderingApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/color-rendering.
import type { CSSProperties } from 'react';
import type { ColorRenderingConfig } from './ColorRenderingConfig';
export function applyColorRendering(c: ColorRenderingConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ colorRendering: c.value } as unknown as CSSProperties) as Record<string, string>;
}
