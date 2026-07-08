// BufferedRenderingApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/buffered-rendering.
import type { CSSProperties } from 'react';
import type { BufferedRenderingConfig } from './BufferedRenderingConfig';
export function applyBufferedRendering(c: BufferedRenderingConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bufferedRendering: c.value } as unknown as CSSProperties) as Record<string, string>;
}
