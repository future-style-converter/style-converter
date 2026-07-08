// CursorApplier.ts — emits { cursor }.  MDN: cursor.
import type { CSSProperties } from 'react';
import type { CursorConfig } from './CursorConfig';
export function applyCursor(c: CursorConfig): CSSProperties {
  return c.value === undefined ? {} : { cursor: c.value } as CSSProperties;
}
