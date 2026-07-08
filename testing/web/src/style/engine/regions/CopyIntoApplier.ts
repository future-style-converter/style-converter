// CopyIntoApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/copy-into.
import type { CSSProperties } from 'react';
import type { CopyIntoConfig } from './CopyIntoConfig';
export function applyCopyInto(c: CopyIntoConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ copyInto: c.value } as unknown as CSSProperties) as Record<string, string>;
}
