// BlockStepSizeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/block-step-size.
import type { CSSProperties } from 'react';
import type { BlockStepSizeConfig } from './BlockStepSizeConfig';
export function applyBlockStepSize(c: BlockStepSizeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ blockStepSize: c.value } as unknown as CSSProperties) as Record<string, string>;
}
