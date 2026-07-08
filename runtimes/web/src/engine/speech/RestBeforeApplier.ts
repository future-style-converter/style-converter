// RestBeforeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/rest-before.
import type { CSSProperties } from 'react';
import type { RestBeforeConfig } from './RestBeforeConfig';
export function applyRestBefore(c: RestBeforeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ restBefore: c.value } as unknown as CSSProperties) as Record<string, string>;
}
