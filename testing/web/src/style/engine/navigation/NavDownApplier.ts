// NavDownApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/nav-down.
import type { CSSProperties } from 'react';
import type { NavDownConfig } from './NavDownConfig';
export function applyNavDown(c: NavDownConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ navDown: c.value } as unknown as CSSProperties) as Record<string, string>;
}
