// ScrollStartTargetBlockApplier.ts — csstype-widened.  Draft css-scroll-snap-2 key.
import type { CSSProperties } from 'react';
import type { ScrollStartTargetBlockConfig } from './ScrollStartTargetBlockConfig';
export function applyScrollStartTargetBlock(c: ScrollStartTargetBlockConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartTargetBlock: c.value } as unknown as CSSProperties) as Record<string, string>;
}
