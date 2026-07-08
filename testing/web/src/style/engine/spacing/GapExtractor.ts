// GapExtractor.ts — folds Gap / RowGap / ColumnGap into a GapConfig.
// Gap is the only spacing family where the `Gap` shorthand sometimes survives
// shorthand expansion (e.g. when only a single value is supplied); we handle
// it by applying to both axes.

import { extractLength } from '../core/types/LengthValue';
import type { GapConfig, GapPropertyType } from './GapConfig';

// Local IR-property shape alias.
interface IRPropertyLike { type: string; data: unknown; }

// Set for O(1) membership test.
const GAP_TYPES = new Set<string>(['Gap', 'RowGap', 'ColumnGap']);

// Renderer predicate.
export function isGapProperty(type: string): type is GapPropertyType {
  return GAP_TYPES.has(type);
}

// Apply a single property.  Gap → both axes; RowGap / ColumnGap → one.
function applyOne(config: GapConfig, prop: IRPropertyLike): void {
  const length = extractLength(prop.data);                            // shared primitive
  if (length.kind === 'unknown' || length.kind === 'auto') return;    // gap rejects auto
  switch (prop.type as GapPropertyType) {
    case 'Gap':        config.rowGap = length; config.columnGap = length; break;
    case 'RowGap':     config.rowGap = length; break;
    case 'ColumnGap':  config.columnGap = length; break;
    default: {
      // Compile-time exhaustiveness guard.
      const _never: never = prop.type as never;
      void _never;
    }
  }
}

// Public entrypoint — fold IR properties into a GapConfig.
export function extractGap(properties: IRPropertyLike[]): GapConfig {
  const cfg: GapConfig = {};                                          // blank start
  for (const p of properties) {                                       // single pass
    if (isGapProperty(p.type)) applyOne(cfg, p);                      // narrow + apply
  }
  return cfg;
}
