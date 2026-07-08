// FlowIntoApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/flow-into.
import type { CSSProperties } from 'react';
import type { FlowIntoConfig } from './FlowIntoConfig';
export function applyFlowInto(c: FlowIntoConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ flowInto: c.value } as unknown as CSSProperties) as Record<string, string>;
}
