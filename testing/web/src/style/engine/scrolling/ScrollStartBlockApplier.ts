// ScrollStartBlockApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/scroll-start-block.
import type { CSSProperties } from 'react';
import type { ScrollStartBlockConfig } from './ScrollStartBlockConfig';
export function applyScrollStartBlock(c: ScrollStartBlockConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ scrollStartBlock: c.value } as unknown as CSSProperties) as Record<string, string>;
}
