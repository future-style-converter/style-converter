// ScrollStartXApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start-x.
import type { CSSProperties } from 'react';
import type { ScrollStartXConfig } from './ScrollStartXConfig';
export function applyScrollStartX(c: ScrollStartXConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartX: c.value } as unknown as CSSProperties) as Record<string, string>;
}
