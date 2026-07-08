// StringSetApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/string-set.
import type { CSSProperties } from 'react';
import type { StringSetConfig } from './StringSetConfig';
export function applyStringSet(c: StringSetConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ stringSet: c.value } as unknown as CSSProperties) as Record<string, string>;
}
