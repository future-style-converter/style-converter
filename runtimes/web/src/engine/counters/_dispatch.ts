// _dispatch.ts — Phase-10 counters long-tail dispatch (3 properties).
import type { CSSProperties } from 'react';
import { extractCounterIncrement } from './CounterIncrementExtractor';
import { applyCounterIncrement } from './CounterIncrementApplier';
import { extractCounterReset } from './CounterResetExtractor';
import { applyCounterReset } from './CounterResetApplier';
import { extractCounterSet } from './CounterSetExtractor';
import { applyCounterSet } from './CounterSetApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyCountersPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyCounterIncrement(extractCounterIncrement(properties)));
  Object.assign(out, applyCounterReset(extractCounterReset(properties)));
  Object.assign(out, applyCounterSet(extractCounterSet(properties)));
  return out;
}
