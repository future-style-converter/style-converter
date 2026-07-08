// ListStyleImageApplier.ts — emits { listStyleImage }.  MDN: list-style-image.
import type { CSSProperties } from 'react';
import type { ListStyleImageConfig } from './ListStyleImageConfig';
export function applyListStyleImage(c: ListStyleImageConfig): CSSProperties {
  return c.value === undefined ? {} : { listStyleImage: c.value } as CSSProperties;
}
