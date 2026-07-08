// CounterIncrementApplier.ts — emits { counterIncrement }.  MDN: counter-increment.
import type { CSSProperties } from 'react';
import type { CounterIncrementConfig } from './CounterIncrementConfig';
export function applyCounterIncrement(c: CounterIncrementConfig): CSSProperties {
  return c.value === undefined ? {} : { counterIncrement: c.value } as CSSProperties;
}
