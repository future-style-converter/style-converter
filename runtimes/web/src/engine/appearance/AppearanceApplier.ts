// AppearanceApplier.ts — emits { appearance }.  MDN: appearance.
import type { CSSProperties } from 'react';
import type { AppearanceConfig } from './AppearanceConfig';
export function applyAppearance(c: AppearanceConfig): CSSProperties {
  return c.value === undefined ? {} : { appearance: c.value } as CSSProperties;
}
