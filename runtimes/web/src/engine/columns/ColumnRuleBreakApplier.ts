// ColumnRuleBreakApplier.ts — emits { columnRuleBreak }; csstype-widened.
// csstype has no `columnRuleBreak` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `column-rule-break`.
import type { CSSProperties } from 'react';
import type { ColumnRuleBreakConfig } from './ColumnRuleBreakConfig';
export function applyColumnRuleBreak(c: ColumnRuleBreakConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ columnRuleBreak: c.value } as unknown as CSSProperties) as Record<string, string>;
}
