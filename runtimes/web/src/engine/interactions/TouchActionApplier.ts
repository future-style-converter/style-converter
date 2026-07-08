// TouchActionApplier.ts — emits { touchAction }.  MDN: touch-action.
import type { CSSProperties } from 'react';
import type { TouchActionConfig } from './TouchActionConfig';
export function applyTouchAction(c: TouchActionConfig): CSSProperties {
  return c.value === undefined ? {} : { touchAction: c.value } as CSSProperties;
}
