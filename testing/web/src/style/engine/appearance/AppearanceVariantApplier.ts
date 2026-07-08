// AppearanceVariantApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/appearance-variant.
import type { CSSProperties } from 'react';
import type { AppearanceVariantConfig } from './AppearanceVariantConfig';
export function applyAppearanceVariant(c: AppearanceVariantConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ appearanceVariant: c.value } as unknown as CSSProperties) as Record<string, string>;
}
