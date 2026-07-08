// AlignSelfApplier.ts — emits a CSS declaration for the `alignSelf` property.
// Standard property — maps 1:1 to `CSSProperties['alignSelf']`.  Spec: CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-self.

import type { CSSProperties } from 'react';
import type { AlignSelfConfig } from './AlignSelfConfig';

export type AlignSelfStyles = Pick<CSSProperties, 'alignSelf'>;

export function applyAlignSelf(config: AlignSelfConfig): AlignSelfStyles {
  if (config.value === undefined) return {};                         // unset
  // CSS Anchor Positioning Level 1 §6 fallback — when there is no default
  // anchor in scope (the only case SC supports today, since we have no
  // anchor-positioning runtime), `anchor-center` MUST resolve to `center`.
  // Folding here keeps the IR truthful (parser still emits ANCHOR_CENTER)
  // while emitting CSS that browsers will lay out identically to the spec
  // ref (`align-self: center`).  Matches the AlignSelfApplier behaviour on
  // Android (FlexExtractor.parseAlignSelf) and iOS (FlexboxExtractor.mapAlignment).
  const out = config.value === 'anchor-center' ? 'center' : config.value;
  return { alignSelf: out } as AlignSelfStyles;                             // typed single-key
}
