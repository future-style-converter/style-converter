// AlignTracksApplier.ts — emits `align-tracks`.  L3 draft; widened.
// WHY widen: csstype has no `align-tracks` — draft-level Grid L3 masonry.

import type { AlignTracksConfig } from './AlignTracksConfig';

export function applyAlignTracks(config: AlignTracksConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { alignTracks: config.value };
}
