// BlockStepInsertApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/block-step-insert.
import type { CSSProperties } from 'react';
import type { BlockStepInsertConfig } from './BlockStepInsertConfig';
export function applyBlockStepInsert(c: BlockStepInsertConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ blockStepInsert: c.value } as unknown as CSSProperties) as Record<string, string>;
}
