// PointerEventsApplier.ts — emits { pointerEvents }.  MDN: pointer-events.
import type { CSSProperties } from 'react';
import type { PointerEventsConfig } from './PointerEventsConfig';
export function applyPointerEvents(c: PointerEventsConfig): CSSProperties {
  return c.value === undefined ? {} : { pointerEvents: c.value } as CSSProperties;
}
