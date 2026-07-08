// StrokeMiterlimitApplier.ts — emits { strokeMiterlimit }.  MDN: stroke-miterlimit.
import type { CSSProperties } from 'react';
import type { StrokeMiterlimitConfig } from './StrokeMiterlimitConfig';
export function applyStrokeMiterlimit(c: StrokeMiterlimitConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeMiterlimit: c.value } as CSSProperties;
}
