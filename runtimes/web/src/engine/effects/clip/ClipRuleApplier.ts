// ClipRuleApplier.ts — native SVG-presentation attribute; csstype has it.
import type { CSSProperties } from 'react';
import type { ClipRuleConfig } from './ClipRuleConfig';

export type ClipRuleStyles = Pick<CSSProperties, 'clipRule'>;

export function applyClipRule(config: ClipRuleConfig): ClipRuleStyles {
  if (config.value === undefined) return {};
  return { clipRule: config.value } as ClipRuleStyles;
}
