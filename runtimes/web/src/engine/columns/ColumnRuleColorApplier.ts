// ColumnRuleColorApplier.ts — emits { columnRuleColor }.  MDN: column-rule-color.
import type { CSSProperties } from 'react';
import type { ColumnRuleColorConfig } from './ColumnRuleColorConfig';
export function applyColumnRuleColor(c: ColumnRuleColorConfig): CSSProperties {
  return c.value === undefined ? {} : { columnRuleColor: c.value } as CSSProperties;
}
