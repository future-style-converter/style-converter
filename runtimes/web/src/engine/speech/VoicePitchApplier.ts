// VoicePitchApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-pitch.
import type { CSSProperties } from 'react';
import type { VoicePitchConfig } from './VoicePitchConfig';
export function applyVoicePitch(c: VoicePitchConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voicePitch: c.value } as unknown as CSSProperties) as Record<string, string>;
}
