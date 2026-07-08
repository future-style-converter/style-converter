// YApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/y.
import type { CSSProperties } from 'react';
import type { YConfig } from './YConfig';
export function applyY(c: YConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ y: c.value } as unknown as CSSProperties) as Record<string, string>;
}
