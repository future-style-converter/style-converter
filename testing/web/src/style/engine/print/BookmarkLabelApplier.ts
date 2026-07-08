// BookmarkLabelApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/bookmark-label.
import type { CSSProperties } from 'react';
import type { BookmarkLabelConfig } from './BookmarkLabelConfig';
export function applyBookmarkLabel(c: BookmarkLabelConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bookmarkLabel: c.value } as unknown as CSSProperties) as Record<string, string>;
}
