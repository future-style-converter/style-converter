// BoxOrientApplier.ts — emits `-webkit-box-orient` via React's WebkitBoxOrient
// key (React maps the Webkit prefix to the -webkit- CSS property). Widened
// because csstype pins BoxOrient to its literal union and our extractor
// yields a plain string.
import type { CSSProperties } from 'react';
import type { BoxOrientConfig } from './BoxOrientConfig';

// Pure function — emit only when the extractor produced a value.
export function applyBoxOrient(c: BoxOrientConfig): Record<string, string> {
  if (c.value === undefined) return {};                             // unset → no declaration
  return ({ WebkitBoxOrient: c.value } as unknown as CSSProperties) as Record<string, string>;
}
