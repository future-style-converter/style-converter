// RowRuleColorApplier.ts — emits { rowRuleColor }; csstype-widened.
// csstype has no `rowRuleColor` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `row-rule-color`.
import type { CSSProperties } from 'react';
import type { RowRuleColorConfig } from './RowRuleColorConfig';
export function applyRowRuleColor(c: RowRuleColorConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ rowRuleColor: c.value } as unknown as CSSProperties) as Record<string, string>;
}
