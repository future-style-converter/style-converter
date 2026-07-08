// VoiceDurationApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-duration.
import type { CSSProperties } from 'react';
import type { VoiceDurationConfig } from './VoiceDurationConfig';
export function applyVoiceDuration(c: VoiceDurationConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceDuration: c.value } as unknown as CSSProperties) as Record<string, string>;
}
