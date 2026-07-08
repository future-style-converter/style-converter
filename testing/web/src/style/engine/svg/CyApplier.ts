// CyApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/cy.
import type { CSSProperties } from 'react';
import type { CyConfig } from './CyConfig';
export function applyCy(c: CyConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ cy: c.value } as unknown as CSSProperties) as Record<string, string>;
}
