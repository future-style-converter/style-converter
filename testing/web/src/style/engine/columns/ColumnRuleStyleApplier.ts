// ColumnRuleStyleApplier.ts — emits { columnRuleStyle }.  MDN: column-rule-style.
import type { CSSProperties } from 'react';
import type { ColumnRuleStyleConfig } from './ColumnRuleStyleConfig';
export function applyColumnRuleStyle(c: ColumnRuleStyleConfig): CSSProperties {
  return c.value === undefined ? {} : { columnRuleStyle: c.value } as CSSProperties;
}
