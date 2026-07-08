// ScrollStartInlineApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start-inline.
import type { CSSProperties } from 'react';
import type { ScrollStartInlineConfig } from './ScrollStartInlineConfig';
export function applyScrollStartInline(c: ScrollStartInlineConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartInline: c.value } as unknown as CSSProperties) as Record<string, string>;
}
