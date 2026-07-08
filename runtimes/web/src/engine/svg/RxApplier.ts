// RxApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/rx.
import type { CSSProperties } from 'react';
import type { RxConfig } from './RxConfig';
export function applyRx(c: RxConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ rx: c.value } as unknown as CSSProperties) as Record<string, string>;
}
