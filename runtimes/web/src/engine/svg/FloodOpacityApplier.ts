// FloodOpacityApplier.ts — emits { floodOpacity }.  MDN: flood-opacity.
import type { CSSProperties } from 'react';
import type { FloodOpacityConfig } from './FloodOpacityConfig';
export function applyFloodOpacity(c: FloodOpacityConfig): CSSProperties {
  return c.value === undefined ? {} : { floodOpacity: c.value } as CSSProperties;
}
