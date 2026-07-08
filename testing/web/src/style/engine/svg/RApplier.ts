// RApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/r.
import type { CSSProperties } from 'react';
import type { RConfig } from './RConfig';
export function applyR(c: RConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ r: c.value } as unknown as CSSProperties) as Record<string, string>;
}
