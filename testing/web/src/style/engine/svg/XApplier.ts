// XApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/x.
import type { CSSProperties } from 'react';
import type { XConfig } from './XConfig';
export function applyX(c: XConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ x: c.value } as unknown as CSSProperties) as Record<string, string>;
}
