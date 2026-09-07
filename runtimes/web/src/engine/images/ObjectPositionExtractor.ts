// ObjectPositionExtractor.ts — folds IR `ObjectPosition` into a CSS
// `<position>` string (css-images-3 §4.3.1).
//
// WAVE-36 LANE M1 — WHY THIS IS NO LONGER `keywordOrRaw`. The IR value for
// object-position is NOT a flat keyword: ObjectPositionProperty.kt emits a
// `Position` capsule, `{ x: <axis value>, y: <axis value> }`, one entry per
// axis. `keywordOrRaw` looks for `.keyword` / `.value` / `.raw` on the top
// object, finds none of them there, and returns `undefined` — so the fold
// dropped EVERY object-position declaration in the corpus and the browser
// painted the `50% 50%` initial value on all of them. In the css-images
// `object-fit-*` family that is six of the seven boxes in every row
// (`top right`, `bottom left`, `top 25% left 25%`, …) collapsing onto one
// another, which no amount of correct object-fit could recover.
//
// The five axis variants below are the serialized forms of
// ObjectPositionProperty.ObjectPositionValue's sealed arms; nothing else can
// appear, and an unrecognised arm returns undefined so the fold skips the
// declaration rather than emitting half a position.
import { foldLast, kebab, lengthOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectPositionConfig } from './ObjectPositionConfig';

/** One axis of the `Position` capsule, or undefined when unrecognised. */
function axisToCss(v: unknown): string | undefined {
  if (v === null || v === undefined || typeof v !== 'object') return undefined;
  const o = v as Record<string, unknown>;
  switch (o.type) {
    // `{type:'keyword', value:'TOP'}` — a bare left/center/right/top/bottom.
    case 'keyword':
      return kebab(o.value);
    // `{type:'keyword-offset', keyword:'BOTTOM', offset:<length>}` — the
    // §5.2 `[left|right] <length-percentage>` edge-relative form. Offsets
    // arrive as `{px:N}` (absolutized) or `{original:{v,u}}` (percentages,
    // which resolve against box − content size and so stay symbolic).
    case 'keyword-offset': {
      const k = kebab(o.keyword);
      const off = lengthOrKeyword(o.offset);
      return k && off ? `${k} ${off}` : undefined;
    }
    // `{type:'percentage', percentage:N}` — a bare percentage component.
    case 'percentage':
      return typeof o.percentage === 'number' ? `${o.percentage}%` : undefined;
    // `{type:'length', px:N}` — a bare absolute length. The Kotlin sealed
    // arm wraps an IRLength, and the two serializations seen on the wire
    // are the flattened `{px}` and the nested `{length:{…}}`; both go
    // through the shared length helper.
    case 'length':
      return lengthOrKeyword('length' in o ? o.length : o);
    default:
      return undefined;
  }
}

/** `global` (inherit/initial/…) and `raw` (var()/calc()/unparseable) are
 *  WHOLE-VALUE arms: the parser stores the same object on BOTH axes, so
 *  joining them would emit `inherit inherit`. Detected first, emitted once. */
function wholeValue(v: unknown): string | undefined {
  if (v === null || v === undefined || typeof v !== 'object') return undefined;
  const o = v as Record<string, unknown>;
  if (o.type !== 'global' && o.type !== 'raw') return undefined;
  return typeof o.value === 'string' && o.value.length > 0 ? o.value : undefined;
}

function parseObjectPosition(data: unknown): string | undefined {
  if (data === null || data === undefined || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const whole = wholeValue(o.x);
  if (whole !== undefined) return whole;
  const x = axisToCss(o.x);
  const y = axisToCss(o.y);
  // Both halves or nothing: a one-axis emission would silently re-centre the
  // other, which is a different wrong answer, not a partial right one.
  return x !== undefined && y !== undefined ? `${x} ${y}` : undefined;
}

export function extractObjectPosition(properties: IRPropertyLike[]): ObjectPositionConfig {
  return { value: foldLast(properties, 'ObjectPosition', parseObjectPosition) };
}
