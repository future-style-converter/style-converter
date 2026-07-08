// BlockStepRoundApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/block-step-round.
import type { CSSProperties } from 'react';
import type { BlockStepRoundConfig } from './BlockStepRoundConfig';
export function applyBlockStepRound(c: BlockStepRoundConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ blockStepRound: c.value } as unknown as CSSProperties) as Record<string, string>;
}
