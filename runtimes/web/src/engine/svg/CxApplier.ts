// CxApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/cx.
import type { CSSProperties } from 'react';
import type { CxConfig } from './CxConfig';
export function applyCx(c: CxConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ cx: c.value } as unknown as CSSProperties) as Record<string, string>;
}
