// FillRuleApplier.ts — emits { fillRule }.  MDN: fill-rule.
import type { CSSProperties } from 'react';
import type { FillRuleConfig } from './FillRuleConfig';
export function applyFillRule(c: FillRuleConfig): CSSProperties {
  return c.value === undefined ? {} : { fillRule: c.value } as CSSProperties;
}
