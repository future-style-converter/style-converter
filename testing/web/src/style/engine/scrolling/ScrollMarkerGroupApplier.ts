// ScrollMarkerGroupApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-marker-group.
import type { CSSProperties } from 'react';
import type { ScrollMarkerGroupConfig } from './ScrollMarkerGroupConfig';
export function applyScrollMarkerGroup(c: ScrollMarkerGroupConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollMarkerGroup: c.value } as unknown as CSSProperties) as Record<string, string>;
}
