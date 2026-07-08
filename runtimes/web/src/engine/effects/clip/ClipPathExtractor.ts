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
  return len(raw);                                                                    // length / percent / undefined
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
      // `shapeRadius` to dispatch on type. Flattened-px form (`{type:
      // "circle", px: N}`) still works through the pre-existing `o.px`
      // branch since deepFlatten only fires when there's a single
      // object-valued non-type field — a keyword is a primitive string
      // and stays at the `r` key. Empty-radius / no-radius case
      // (`{type:"circle"}` with no `r` and no `px`) keeps the prior
      // implicit-closest-side default so existing fixtures that relied
      // on the missing-radius behaviour don't shift bytes.
      const r = o.r !== undefined
        ? shapeRadius(o.r)
        : (typeof o.px === 'number' ? `${o.px}px` : 'closest-side');
      const pos = o.pos as Record<string, unknown> | undefined;
      const at = pos ? ` at ${len(pos.x)} ${len(pos.y)}` : '';                       // optional 'at x y'
      return `circle(${r}${at})`.replace('( ', '(');
    }
    case 'ellipse': {
      // Per-axis dispatch — each axis can independently be a keyword or
      // length / percent. Use `shapeRadius` to preserve keyword strings
      // verbatim while keeping byte-compat for the length / percent forms
      // every existing fixture uses.
      const rx = shapeRadius(o.rx), ry = shapeRadius(o.ry);
      const pos = o.pos as Record<string, unknown> | undefined;
      const at = pos ? ` at ${len(pos.x)} ${len(pos.y)}` : '';
      return `ellipse(${rx} ${ry}${at})`;
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
    case 'rect': {                                                                   // deprecated-shape-fn form
      return `rect(${len(o.t)} ${len(o.r)} ${len(o.b)} ${len(o.l)})`;
    }
    case 'xywh': {                                                                   // CSS Shapes 2 xywh()
      const tail = o.round !== undefined ? ` round ${len(o.round)}` : '';
      return `xywh(${len(o.x)} ${len(o.y)} ${len(o.w)} ${len(o.h)}${tail})`;
    }
    case 'path': {
      return typeof o.d === 'string' ? `path("${o.d}")` : undefined;                  // pass SVG d verbatim
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
