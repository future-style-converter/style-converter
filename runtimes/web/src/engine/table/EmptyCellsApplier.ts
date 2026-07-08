// EmptyCellsApplier.ts — emits { emptyCells }.  MDN: empty-cells.
import type { CSSProperties } from 'react';
import type { EmptyCellsConfig } from './EmptyCellsConfig';
export function applyEmptyCells(c: EmptyCellsConfig): CSSProperties {
  return c.value === undefined ? {} : { emptyCells: c.value } as CSSProperties;
}
