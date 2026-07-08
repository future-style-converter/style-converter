// NavRightApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/nav-right.
import type { CSSProperties } from 'react';
import type { NavRightConfig } from './NavRightConfig';
export function applyNavRight(c: NavRightConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ navRight: c.value } as unknown as CSSProperties) as Record<string, string>;
}
