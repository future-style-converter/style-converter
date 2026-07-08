// ScrollbarGutterApplier.ts — emits { scrollbarGutter }.  MDN: scrollbar-gutter.
import type { CSSProperties } from 'react';
import type { ScrollbarGutterConfig } from './ScrollbarGutterConfig';
export function applyScrollbarGutter(c: ScrollbarGutterConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollbarGutter: c.value } as CSSProperties;
}
