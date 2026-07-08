// ContainIntrinsicBlockSizeApplier.ts — emits { containIntrinsicBlockSize }.  MDN: contain-intrinsic-block-size.
import type { CSSProperties } from 'react';
import type { ContainIntrinsicBlockSizeConfig } from './ContainIntrinsicBlockSizeConfig';
export function applyContainIntrinsicBlockSize(c: ContainIntrinsicBlockSizeConfig): CSSProperties {
  return c.value === undefined ? {} : { containIntrinsicBlockSize: c.value } as CSSProperties;
}
