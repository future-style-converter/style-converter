// ScrollStartTargetYApplier.ts — csstype-widened.  Draft css-scroll-snap-2 key.
import type { CSSProperties } from 'react';
import type { ScrollStartTargetYConfig } from './ScrollStartTargetYConfig';
export function applyScrollStartTargetY(c: ScrollStartTargetYConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartTargetY: c.value } as unknown as CSSProperties) as Record<string, string>;
}
