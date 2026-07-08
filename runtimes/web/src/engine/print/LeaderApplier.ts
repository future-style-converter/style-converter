// LeaderApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/leader.
import type { CSSProperties } from 'react';
import type { LeaderConfig } from './LeaderConfig';
export function applyLeader(c: LeaderConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ leader: c.value } as unknown as CSSProperties) as Record<string, string>;
}
