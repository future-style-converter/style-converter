// ContainIntrinsicWidthApplier.ts — emits { containIntrinsicWidth }.  MDN: contain-intrinsic-width.
import type { CSSProperties } from 'react';
import type { ContainIntrinsicWidthConfig } from './ContainIntrinsicWidthConfig';
export function applyContainIntrinsicWidth(c: ContainIntrinsicWidthConfig): CSSProperties {
  return c.value === undefined ? {} : { containIntrinsicWidth: c.value } as CSSProperties;
}
