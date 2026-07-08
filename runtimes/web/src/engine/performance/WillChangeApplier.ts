// WillChangeApplier.ts — emits { willChange }.  MDN: will-change.
import type { CSSProperties } from 'react';
import type { WillChangeConfig } from './WillChangeConfig';
export function applyWillChange(c: WillChangeConfig): CSSProperties {
  return c.value === undefined ? {} : { willChange: c.value } as CSSProperties;
}
