// SizeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/size.
import type { CSSProperties } from 'react';
import type { SizeConfig } from './SizeConfig';
export function applySize(c: SizeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ size: c.value } as unknown as CSSProperties) as Record<string, string>;
}
