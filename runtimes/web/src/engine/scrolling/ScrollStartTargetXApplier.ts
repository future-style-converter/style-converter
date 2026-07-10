// ScrollStartTargetXApplier.ts — csstype-widened.  Draft css-scroll-snap-2 key.
import type { CSSProperties } from 'react';
import type { ScrollStartTargetXConfig } from './ScrollStartTargetXConfig';
export function applyScrollStartTargetX(c: ScrollStartTargetXConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartTargetX: c.value } as unknown as CSSProperties) as Record<string, string>;
}
