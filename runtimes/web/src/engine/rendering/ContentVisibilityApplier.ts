// ContentVisibilityApplier.ts — emits { contentVisibility }.  MDN: content-visibility.
import type { CSSProperties } from 'react';
import type { ContentVisibilityConfig } from './ContentVisibilityConfig';
export function applyContentVisibility(c: ContentVisibilityConfig): CSSProperties {
  return c.value === undefined ? {} : { contentVisibility: c.value } as CSSProperties;
}
