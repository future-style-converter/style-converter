// DApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/d.
import type { CSSProperties } from 'react';
import type { DConfig } from './DConfig';
export function applyD(c: DConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ d: c.value } as unknown as CSSProperties) as Record<string, string>;
}
