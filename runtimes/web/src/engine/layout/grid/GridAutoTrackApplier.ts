// GridAutoTrackApplier.ts — csstype-widened (css-grid-3 masonry draft key;
// browsers ignore it today, but the pass-through keeps coverage honest).
import type { CSSProperties } from 'react';
import type { GridAutoTrackConfig } from './GridAutoTrackConfig';
// Pure function — emit only when the extractor produced a value.
export function applyGridAutoTrack(c: GridAutoTrackConfig): Record<string, string> {
  if (c.value === undefined) return {};                             // unset → no declaration
  return ({ gridAutoTrack: c.value } as unknown as CSSProperties) as Record<string, string>;
}
