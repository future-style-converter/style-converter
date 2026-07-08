// SpeakHeaderApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speak-header.
import type { CSSProperties } from 'react';
import type { SpeakHeaderConfig } from './SpeakHeaderConfig';
export function applySpeakHeader(c: SpeakHeaderConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speakHeader: c.value } as unknown as CSSProperties) as Record<string, string>;
}
