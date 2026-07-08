// FieldSizingApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/field-sizing.
import type { CSSProperties } from 'react';
import type { FieldSizingConfig } from './FieldSizingConfig';
export function applyFieldSizing(c: FieldSizingConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ fieldSizing: c.value } as unknown as CSSProperties) as Record<string, string>;
}
