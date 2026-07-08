// InsetBlockEndApplier.ts — emits `insetBlockEnd`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/i-n-s-e-t--b-l-o-c-k--e-n-d.

import type { CSSProperties } from 'react';
import type { InsetBlockEndConfig } from './InsetBlockEndConfig';

export type InsetBlockEndStyles = Pick<CSSProperties, 'insetBlockEnd'>;

export function applyInsetBlockEnd(config: InsetBlockEndConfig): InsetBlockEndStyles {
  if (config.value === undefined) return {};
  return { insetBlockEnd: config.value } as InsetBlockEndStyles;
}
