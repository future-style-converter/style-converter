// WrapAfterApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/wrap-after.
import type { CSSProperties } from 'react';
import type { WrapAfterConfig } from './WrapAfterConfig';
export function applyWrapAfter(c: WrapAfterConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ wrapAfter: c.value } as unknown as CSSProperties) as Record<string, string>;
}
