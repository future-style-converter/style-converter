// BookmarkLevelApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/bookmark-level.
import type { CSSProperties } from 'react';
import type { BookmarkLevelConfig } from './BookmarkLevelConfig';
export function applyBookmarkLevel(c: BookmarkLevelConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bookmarkLevel: c.value } as unknown as CSSProperties) as Record<string, string>;
}
