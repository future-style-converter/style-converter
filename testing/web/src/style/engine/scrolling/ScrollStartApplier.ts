// ScrollStartApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start.
import type { CSSProperties } from 'react';
import type { ScrollStartConfig } from './ScrollStartConfig';
export function applyScrollStart(c: ScrollStartConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStart: c.value } as unknown as CSSProperties) as Record<string, string>;
}
