// RowRuleBreakApplier.ts — emits { rowRuleBreak }; csstype-widened.
// csstype has no `rowRuleBreak` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `row-rule-break`.
import type { CSSProperties } from 'react';
import type { RowRuleBreakConfig } from './RowRuleBreakConfig';
export function applyRowRuleBreak(c: RowRuleBreakConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ rowRuleBreak: c.value } as unknown as CSSProperties) as Record<string, string>;
}
