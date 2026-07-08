// ScrollTargetGroupApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-target-group.
import type { CSSProperties } from 'react';
import type { ScrollTargetGroupConfig } from './ScrollTargetGroupConfig';
export function applyScrollTargetGroup(c: ScrollTargetGroupConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollTargetGroup: c.value } as unknown as CSSProperties) as Record<string, string>;
}
