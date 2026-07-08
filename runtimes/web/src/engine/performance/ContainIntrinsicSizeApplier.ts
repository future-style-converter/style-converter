// ContainIntrinsicSizeApplier.ts — emits { containIntrinsicSize }.  MDN: contain-intrinsic-size.
import type { CSSProperties } from 'react';
import type { ContainIntrinsicSizeConfig } from './ContainIntrinsicSizeConfig';
export function applyContainIntrinsicSize(c: ContainIntrinsicSizeConfig): CSSProperties {
  return c.value === undefined ? {} : { containIntrinsicSize: c.value } as CSSProperties;
}
