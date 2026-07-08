// BookmarkStateApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/bookmark-state.
import type { CSSProperties } from 'react';
import type { BookmarkStateConfig } from './BookmarkStateConfig';
export function applyBookmarkState(c: BookmarkStateConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bookmarkState: c.value } as unknown as CSSProperties) as Record<string, string>;
}
