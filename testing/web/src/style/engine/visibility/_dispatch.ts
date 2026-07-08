// _dispatch.ts — Phase-8 visibility + overflow dispatch (6 properties).
import type { CSSProperties } from 'react';

import { extractVisibility } from './VisibilityExtractor';
import { applyVisibility } from './VisibilityApplier';
import { extractOverflow } from './OverflowExtractor';
import { applyOverflow } from './OverflowApplier';
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
  // Note: if `Overflow` shorthand and `OverflowX/Y` coexist, last-write-wins at
  // the object level means OverflowX/Y take precedence (assigned later).  CSS
  // parser expands the two-value shorthand, so normally we only see one form.
  Object.assign(out, applyVisibility(extractVisibility(properties)));
  Object.assign(out, applyOverflow(extractOverflow(properties)));
  Object.assign(out, applyOverflowX(extractOverflowX(properties)));
  Object.assign(out, applyOverflowY(extractOverflowY(properties)));
  Object.assign(out, applyOverflowBlock(extractOverflowBlock(properties)));
  Object.assign(out, applyOverflowInline(extractOverflowInline(properties)));
  return out;
}
