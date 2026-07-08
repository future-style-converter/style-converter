// WrapFlowApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/wrap-flow.
import type { CSSProperties } from 'react';
import type { WrapFlowConfig } from './WrapFlowConfig';
export function applyWrapFlow(c: WrapFlowConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ wrapFlow: c.value } as unknown as CSSProperties) as Record<string, string>;
}
