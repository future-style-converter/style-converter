// BookmarkTargetApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/bookmark-target.
import type { CSSProperties } from 'react';
import type { BookmarkTargetConfig } from './BookmarkTargetConfig';
export function applyBookmarkTarget(c: BookmarkTargetConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bookmarkTarget: c.value } as unknown as CSSProperties) as Record<string, string>;
}
