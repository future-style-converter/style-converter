// PaddingExtractor.ts — folds a list of Padding* IR properties into a PaddingConfig.
// Consumes any mix of physical (PaddingTop/Right/Bottom/Left) and logical
// (PaddingBlockStart/End, PaddingInlineStart/End) longhands.  The shorthand
// `padding: ...` is already expanded upstream by the Kotlin CSS parser, so we
// only ever see per-side longhands here.

import { extractLength, type LengthValue } from '../core/types/LengthValue';
import type { PaddingConfig, PaddingPropertyType } from './PaddingConfig';

// Minimal shape of an IR property; mirrors IRModels.IRProperty but kept local
// to avoid coupling the engine module to the legacy types directory.
interface IRPropertyLike { type: string; data: unknown; }

// Set for O(1) membership tests — drives `isPaddingProperty` below.
const PADDING_TYPES = new Set<string>([
  'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft',
  'PaddingBlockStart', 'PaddingBlockEnd', 'PaddingInlineStart', 'PaddingInlineEnd',
]);

// Predicate for the renderer: "is this property handled by the padding pipeline?"
export function isPaddingProperty(type: string): type is PaddingPropertyType {
  return PADDING_TYPES.has(type);
}

// Apply a single IR property to an existing PaddingConfig (mutation for speed;
// the builder creates a fresh config per component so no aliasing concerns).
function applyOne(config: PaddingConfig, prop: IRPropertyLike): void {
  // Delegate IR-shape handling to the shared primitive extractor.
  const length: LengthValue = extractLength(prop.data);
  // A {kind:'unknown'} means the IR is unrecognisable — skip rather than emit garbage.
  if (length.kind === 'unknown') return;
  // {kind:'auto'} is nonsense for padding (CSS doesn't accept auto there); drop it.
  if (length.kind === 'auto') return;
  // Exhaustive switch — TS will error if a new PaddingPropertyType is added.
  switch (prop.type as PaddingPropertyType) {
    case 'PaddingTop':          config.top = length; break;
    case 'PaddingRight':        config.right = length; break;
    case 'PaddingBottom':       config.bottom = length; break;
    case 'PaddingLeft':         config.left = length; break;
    case 'PaddingBlockStart':   config.blockStart = length; break;
    case 'PaddingBlockEnd':     config.blockEnd = length; break;
    case 'PaddingInlineStart':  config.inlineStart = length; break;
    case 'PaddingInlineEnd':    config.inlineEnd = length; break;
    default: {
      // Compile-time exhaustiveness guard — unreachable at runtime.
      const _never: never = prop.type as never;
      void _never;
    }
  }
}

// Public entrypoint — iterate, pick padding props, fold into a config.
// Returns an empty config if the component has no padding set.
export function extractPadding(properties: IRPropertyLike[]): PaddingConfig {
  const cfg: PaddingConfig = {};                                      // start blank
  for (const p of properties) {                                       // single pass
    if (isPaddingProperty(p.type)) applyOne(cfg, p);                  // narrow + apply
  }
  return cfg;
}
