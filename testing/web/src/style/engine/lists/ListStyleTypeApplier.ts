// ListStyleTypeApplier.ts — emits { listStyleType }.  MDN: list-style-type.
import type { CSSProperties } from 'react';
import type { ListStyleTypeConfig } from './ListStyleTypeConfig';
export function applyListStyleType(c: ListStyleTypeConfig): CSSProperties {
  return c.value === undefined ? {} : { listStyleType: c.value } as CSSProperties;
}
