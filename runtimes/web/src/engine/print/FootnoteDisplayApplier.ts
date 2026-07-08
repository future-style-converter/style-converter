// FootnoteDisplayApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/footnote-display.
import type { CSSProperties } from 'react';
import type { FootnoteDisplayConfig } from './FootnoteDisplayConfig';
export function applyFootnoteDisplay(c: FootnoteDisplayConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ footnoteDisplay: c.value } as unknown as CSSProperties) as Record<string, string>;
}
