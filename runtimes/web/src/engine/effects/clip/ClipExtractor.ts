// ClipExtractor.ts — IR -> ClipConfig.
import { extractLength, toCssLength } from '../../core/types/LengthValue';
import { foldLast, type IRPropertyLike } from '../_shared';
import type { ClipConfig } from './ClipConfig';
import { CLIP_PROPERTY_TYPE } from './ClipConfig';

function len(raw: unknown): string {
  // CSS 2.1 §11.1.2 `rect()` sides are `<length> | auto`, and the Kotlin
  // parser encodes `auto` as an ABSENT field (ClipPropertyParser: "we
  // surface `auto` as a null IR field so per-platform appliers can resolve
  // against actual draw bounds"). This helper used to map an absent field
  // to '0' via the `unknown` fallback, so `clip: rect(auto, auto, auto,
  // auto)` — a no-op clip that must show the whole box — reached the
  // browser as `rect(0, 0, 0, 0)`, an empty region that hid the element
  // entirely. WPT clip-rect-auto-001/002 fail on exactly that, and -004 /
  // -005 lose their one auto side the same way.
  if (raw === undefined || raw === null) return 'auto';                              // absent side == auto
  if (raw === 'auto') return 'auto';                                                 // explicit spelling, if a fixture uses it
  const v = extractLength(raw);                                                      // shared
  return v.kind === 'unknown' ? 'auto' : toCssLength(v);                             // unparseable: auto, never a silent 0
}

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                           // absent/unknown
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';                                              // keyword
  if (o.type === 'rect') {                                                           // rect(T,R,B,L)
    return `rect(${len(o.top)}, ${len(o.right)}, ${len(o.bottom)}, ${len(o.left)})`; // comma per CSS 2.1
  }
  return undefined;
}

export function extractClip(properties: IRPropertyLike[]): ClipConfig {
  return { value: foldLast(properties, CLIP_PROPERTY_TYPE, parseOne) };
}
