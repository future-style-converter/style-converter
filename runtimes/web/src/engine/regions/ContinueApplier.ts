// ContinueApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/continue.
import type { CSSProperties } from 'react';
import type { ContinueConfig } from './ContinueConfig';
export function applyContinue(c: ContinueConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ continue: c.value } as unknown as CSSProperties) as Record<string, string>;
}
