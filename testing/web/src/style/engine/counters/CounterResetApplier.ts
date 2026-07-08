// CounterResetApplier.ts — emits { counterReset }.  MDN: counter-reset.
import type { CSSProperties } from 'react';
import type { CounterResetConfig } from './CounterResetConfig';
export function applyCounterReset(c: CounterResetConfig): CSSProperties {
  return c.value === undefined ? {} : { counterReset: c.value } as CSSProperties;
}
