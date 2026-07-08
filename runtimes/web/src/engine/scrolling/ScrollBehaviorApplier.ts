// ScrollBehaviorApplier.ts — emits { scrollBehavior }.  MDN: scroll-behavior.
import type { CSSProperties } from 'react';
import type { ScrollBehaviorConfig } from './ScrollBehaviorConfig';
export function applyScrollBehavior(c: ScrollBehaviorConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollBehavior: c.value } as CSSProperties;
}
