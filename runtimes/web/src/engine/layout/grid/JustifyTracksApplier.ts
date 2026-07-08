// JustifyTracksApplier.ts — emits `justify-tracks`.  L3 draft; widened.
// WHY widen: csstype's React.CSSProperties has no `justify-tracks` entry —
// see https://drafts.csswg.org/css-grid-3/#justify-tracks-alignment.

import type { JustifyTracksConfig } from './JustifyTracksConfig';

export function applyJustifyTracks(config: JustifyTracksConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { justifyTracks: config.value };                                           // untyped passthrough
}
