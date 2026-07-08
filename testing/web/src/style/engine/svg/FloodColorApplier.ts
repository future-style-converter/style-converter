// FloodColorApplier.ts — emits { floodColor }.  MDN: flood-color.
import type { CSSProperties } from 'react';
import type { FloodColorConfig } from './FloodColorConfig';
export function applyFloodColor(c: FloodColorConfig): CSSProperties {
  return c.value === undefined ? {} : { floodColor: c.value } as CSSProperties;
}
