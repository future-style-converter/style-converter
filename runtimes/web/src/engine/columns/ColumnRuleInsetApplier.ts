// ColumnRuleInsetApplier.ts — emits { columnRuleInset }; csstype-widened.
// csstype has no `columnRuleInset` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `column-rule-inset`.
import type { CSSProperties } from 'react';
import type { ColumnRuleInsetConfig } from './ColumnRuleInsetConfig';
export function applyColumnRuleInset(c: ColumnRuleInsetConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ columnRuleInset: c.value } as unknown as CSSProperties) as Record<string, string>;
}
