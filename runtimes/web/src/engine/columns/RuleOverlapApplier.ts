// RuleOverlapApplier.ts — emits { ruleOverlap }; csstype-widened.
// csstype has no `ruleOverlap` key yet (css-gaps-1 is a draft), so the object is
// cast the same way engine/rhythm/BlockStepAlignApplier.ts casts its L4 key.
// CssText.cssPropertyName() re-spells the camelCase key as `rule-overlap`.
import type { CSSProperties } from 'react';
import type { RuleOverlapConfig } from './RuleOverlapConfig';
export function applyRuleOverlap(c: RuleOverlapConfig): Record<string, string> {
  // Absent value → emit nothing, so the browser keeps the CSS initial value.
  if (c.value === undefined) return {};
  return ({ ruleOverlap: c.value } as unknown as CSSProperties) as Record<string, string>;
}
