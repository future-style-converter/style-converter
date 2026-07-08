// ContentApplier.ts — emits { content }.  MDN: content.
import type { CSSProperties } from 'react';
import type { ContentConfig } from './ContentConfig';
export function applyContent(c: ContentConfig): CSSProperties {
  return c.value === undefined ? {} : { content: c.value } as CSSProperties;
}
