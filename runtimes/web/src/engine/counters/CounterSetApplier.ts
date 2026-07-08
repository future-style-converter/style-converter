// CounterSetApplier.ts — emits { counterSet }.  MDN: counter-set.
import type { CSSProperties } from 'react';
import type { CounterSetConfig } from './CounterSetConfig';
export function applyCounterSet(c: CounterSetConfig): CSSProperties {
  return c.value === undefined ? {} : { counterSet: c.value } as CSSProperties;
}
