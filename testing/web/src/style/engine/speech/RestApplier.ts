// RestApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/rest.
import type { CSSProperties } from 'react';
import type { RestConfig } from './RestConfig';
export function applyRest(c: RestConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ rest: c.value } as unknown as CSSProperties) as Record<string, string>;
}
