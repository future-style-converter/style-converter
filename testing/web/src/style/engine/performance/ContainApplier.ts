// ContainApplier.ts — emits { contain }.  MDN: contain.
import type { CSSProperties } from 'react';
import type { ContainConfig } from './ContainConfig';
export function applyContain(c: ContainConfig): CSSProperties {
  return c.value === undefined ? {} : { contain: c.value } as CSSProperties;
}
