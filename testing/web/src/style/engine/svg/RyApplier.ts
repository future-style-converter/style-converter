// RyApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/ry.
import type { CSSProperties } from 'react';
import type { RyConfig } from './RyConfig';
export function applyRy(c: RyConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ ry: c.value } as unknown as CSSProperties) as Record<string, string>;
}
