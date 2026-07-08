// SizeExtractor.ts — folds a list of sizing IR properties into a SizeConfig.
// The IR uses two *distinct* length wrappers for sizing properties:
//   Width / Height / Min* / Max* (WidthValue)  -> { type:'length', px } / 'auto' / 'min-content' / { 'fit-content': ... } / { type:'none' }
//   BlockSize / InlineSize / Min* / Max* (SizeValue) -> { px:N } raw / bare-number percent / 'auto' / 'none'
// extractLength() already handles both wrappers (plus {type:'none'} and the
// bounded fit-content shape) since the Phase-3 LengthValue extension, so we
// can delegate unconditionally and branch only on the IR property name.

import { extractLength, type LengthValue } from '../core/types/LengthValue';
import { extractAspectRatio } from './AspectRatioValue';
import type { SizeConfig, SizePropertyType } from './SizeConfig';
import { SIZE_PROPERTY_TYPES } from './SizeConfig';

// Minimal IR property shape — keeps the engine decoupled from legacy types.
interface IRPropertyLike { type: string; data: unknown; }

// O(1) membership set — drives the `isSizeProperty` type-guard below.
const SIZE_TYPES = new Set<string>(SIZE_PROPERTY_TYPES);

// Public predicate — the renderer uses this (via the PropertyRegistry) to
// decide whether a given IR property flows through this engine module.
export function isSizeProperty(type: string): type is SizePropertyType {
  return SIZE_TYPES.has(type);
}

// Apply a single IR property to an existing SizeConfig (mutation is fine —
// the builder creates a fresh config per component).
function applyOne(cfg: SizeConfig, prop: IRPropertyLike): void {
  // AspectRatio has its own shape; branch out first so we never feed it to
  // extractLength (which would return {kind:'unknown'} for the ratio object).
  if (prop.type === 'AspectRatio') {
    const ar = extractAspectRatio(prop.data);
    if (ar) cfg.aspectRatio = ar;   // null -> skip (unrecognised shape)
    return;
  }
  // Every remaining property carries a length-like value — delegate parsing
  // to the shared extractor (handles WidthValue *and* SizeValue envelopes).
  const len: LengthValue = extractLength(prop.data);
  // Drop unparseable values rather than emit garbage CSS.
  if (len.kind === 'unknown') return;
  // Exhaustive switch — if a SizePropertyType is ever added without a case,
  // TypeScript will flag the `never` check in the default branch.
  switch (prop.type as SizePropertyType) {
    case 'Width':          cfg.width = len; break;
    case 'Height':         cfg.height = len; break;
    case 'MinWidth':       cfg.minWidth = len; break;
    case 'MaxWidth':       cfg.maxWidth = len; break;
    case 'MinHeight':      cfg.minHeight = len; break;
    case 'MaxHeight':      cfg.maxHeight = len; break;
    case 'BlockSize':      cfg.blockSize = len; break;
    case 'InlineSize':     cfg.inlineSize = len; break;
    case 'MinBlockSize':   cfg.minBlockSize = len; break;
    case 'MaxBlockSize':   cfg.maxBlockSize = len; break;
    case 'MinInlineSize':  cfg.minInlineSize = len; break;
    case 'MaxInlineSize':  cfg.maxInlineSize = len; break;
    case 'AspectRatio':    /* handled above — unreachable */ break;
    default: {
      // Compile-time exhaustiveness guard — no runtime cost, but catches
      // missing branches during type-checking.
      const _never: never = prop.type as never;
      void _never;
    }
  }
}

// Public entry point — single-pass fold over the component's IR properties.
export function extractSize(properties: IRPropertyLike[]): SizeConfig {
  const cfg: SizeConfig = {};                       // start with no sides set
  for (const p of properties) {                     // single pass
    if (isSizeProperty(p.type)) applyOne(cfg, p);   // narrow + dispatch
  }
  return cfg;
}
