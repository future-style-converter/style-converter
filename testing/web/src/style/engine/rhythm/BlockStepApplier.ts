// BlockStepApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/block-step.
import type { CSSProperties } from 'react';
import type { BlockStepConfig } from './BlockStepConfig';
export function applyBlockStep(c: BlockStepConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ blockStep: c.value } as unknown as CSSProperties) as Record<string, string>;
}
