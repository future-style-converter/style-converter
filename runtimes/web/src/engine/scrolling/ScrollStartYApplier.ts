// ScrollStartYApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start-y.
import type { CSSProperties } from 'react';
import type { ScrollStartYConfig } from './ScrollStartYConfig';
export function applyScrollStartY(c: ScrollStartYConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartY: c.value } as unknown as CSSProperties) as Record<string, string>;
}
