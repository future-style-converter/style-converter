// NavUpApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/nav-up.
import type { CSSProperties } from 'react';
import type { NavUpConfig } from './NavUpConfig';
export function applyNavUp(c: NavUpConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ navUp: c.value } as unknown as CSSProperties) as Record<string, string>;
}
