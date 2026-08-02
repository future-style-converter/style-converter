// RowRuleWidthApplier.ts — emits { rowRuleWidth }; csstype-widened.
// csstype has no `rowRuleWidth` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `row-rule-width`.
import type { CSSProperties } from 'react';
import type { RowRuleWidthConfig } from './RowRuleWidthConfig';
export function applyRowRuleWidth(c: RowRuleWidthConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ rowRuleWidth: c.value } as unknown as CSSProperties) as Record<string, string>;
}
