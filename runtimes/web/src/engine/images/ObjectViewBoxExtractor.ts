// ObjectViewBoxExtractor.ts — folds IR `ObjectViewBox` into a CSS
// `<basic-shape-rect>` string (css-images-4 §5.3 object-view-box).
//
// WAVE-36 LANE M1 — same shape bug as its ObjectPosition sibling. The IR
// value is a structured basic shape, `{type:'inset', top, right, bottom,
// left}`, not a flat keyword — so `keywordOrRaw` found no
// `.keyword`/`.value`/`.raw` at the top level, returned undefined, and every
// `inset()` view box was dropped on the floor. Only `none` (the initial
// value, which serializes as a bare `{type:'none'}` tag) ever survived.
//
// `inset` and `none` are the WHOLE IR vocabulary here — ObjectViewBoxValue
// (converter/.../images/ObjectViewBoxProperty.kt) is a two-arm sealed
// interface, and the parser returns null for the spec's `rect()` / `xywh()`
// spellings, so those never reach the wire at all. This file deliberately
// does NOT anticipate them: a branch for a shape the producer cannot emit is
// untested code that would silently start deciding pixels the day the parser
// grows the arm.
import { foldLast, keywordOrRaw, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectViewBoxConfig } from './ObjectViewBoxConfig';

/** The four edge offsets of `inset()`, in CSS's clockwise order. */
const INSET_EDGES = ['top', 'right', 'bottom', 'left'] as const;

function parseObjectViewBox(data: unknown): string | undefined {
  // `none` and any flat keyword arm still ride the shared passthrough.
  if (typeof data === 'string') return keywordOrRaw(data);
  if (data === null || data === undefined || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type !== 'inset') return keywordOrRaw(data);
  const parts: string[] = [];
  for (const edge of INSET_EDGES) {
    const v = lengthOrKeyword(o[edge]);
    // All four edges or none: a partial emission would move the view box
    // somewhere the author never wrote, so a missing edge drops the whole
    // declaration and the browser keeps the `none` initial.
    if (v === undefined) return undefined;
    parts.push(v);
  }
  return `inset(${parts.join(' ')})`;
}

export function extractObjectViewBox(properties: IRPropertyLike[]): ObjectViewBoxConfig {
  return { value: foldLast(properties, 'ObjectViewBox', parseObjectViewBox) };
}
