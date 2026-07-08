// ResizeApplier.ts — emits { resize }.  MDN: resize.
import type { CSSProperties } from 'react';
import type { ResizeConfig } from './ResizeConfig';
export function applyResize(c: ResizeConfig): CSSProperties {
  return c.value === undefined ? {} : { resize: c.value } as CSSProperties;
}
