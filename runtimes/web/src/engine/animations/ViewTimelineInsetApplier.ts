// ViewTimelineInsetApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ViewTimelineInsetConfig } from './ViewTimelineInsetConfig';
export type ViewTimelineInsetStyles = Record<string, string>;
export function applyViewTimelineInset(c: ViewTimelineInsetConfig): ViewTimelineInsetStyles {
  if (c.value === undefined) return {};
  return ({ viewTimelineInset: c.value } as unknown as CSSProperties) as ViewTimelineInsetStyles;
}
