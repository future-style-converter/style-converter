// OverscrollBehaviorYApplier.ts — emits { overscrollBehaviorY }.  MDN: overscroll-behavior-y.
import type { CSSProperties } from 'react';
import type { OverscrollBehaviorYConfig } from './OverscrollBehaviorYConfig';
export function applyOverscrollBehaviorY(c: OverscrollBehaviorYConfig): CSSProperties {
  return c.value === undefined ? {} : { overscrollBehaviorY: c.value } as CSSProperties;
}
