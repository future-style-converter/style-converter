// WrapBeforeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/wrap-before.
import type { CSSProperties } from 'react';
import type { WrapBeforeConfig } from './WrapBeforeConfig';
export function applyWrapBefore(c: WrapBeforeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ wrapBefore: c.value } as unknown as CSSProperties) as Record<string, string>;
}
