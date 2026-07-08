// SpeakPunctuationApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speak-punctuation.
import type { CSSProperties } from 'react';
import type { SpeakPunctuationConfig } from './SpeakPunctuationConfig';
export function applySpeakPunctuation(c: SpeakPunctuationConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speakPunctuation: c.value } as unknown as CSSProperties) as Record<string, string>;
}
