// ObjectViewBoxExtractor.ts — folds IR `ObjectViewBox` into a CSS
// `<basic-shape-rect>` string (css-images-5 §3.1 object-view-box).
//
// WAVE-36 LANE M1 — same shape bug as its ObjectPosition sibling. The IR
// value is a structured basic shape, `{type:'inset', top, right, bottom,
// left}`, not a flat keyword — so `keywordOrRaw` found no
// `.keyword`/`.value`/`.raw` at the top level, returned undefined, and every
// `inset()` view box was dropped on the floor. Only `none` (the initial
// value, which serializes as a bare `{type:'none'}` tag) ever survived.
//
// WAVE-37 LANE W2 — the other two <basic-shape-rect> spellings.
// The wave-36 note below said `inset` and `none` were the WHOLE IR
// vocabulary "because the parser returns null for rect()/xywh()". That was
// true and it was the bug: css-shapes-1 §3.2 gives <basic-shape-rect> three
// equal spellings, and dropping two of them made object-view-box-rect,
// -xywh and their percentage twins paint the un-cropped source image (ssim
// 0.9740, coverage veto, all four). The converter now emits `{type:'rect'}`
// and `{type:'xywh'}`, and this file grew the matching arms — deliberately
// TABLE-DRIVEN off the IR field names so the producer and consumer cannot
// disagree about argument order.
import { foldLast, keywordOrRaw, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectViewBoxConfig } from './ObjectViewBoxConfig';

/** IR `type` discriminator → the payload keys, in CSS argument order.
 *  `inset` is clockwise edges; `rect` is the same four edges (css-shapes-1
 *  §3.2 keeps inset()'s order); `xywh` is origin then size. */
const SHAPE_ARGS: Record<string, readonly string[]> = {
  inset: ['top', 'right', 'bottom', 'left'],
  rect:  ['top', 'right', 'bottom', 'left'],
  xywh:  ['x', 'y', 'width', 'height'],
};

function parseObjectViewBox(data: unknown): string | undefined {
  // `none` and any flat keyword arm still ride the shared passthrough.
  if (typeof data === 'string') return keywordOrRaw(data);
  if (data === null || data === undefined || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const fn = typeof o.type === 'string' ? o.type : '';
  const args = SHAPE_ARGS[fn];
  if (!args) return keywordOrRaw(data);
  const parts: string[] = [];
  for (const key of args) {
    const v = shapeArgCss(o[key]);
    // All four arguments or none: a partial emission would move the view box
    // somewhere the author never wrote, so a missing one drops the whole
    // declaration and the browser keeps the `none` initial.
    if (v === undefined) return undefined;
    parts.push(v);
  }
  return `${fn}(${parts.join(' ')})`;
}

/** One `<length-percentage>` argument → CSS text.
 *  IRLengthPercentage.Percentage serializes as a BARE NUMBER (the legacy
 *  `{x:50,y:50}` wire form every platform extractor was coded against), so a
 *  raw number here means percent — `keywordOrRaw` would stringify it to a
 *  unitless `50`, which is not a <length-percentage> and invalidates the whole
 *  function. Lengths keep the object form and go through the shared helper. */
function shapeArgCss(v: unknown): string | undefined {
  if (typeof v === 'number') return `${v}%`;
  return lengthOrKeyword(v);
}

export function extractObjectViewBox(properties: IRPropertyLike[]): ObjectViewBoxConfig {
  return { value: foldLast(properties, 'ObjectViewBox', parseObjectViewBox) };
}
