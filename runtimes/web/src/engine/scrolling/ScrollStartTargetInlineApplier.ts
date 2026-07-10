// ScrollStartTargetInlineApplier.ts — csstype-widened.  Draft css-scroll-snap-2 key.
import type { CSSProperties } from 'react';
import type { ScrollStartTargetInlineConfig } from './ScrollStartTargetInlineConfig';
export function applyScrollStartTargetInline(c: ScrollStartTargetInlineConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartTargetInline: c.value } as unknown as CSSProperties) as Record<string, string>;
}
