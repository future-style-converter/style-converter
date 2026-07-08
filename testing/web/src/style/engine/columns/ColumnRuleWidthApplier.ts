// ColumnRuleWidthApplier.ts — emits { columnRuleWidth }.  MDN: column-rule-width.
import type { CSSProperties } from 'react';
import type { ColumnRuleWidthConfig } from './ColumnRuleWidthConfig';
export function applyColumnRuleWidth(c: ColumnRuleWidthConfig): CSSProperties {
  return c.value === undefined ? {} : { columnRuleWidth: c.value } as CSSProperties;
}
