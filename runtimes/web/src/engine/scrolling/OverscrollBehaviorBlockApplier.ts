// OverscrollBehaviorBlockApplier.ts — emits { overscrollBehaviorBlock }.  MDN: overscroll-behavior-block.
import type { CSSProperties } from 'react';
import type { OverscrollBehaviorBlockConfig } from './OverscrollBehaviorBlockConfig';
export function applyOverscrollBehaviorBlock(c: OverscrollBehaviorBlockConfig): CSSProperties {
  return c.value === undefined ? {} : { overscrollBehaviorBlock: c.value } as CSSProperties;
}
