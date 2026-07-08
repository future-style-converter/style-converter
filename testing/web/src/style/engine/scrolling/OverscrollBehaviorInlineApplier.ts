// OverscrollBehaviorInlineApplier.ts — emits { overscrollBehaviorInline }.  MDN: overscroll-behavior-inline.
import type { CSSProperties } from 'react';
import type { OverscrollBehaviorInlineConfig } from './OverscrollBehaviorInlineConfig';
export function applyOverscrollBehaviorInline(c: OverscrollBehaviorInlineConfig): CSSProperties {
  return c.value === undefined ? {} : { overscrollBehaviorInline: c.value } as CSSProperties;
}
