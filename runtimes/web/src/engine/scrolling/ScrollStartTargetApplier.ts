// ScrollStartTargetApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start-target.
import type { CSSProperties } from 'react';
import type { ScrollStartTargetConfig } from './ScrollStartTargetConfig';
export function applyScrollStartTarget(c: ScrollStartTargetConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartTarget: c.value } as unknown as CSSProperties) as Record<string, string>;
}
