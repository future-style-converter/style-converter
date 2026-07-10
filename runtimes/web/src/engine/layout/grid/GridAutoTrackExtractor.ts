// GridAutoTrackExtractor.ts — folds IR `GridAutoTrack` values into a config.
// Wire shapes (GridAutoTrackPropertyParser.kt → GridTrackSize sealed class,
// discriminated by @SerialName type tags):
//   { type:'length', px:N }                       → 'Npx'
//   { type:'length', original:{v,u:'FR'} }        → '1fr' (fr has no px)
//   { type:'auto' }                               → 'auto'
//   { type:'minmax', min:<size>, max:<size> }     → 'minmax(a, b)'
//   { type:'fit-content', value:<IRLength> }      → 'fit-content(len)'
// Leaf sizes reuse the shared grid trackSize first, then layoutLength (which
// understands the {original:{v,u}} envelope trackSize predates). Standalone
// by design: does NOT touch GridExtractor or its repeat() path.
import { foldLast, type IRPropertyLike } from '../../_phase10_shared';
import { layoutLength } from '../_shared';
import { trackSize } from './_grid_shared';
import { GRID_AUTO_TRACK_PROPERTY_TYPE, type GridAutoTrackConfig } from './GridAutoTrackConfig';

// One track-size leaf or composite → CSS string, or undefined to drop.
function autoTrack(data: unknown): string | undefined {
  if (data && typeof data === 'object') {                           // sealed-class variants
    const o = data as Record<string, unknown>;
    if (o.type === 'auto') return 'auto';                           // GridTrackSize.Auto object form
    if ('min' in o && 'max' in o) {                                 // GridTrackSize.MinMax
      const min = autoTrack(o.min);                                 // lower bound (recursive)
      const max = autoTrack(o.max);                                 // upper bound (recursive)
      return min && max ? `minmax(${min}, ${max})` : undefined;     // both or nothing
    }
    if (o.type === 'fit-content' && 'value' in o) {                 // GridTrackSize.FitContent
      const len = autoTrack(o.value);                               // bound length leaf
      return len ? `fit-content(${len})` : undefined;               // CSS functional form
    }
  }
  // Shared grid track renderer handles {fr},{px},'auto' string,repeat().
  const viaTrack = trackSize(data);
  if (viaTrack !== undefined) return viaTrack;
  // layoutLength additionally understands the {type:'length', original:{v,u}}
  // envelope (e.g. the fr leaf inside minmax) that trackSize predates.
  return layoutLength(data);
}

// Single-pass fold, last write wins — matches the other grid extractors.
export function extractGridAutoTrack(properties: IRPropertyLike[]): GridAutoTrackConfig {
  return { value: foldLast(properties, GRID_AUTO_TRACK_PROPERTY_TYPE, autoTrack) };
}
