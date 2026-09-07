// ClipPathExtractor.ts — IR -> ClipPathConfig.
// Serialises every shape/function to its CSS form.  Length fields use
// `extractLength` so percentage + px + calc() all work.

import { extractLength, toCssLength } from '../../core/types/LengthValue';          // shared length parser
import { foldLast, type IRPropertyLike } from '../_shared';                          // cascade helper
import type { ClipPathConfig } from './ClipPathConfig';
import { CLIP_PATH_PROPERTY_TYPE } from './ClipPathConfig';

// Helper: convert an IR length-ish value to a CSS string, defaulting to '0'.
function len(raw: unknown): string {
  if (raw === undefined || raw === null) return '0';                                 // missing axis
  if (raw === 'auto') return 'auto';                                                 // rect(auto ...)
  const v = extractLength(raw);                                                      // shared parser
  return v.kind === 'unknown' ? '0' : toCssLength(v);                                // fallback to 0 per spec
}

// CSS Shapes 1 §3.1 `<shape-radius>` = `<length-percentage> | closest-side
// | farthest-side`. The Kotlin parser routes the keyword form as a JSON
// string primitive at the `r` / `rx` / `ry` key (new wire variant added
// in swarm-003 clip-path-borderBox-1a); the length / percent form stays
// as the legacy IRLength object. We emit the keyword verbatim so the
// browser receives e.g. `circle(farthest-side)` instead of the prior
// implicit-`closest-side` fallback (which only coincidentally matched on
// square boxes — see investigation for the silent-correct-on-80x80
// gotcha). Length / percent falls back through `len()` to keep byte-compat
// with every pre-keyword fixture.
function shapeRadius(raw: unknown): string {
  if (typeof raw === 'string') {                                                     // keyword branch
    const k = raw.toLowerCase();
    if (k === 'closest-side' || k === 'farthest-side') return k;                     // canonical only
    return 'closest-side';                                                            // unknown keyword → spec default
  }
  // An ABSENT axis means the author wrote `ellipse()` (or `ellipse(at …)`),
  // whose radii default to `closest-side` per css-shapes-1 §3.1 — the same
  // default the circle branch already used. This used to fall through to
  // `len(undefined)` === '0', i.e. `ellipse(0 0)`: a zero-area clip that
  // erases the element (WPT clip-path-ellipse-006).
  if (raw === undefined || raw === null) return 'closest-side';
  return len(raw);                                                                    // length / percent
}

// The IR property serializer "deep flattens" any object that has a `type`
// discriminator plus EXACTLY ONE other field whose value is an object: the
// nested object's keys are inlined next to `type`
// (converter/src/main/kotlin/app/irmodels/IRPropertySerializer.kt,
// `deepFlatten`).  For clip-path that fires whenever a circle/ellipse
// carries a radius but no `at` clause, or an `at` clause but no radius:
//
//   circle(40px)               -> {type:'circle', px:40}
//   circle(50%)                -> {type:'circle', original:{v:50,u:'PERCENT'}}
//   circle(at 200px 150px)     -> {type:'circle', x:{px:200}, y:{px:150}}
//   circle(50% at 50% 50%)     -> {type:'circle', r:…, pos:{x:…,y:…}}   (2 fields, no flatten)
//
// The two helpers below undo that so every form reads the same.  The
// Compose extractor already handled the flattened radius; web did not, so
// `circle(50%)` was reaching the browser as `circle(closest-side)` — a
// value that only coincidentally agrees on a square box.  IRLength's wire
// keys (`px` / `original`) and Position's (`x` / `y`) are disjoint, so
// which one was inlined is unambiguous.
function flattenedLengthOf(o: Record<string, unknown>): unknown | undefined {
  if (o.px !== undefined) return { px: o.px };                                       // absolute IRLength
  if (o.original !== undefined) return { original: o.original };                     // relative/percent IRLength
  return undefined;                                                                  // nothing inlined here
}

// Position of an `at` clause: the wrapped `pos` object when present, else
// the flattened `{x, y[, xEdge, yEdge]}` inlined next to `type`.
function positionOf(o: Record<string, unknown>): Record<string, unknown> | undefined {
  if (o.pos && typeof o.pos === 'object') return o.pos as Record<string, unknown>;   // normal (unflattened) form
  if (o.x !== undefined && o.y !== undefined) return o;                              // flattened form: read x/y/xEdge/yEdge off the parent
  return undefined;                                                                  // no `at` clause
}

// Serialise one axis of an `at` clause.  `xEdge`/`yEdge` are emitted by the
// Kotlin parser ONLY for the css-values-4 `[right|bottom] <length-percentage>`
// arm — `circle(50% at right 40px bottom 40px)` — because that offset is
// measured from the far edge and cannot be rewritten as a left/top-origin
// length without the reference-box size.  Every other form is already
// normalised to the default origin and carries no edge (see
// ClipPathProperty.Position).  CSS accepts the two-token `right 40px`
// spelling inside `at`, so we hand the browser the same grammar the author
// wrote.
function positionAxis(pos: Record<string, unknown>, axisKey: 'x' | 'y', edgeKey: 'xEdge' | 'yEdge'): string {
  const offset = len(pos[axisKey]);                                                  // length/percent from that edge
  const edge = pos[edgeKey];                                                         // 'right' | 'bottom' | undefined
  return typeof edge === 'string' ? `${edge} ${offset}` : offset;                    // two-token form only when anchored
}

// Full ` at <x> <y>` suffix, or '' when the shape has no `at` clause.
function atClause(o: Record<string, unknown>): string {
  const pos = positionOf(o);
  if (!pos) return '';                                                               // radius-only shape
  return ` at ${positionAxis(pos, 'x', 'xEdge')} ${positionAxis(pos, 'y', 'yEdge')}`;
}

// Helper: single shape function to CSS.  Handles the variants seen in fixtures.
function shapeToCss(o: Record<string, unknown>): string | undefined {
  switch (o.type) {                                                                  // discriminator
    case 'inset': {
      const t = len(o.t), r = len(o.r), b = len(o.b), l = len(o.l);                  // TRBL lengths
      const round = o.round !== undefined ? ` round ${len(o.round)}` : '';           // optional round <r>
      return `inset(${t} ${r} ${b} ${l}${round})`;
    }
    case 'circle': {
      // `r` is a `<shape-radius>`: keyword string OR IRLength object. Use
      // `shapeRadius` to dispatch on type. A keyword is a primitive string
      // so it always stays at the `r` key; a LENGTH radius with no `at`
      // clause gets inlined by deepFlatten, which `flattenedLengthOf`
      // undoes (this is why `circle(50%)` used to render as
      // `circle(closest-side)` — the `original` spelling had no branch).
      // Genuinely radius-less shapes (`circle(at center)`) keep the spec
      // default of closest-side.
      const flatR = o.r !== undefined ? undefined : flattenedLengthOf(o);
      const r = o.r !== undefined ? shapeRadius(o.r)
        : flatR !== undefined ? len(flatR)
        : 'closest-side';
      return `circle(${r}${atClause(o)})`.replace('( ', '(');
    }
    case 'ellipse': {
      // Per-axis dispatch — each axis can independently be a keyword or
      // length / percent. Use `shapeRadius` to preserve keyword strings
      // verbatim while keeping byte-compat for the length / percent forms
      // every existing fixture uses.
      const rx = shapeRadius(o.rx), ry = shapeRadius(o.ry);
      return `ellipse(${rx} ${ry}${atClause(o)})`;
    }
    case 'polygon': {
      if (!Array.isArray(o.points)) return undefined;                                // require points array
      // Per-axis: IRLengthPercentageSerializer (src/main/kotlin/app/irmodels/
      // ValueTypes.kt) encodes a percentage as a raw number (legacy compat)
      // and a length as an IRLength object (`{px: N}` for absolute,
      // `{original:{v,u}}` for relative). Dispatch on the JSON node kind:
      // a primitive number → `<n>%`, an object → use the shared length
      // helper so px/em/rem all flow through the same `toCssLength` path
      // that every other CSS shape function already uses (inset/circle/etc).
      const axis = (raw: unknown): string => {
        if (raw === undefined || raw === null) return '0';                           // missing axis defaults to spec 0
        if (typeof raw === 'number') return `${raw}%`;                               // legacy / percent branch
        if (typeof raw === 'object') return len(raw);                                // length branch → '100px', '1.5em', '50%' (via PERCENT IRLength)
        return '0';                                                                  // defensive: arrays / strings unsupported
      };
      const pts = o.points.map(p => {                                                // serialise each point
        const pt = p as Record<string, unknown>;
        return `${axis(pt.x)} ${axis(pt.y)}`;
      });
      return `polygon(${pts.join(', ')})`;
    }
    case 'rect': {                                                                   // css-shapes-2 rect()
      // `round <r>` is optional and was previously dropped on the floor
      // here even when the IR carried it; mirror the xywh() branch below.
      const tail = o.round !== undefined ? ` round ${len(o.round)}` : '';
      return `rect(${len(o.t)} ${len(o.r)} ${len(o.b)} ${len(o.l)}${tail})`;
    }
    case 'xywh': {                                                                   // CSS Shapes 2 xywh()
      const tail = o.round !== undefined ? ` round ${len(o.round)}` : '';
      return `xywh(${len(o.x)} ${len(o.y)} ${len(o.w)} ${len(o.h)}${tail})`;
    }
    case 'path': {
      if (typeof o.d !== 'string') return undefined;
      // css-shapes-1 §3.1 `path( <fill-rule>? , <string> )`. The rule is a
      // separate IR field (`rule`) since wave 37 — it used to be swallowed
      // into `d`, which produced an unparseable `path("nonzero, 'M…'")`.
      const rule = typeof o.rule === 'string' && o.rule.length > 0 ? `${o.rule}, ` : '';
      return `path(${rule}"${o.d}")`;                                                  // SVG d passed verbatim
    }
    case 'shape': {                                                                   // css-shapes-2 §4 shape()
      // `fn` is the whole call including the `shape(` prefix — nothing in
      // the segment list can be pre-computed without the reference box, so
      // the Kotlin parser stores it verbatim (same contract as path()'s
      // `d`) and we hand it to the browser unchanged.
      return typeof o.fn === 'string' && o.fn.length > 0 ? o.fn : undefined;
    }
    default: return undefined;                                                         // unknown shape
  }
}

// Entry-point per-property parser.  Handles the `{geometry-box, shape?}` wrapper
// that the Kotlin parser emits for combined box+shape values.
function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                         // absent
  if (typeof data === 'string') {                                                    // 'none' | '#id' | url ref
    return data;
  }
  if (typeof data !== 'object') return undefined;                                    // unknown primitive
  const o = data as Record<string, unknown>;
  // Combined form: {"geometry-box":"border-box"[, shape]}
  if (typeof o['geometry-box'] === 'string') {
    const box = o['geometry-box'] as string;
    if (o.shape && typeof o.shape === 'object') {
      const s = shapeToCss(o.shape as Record<string, unknown>);
      return s ? `${s} ${box}` : box;                                                 // CSS: <shape> <box>
    }
    return box;                                                                       // box keyword alone
  }
  // Bare shape: delegate.
  return shapeToCss(o);
}

export function extractClipPath(properties: IRPropertyLike[]): ClipPathConfig {
  return { value: foldLast(properties, CLIP_PATH_PROPERTY_TYPE, parseOne) };
}
