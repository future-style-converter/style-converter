// OverscrollBehaviorXApplier.ts — emits { overscrollBehaviorX }.  MDN: overscroll-behavior-x.
import type { CSSProperties } from 'react';
import type { OverscrollBehaviorXConfig } from './OverscrollBehaviorXConfig';
export function applyOverscrollBehaviorX(c: OverscrollBehaviorXConfig): CSSProperties {
  return c.value === undefined ? {} : { overscrollBehaviorX: c.value } as CSSProperties;
}
