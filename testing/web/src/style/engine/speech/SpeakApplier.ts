// SpeakApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speak.
import type { CSSProperties } from 'react';
import type { SpeakConfig } from './SpeakConfig';
export function applySpeak(c: SpeakConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speak: c.value } as unknown as CSSProperties) as Record<string, string>;
}
