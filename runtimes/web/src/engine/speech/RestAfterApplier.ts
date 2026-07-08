// RestAfterApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/rest-after.
import type { CSSProperties } from 'react';
import type { RestAfterConfig } from './RestAfterConfig';
export function applyRestAfter(c: RestAfterConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ restAfter: c.value } as unknown as CSSProperties) as Record<string, string>;
}
