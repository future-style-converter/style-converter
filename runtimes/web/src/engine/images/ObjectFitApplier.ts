// ObjectFitApplier.ts — emits { objectFit }.  MDN: object-fit.
import type { CSSProperties } from 'react';
import type { ObjectFitConfig } from './ObjectFitConfig';
export function applyObjectFit(c: ObjectFitConfig): CSSProperties {
  return c.value === undefined ? {} : { objectFit: c.value } as CSSProperties;
}
