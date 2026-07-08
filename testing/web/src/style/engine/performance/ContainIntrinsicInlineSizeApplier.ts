// ContainIntrinsicInlineSizeApplier.ts — emits { containIntrinsicInlineSize }.  MDN: contain-intrinsic-inline-size.
import type { CSSProperties } from 'react';
import type { ContainIntrinsicInlineSizeConfig } from './ContainIntrinsicInlineSizeConfig';
export function applyContainIntrinsicInlineSize(c: ContainIntrinsicInlineSizeConfig): CSSProperties {
  return c.value === undefined ? {} : { containIntrinsicInlineSize: c.value } as CSSProperties;
}
