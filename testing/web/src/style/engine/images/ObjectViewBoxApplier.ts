// ObjectViewBoxApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/object-view-box.
import type { CSSProperties } from 'react';
import type { ObjectViewBoxConfig } from './ObjectViewBoxConfig';
export function applyObjectViewBox(c: ObjectViewBoxConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ objectViewBox: c.value } as unknown as CSSProperties) as Record<string, string>;
}
