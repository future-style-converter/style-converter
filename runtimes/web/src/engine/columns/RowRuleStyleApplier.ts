// RowRuleStyleApplier.ts — emits { rowRuleStyle }; csstype-widened.
// csstype has no `rowRuleStyle` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `row-rule-style`.
import type { CSSProperties } from 'react';
import type { RowRuleStyleConfig } from './RowRuleStyleConfig';
export function applyRowRuleStyle(c: RowRuleStyleConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ rowRuleStyle: c.value } as unknown as CSSProperties) as Record<string, string>;
}
