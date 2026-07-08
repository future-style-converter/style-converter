// MarginExtractor.ts — folds Margin* IR properties into a MarginConfig.
// The only non-padding wrinkle: a bare IR string "auto" means the margin
// absorbs free space (flex centering, etc.).  extractLength already handles
// it (returns {kind:'auto'}), so we just forward.

import { extractLength } from '../core/types/LengthValue';
import type { MarginConfig, MarginPropertyType, MarginValue } from './MarginConfig';

// Local alias mirroring IRProperty to keep the engine module decoupled.
interface IRPropertyLike { type: string; data: unknown; }

// Set for fast type-membership checks.
const MARGIN_TYPES = new Set<string>([
  'MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft',
  'MarginBlockStart', 'MarginBlockEnd', 'MarginInlineStart', 'MarginInlineEnd',
]);

// Predicate for the renderer — mirrors isPaddingProperty.
export function isMarginProperty(type: string): type is MarginPropertyType {
  return MARGIN_TYPES.has(type);
}

// Apply one IR property to the config.  Auto and numeric values both flow
// through extractLength (whose union already covers {kind:'auto'}).
function applyOne(config: MarginConfig, prop: IRPropertyLike): void {
  const value: MarginValue = extractLength(prop.data);                // shared primitive
  if (value.kind === 'unknown') return;                               // drop unparseable IR
  // Exhaustive switch — TS flags additions via the `never` default.
  switch (prop.type as MarginPropertyType) {
    case 'MarginTop':           config.top = value; break;
    case 'MarginRight':         config.right = value; break;
    case 'MarginBottom':        config.bottom = value; break;
    case 'MarginLeft':          config.left = value; break;
    case 'MarginBlockStart':    config.blockStart = value; break;
    case 'MarginBlockEnd':      config.blockEnd = value; break;
    case 'MarginInlineStart':   config.inlineStart = value; break;
    case 'MarginInlineEnd':     config.inlineEnd = value; break;
    default: {
      // Compile-time exhaustiveness guard.
      const _never: never = prop.type as never;
      void _never;
    }
  }
}

// Public entrypoint — fold relevant IR properties into a MarginConfig.
export function extractMargin(properties: IRPropertyLike[]): MarginConfig {
  const cfg: MarginConfig = {};                                       // blank start
  for (const p of properties) {                                       // single pass
    if (isMarginProperty(p.type)) applyOne(cfg, p);                   // narrowed apply
  }
  return cfg;
}
