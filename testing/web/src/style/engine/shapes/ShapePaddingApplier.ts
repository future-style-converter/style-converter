// ShapePaddingApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/shape-padding.
import type { CSSProperties } from 'react';
import type { ShapePaddingConfig } from './ShapePaddingConfig';
export function applyShapePadding(c: ShapePaddingConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ shapePadding: c.value } as unknown as CSSProperties) as Record<string, string>;
}
