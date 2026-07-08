// InsetBlockStartApplier.ts — emits `insetBlockStart`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/i-n-s-e-t--b-l-o-c-k--s-t-a-r-t.

import type { CSSProperties } from 'react';
import type { InsetBlockStartConfig } from './InsetBlockStartConfig';

export type InsetBlockStartStyles = Pick<CSSProperties, 'insetBlockStart'>;

export function applyInsetBlockStart(config: InsetBlockStartConfig): InsetBlockStartStyles {
  if (config.value === undefined) return {};
  return { insetBlockStart: config.value } as InsetBlockStartStyles;
}
