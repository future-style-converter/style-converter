// PageApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/page.
import type { CSSProperties } from 'react';
import type { PageConfig } from './PageConfig';
export function applyPage(c: PageConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ page: c.value } as unknown as CSSProperties) as Record<string, string>;
}
