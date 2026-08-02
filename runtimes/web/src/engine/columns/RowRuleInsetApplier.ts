// RowRuleInsetApplier.ts — emits { rowRuleInset }; csstype-widened.
// csstype has no `rowRuleInset` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `row-rule-inset`.
import type { CSSProperties } from 'react';
import type { RowRuleInsetConfig } from './RowRuleInsetConfig';
export function applyRowRuleInset(c: RowRuleInsetConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ rowRuleInset: c.value } as unknown as CSSProperties) as Record<string, string>;
}
