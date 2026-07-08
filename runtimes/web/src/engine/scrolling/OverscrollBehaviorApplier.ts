// OverscrollBehaviorApplier.ts — emits { overscrollBehavior }.  MDN: overscroll-behavior.
import type { CSSProperties } from 'react';
import type { OverscrollBehaviorConfig } from './OverscrollBehaviorConfig';
export function applyOverscrollBehavior(c: OverscrollBehaviorConfig): CSSProperties {
  return c.value === undefined ? {} : { overscrollBehavior: c.value } as CSSProperties;
}
