// InsetInlineStartApplier.ts — emits `insetInlineStart`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/i-n-s-e-t--i-n-l-i-n-e--s-t-a-r-t.

import type { CSSProperties } from 'react';
import type { InsetInlineStartConfig } from './InsetInlineStartConfig';

export type InsetInlineStartStyles = Pick<CSSProperties, 'insetInlineStart'>;

export function applyInsetInlineStart(config: InsetInlineStartConfig): InsetInlineStartStyles {
  if (config.value === undefined) return {};
  return { insetInlineStart: config.value } as InsetInlineStartStyles;
}
