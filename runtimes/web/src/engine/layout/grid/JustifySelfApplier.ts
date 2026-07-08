// JustifySelfApplier.ts — emits `justify-self`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/justify-self.

import type { CSSProperties } from 'react';
import type { JustifySelfConfig } from './JustifySelfConfig';

export type JustifySelfStyles = Pick<CSSProperties, 'justifySelf'>;

export function applyJustifySelf(config: JustifySelfConfig): JustifySelfStyles {
  if (config.value === undefined) return {};
  return { justifySelf: config.value } as JustifySelfStyles;
}
