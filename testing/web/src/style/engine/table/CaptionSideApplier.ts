// CaptionSideApplier.ts — emits { captionSide }.  MDN: caption-side.
import type { CSSProperties } from 'react';
import type { CaptionSideConfig } from './CaptionSideConfig';
export function applyCaptionSide(c: CaptionSideConfig): CSSProperties {
  return c.value === undefined ? {} : { captionSide: c.value } as CSSProperties;
}
