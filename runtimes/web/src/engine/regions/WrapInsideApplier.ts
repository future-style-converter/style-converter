// WrapInsideApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/wrap-inside.
import type { CSSProperties } from 'react';
import type { WrapInsideConfig } from './WrapInsideConfig';
export function applyWrapInside(c: WrapInsideConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ wrapInside: c.value } as unknown as CSSProperties) as Record<string, string>;
}
