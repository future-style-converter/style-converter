// BorderSpacingApplier.ts — emits { borderSpacing }.  MDN: border-spacing.
import type { CSSProperties } from 'react';
import type { BorderSpacingConfig } from './BorderSpacingConfig';
export function applyBorderSpacing(c: BorderSpacingConfig): CSSProperties {
  return c.value === undefined ? {} : { borderSpacing: c.value } as CSSProperties;
}
