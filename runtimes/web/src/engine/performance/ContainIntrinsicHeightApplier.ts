// ContainIntrinsicHeightApplier.ts — emits { containIntrinsicHeight }.  MDN: contain-intrinsic-height.
import type { CSSProperties } from 'react';
import type { ContainIntrinsicHeightConfig } from './ContainIntrinsicHeightConfig';
export function applyContainIntrinsicHeight(c: ContainIntrinsicHeightConfig): CSSProperties {
  return c.value === undefined ? {} : { containIntrinsicHeight: c.value } as CSSProperties;
}
