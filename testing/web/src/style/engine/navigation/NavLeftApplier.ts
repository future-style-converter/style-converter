// NavLeftApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/nav-left.
import type { CSSProperties } from 'react';
import type { NavLeftConfig } from './NavLeftConfig';
export function applyNavLeft(c: NavLeftConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ navLeft: c.value } as unknown as CSSProperties) as Record<string, string>;
}
