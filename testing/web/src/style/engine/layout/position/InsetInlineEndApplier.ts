// InsetInlineEndApplier.ts — emits `insetInlineEnd`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/i-n-s-e-t--i-n-l-i-n-e--e-n-d.

import type { CSSProperties } from 'react';
import type { InsetInlineEndConfig } from './InsetInlineEndConfig';

export type InsetInlineEndStyles = Pick<CSSProperties, 'insetInlineEnd'>;

export function applyInsetInlineEnd(config: InsetInlineEndConfig): InsetInlineEndStyles {
  if (config.value === undefined) return {};
  return { insetInlineEnd: config.value } as InsetInlineEndStyles;
}
