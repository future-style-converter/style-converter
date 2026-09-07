// _dispatch.ts — Phase-8 visibility + overflow dispatch (5 properties:
// Visibility + the four overflow LONGHANDS the converter can emit).
import type { CSSProperties } from 'react';

import { extractVisibility } from './VisibilityExtractor';
import { applyVisibility } from './VisibilityApplier';
import { extractOverflowX } from './OverflowXExtractor';
import { applyOverflowX } from './OverflowXApplier';
import { extractOverflowY } from './OverflowYExtractor';
import { applyOverflowY } from './OverflowYApplier';
import { extractOverflowBlock } from './OverflowBlockExtractor';
import { applyOverflowBlock } from './OverflowBlockApplier';
import { extractOverflowInline } from './OverflowInlineExtractor';
import { applyOverflowInline } from './OverflowInlineApplier';

interface IRPropertyLike { type: string; data: unknown }

export function applyVisibilityPhase8(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  // The `overflow` SHORTHAND never reaches this dispatch: ShorthandRegistry.kt:88
  // maps 'overflow' to OverflowExpander, and PropertiesParser expands shorthands
  // before the longhand registry runs, so the wire only ever carries the
  // OverflowX/Y (and logical Block/Inline) longhands — css-overflow-3 defines
  // `overflow` as a shorthand for overflow-x/overflow-y. The Overflow triplet
  // that used to be dispatched here was unreachable and is deleted (A6#9).
  Object.assign(out, applyVisibility(extractVisibility(properties)));
  Object.assign(out, applyOverflowX(extractOverflowX(properties)));
  Object.assign(out, applyOverflowY(extractOverflowY(properties)));
  Object.assign(out, applyOverflowBlock(extractOverflowBlock(properties)));
  Object.assign(out, applyOverflowInline(extractOverflowInline(properties)));
  return out;
}
