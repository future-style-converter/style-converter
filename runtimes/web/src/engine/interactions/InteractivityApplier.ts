// InteractivityApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/interactivity.
import type { CSSProperties } from 'react';
import type { InteractivityConfig } from './InteractivityConfig';
export function applyInteractivity(c: InteractivityConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ interactivity: c.value } as unknown as CSSProperties) as Record<string, string>;
}
