// ListStylePositionApplier.ts — emits { listStylePosition }.  MDN: list-style-position.
import type { CSSProperties } from 'react';
import type { ListStylePositionConfig } from './ListStylePositionConfig';
export function applyListStylePosition(c: ListStylePositionConfig): CSSProperties {
  return c.value === undefined ? {} : { listStylePosition: c.value } as CSSProperties;
}
