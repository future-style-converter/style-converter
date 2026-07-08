// FlowFromApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/flow-from.
import type { CSSProperties } from 'react';
import type { FlowFromConfig } from './FlowFromConfig';
export function applyFlowFrom(c: FlowFromConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ flowFrom: c.value } as unknown as CSSProperties) as Record<string, string>;
}
