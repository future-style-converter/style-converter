// CaretApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/caret.
import type { CSSProperties } from 'react';
import type { CaretConfig } from './CaretConfig';
export function applyCaret(c: CaretConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ caret: c.value } as unknown as CSSProperties) as Record<string, string>;
}
