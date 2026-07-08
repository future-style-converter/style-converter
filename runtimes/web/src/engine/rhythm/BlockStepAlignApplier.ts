// BlockStepAlignApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/block-step-align.
import type { CSSProperties } from 'react';
import type { BlockStepAlignConfig } from './BlockStepAlignConfig';
export function applyBlockStepAlign(c: BlockStepAlignConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ blockStepAlign: c.value } as unknown as CSSProperties) as Record<string, string>;
}
