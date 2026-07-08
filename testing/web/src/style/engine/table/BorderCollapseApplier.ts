// BorderCollapseApplier.ts — emits { borderCollapse }.  MDN: border-collapse.
import type { CSSProperties } from 'react';
import type { BorderCollapseConfig } from './BorderCollapseConfig';
export function applyBorderCollapse(c: BorderCollapseConfig): CSSProperties {
  return c.value === undefined ? {} : { borderCollapse: c.value } as CSSProperties;
}
