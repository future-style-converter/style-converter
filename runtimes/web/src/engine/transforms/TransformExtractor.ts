// TransformExtractor.ts — IR -> TransformConfig.
// Serialises the ordered list of transform functions verbatim into the CSS
// function-call syntax.  Function inventory mirrors
// parsing/css/properties/longhands/transforms/TransformPropertyParser.kt:
//   translate / translateX / translateY / translateZ / translate3d
//   rotate / rotateX / rotateY / rotateZ / rotate3d
//   scale / scaleX / scaleY / scaleZ / scale3d
//   skew / skewX / skewY
//   matrix / matrix3d
//   perspective(<length>)
// Order is preserved per spec (CSS Transforms 1 §2.2 — matrix mul is left-to-right).

import { extractLength, toCssLength } from '../core/types/LengthValue';            // shared length parser
import { extractAngle } from '../core/types/AngleValue';                           // shared angle parser
import { foldLast, type IRPropertyLike } from '../effects/_shared';                 // cascade helper
import type { TransformConfig } from './TransformConfig';
import { TRANSFORM_PROPERTY_TYPE } from './TransformConfig';

// Helper: parse a possibly-length arg and emit CSS string, defaulting to "0".
function lenOrZero(raw: unknown): string {
  const v = extractLength(raw);                                                    // shared parser
  return v.kind === 'unknown' ? '0' : toCssLength(v);                              // spec default 0
}

// Helper: parse an angle and emit "<deg>deg", defaulting to 0deg.
function angDeg(raw: unknown): string {
  const a = extractAngle(raw);                                                     // returns degrees or null
  return `${a ? a.degrees : 0}deg`;                                                // CSS requires unit
}

// Serialise ONE transform function record to its CSS token.
// Returns undefined if the IR carries a function we don't recognise — caller
// filters it out so we never emit a syntactically invalid `transform` value.
function fnToCss(raw: unknown): string | undefined {
  if (!raw || typeof raw !== 'object') return undefined;                           // defensive
  const f = raw as Record<string, unknown>;                                        // widened access
  switch (f.fn) {                                                                   // discriminator
    case 'translate':    return `translate(${lenOrZero(f.x)}, ${lenOrZero(f.y)})`;  // 2 lengths
    case 'translateX':   return `translateX(${lenOrZero(f.x)})`;                    // single axis
    case 'translateY':   return `translateY(${lenOrZero(f.y)})`;
    case 'translateZ':   return `translateZ(${lenOrZero(f.z)})`;                    // 3D z-offset
    case 'translate3d':  return `translate3d(${lenOrZero(f.x)}, ${lenOrZero(f.y)}, ${lenOrZero(f.z)})`;
    case 'rotate':       return `rotate(${angDeg(f.a)})`;                           // 2D rotate
    case 'rotateX':      return `rotateX(${angDeg(f.a)})`;                          // about X axis
    case 'rotateY':      return `rotateY(${angDeg(f.a)})`;                          // about Y axis
    case 'rotateZ':      return `rotateZ(${angDeg(f.a)})`;                          // about Z (== rotate)
    case 'rotate3d':     return `rotate3d(${f.x ?? 0}, ${f.y ?? 0}, ${f.z ?? 0}, ${angDeg(f.a)})`;
    case 'scale':        return `scale(${f.x ?? 1}, ${f.y ?? f.x ?? 1})`;           // 2 factors (y mirrors x)
    case 'scaleX':       return `scaleX(${f.x ?? 1})`;                              // single axis scale
    case 'scaleY':       return `scaleY(${f.y ?? 1})`;
    case 'scaleZ':       return `scaleZ(${f.z ?? 1})`;                              // 3D scale z
    case 'scale3d':      return `scale3d(${f.x ?? 1}, ${f.y ?? 1}, ${f.z ?? 1})`;   // 3 factors
    case 'skew':         return `skew(${angDeg(f.x)}, ${angDeg(f.y)})`;             // 2 angles
    case 'skewX':        return `skewX(${angDeg(f.x)})`;
    case 'skewY':        return `skewY(${angDeg(f.y)})`;
    case 'matrix': {                                                                // 6 numbers
      const vs = [f.a ?? 1, f.b ?? 0, f.c ?? 0, f.d ?? 1, f.e ?? 0, f.f ?? 0];
      return `matrix(${vs.join(', ')})`;
    }
    case 'matrix3d': {                                                              // 16 numbers
      const keys = ['a1','b1','c1','d1','a2','b2','c2','d2','a3','b3','c3','d3','a4','b4','c4','d4'];
      const vs = keys.map((k, i) => f[k] ?? (i === 0 || i === 5 || i === 10 || i === 15 ? 1 : 0));
      return `matrix3d(${vs.join(', ')})`;
    }
    case 'perspective':  return `perspective(${lenOrZero(f.l)})`;                    // inline perspective
    default:             return undefined;                                           // unknown fn → drop
  }
}

// ── wave-40 lane T1: the CSS-WIDE KEYWORD leg ────────────────────────────────
//
// `TransformPropertyParser.kt` routes any GlobalKeywords hit to the wire's
// THIRD variant, `TransformValue.Keyword` → `{type:'keyword', keyword:'…'}`
// (see irmodels/properties/transforms/TransformProperty.kt). Only `functions`
// and `expression` were read here, so `transform: inherit` — the one form a
// browser resolves for free — was silently DROPPED.
//
// MEASURED (wave39-final, css-transforms/css-transform-inherit-scale): the
// green child declares `transform: inherit` to pick up its parent's
// `scale(2)`; without it the child stayed 50×50 inside a 2×-scaled yellow
// parent, so ~12.5 % of the canvas painted YELLOW where the ref
// (ref-filled-green-200px-square.html) is solid green — mean ΔE 9.05, the
// colour-mass veto's exact signature (web-ref 0.9988 SSIM yet wptPass=false).
//
// The set is CLOSED to the CSS Cascade 5 §7 wide keywords: those are the only
// strings a browser resolves natively, and passing an arbitrary wire string
// into an inline style would be a silent fallthrough. `revert-layer` is
// included because GlobalKeywords accepts it; an unknown keyword returns
// undefined and the declaration is dropped exactly as before.
const CSS_WIDE_KEYWORDS = new Set(['inherit', 'initial', 'unset', 'revert', 'revert-layer']);

// Parse ONE `Transform` IR payload into its CSS value (or undefined to skip).
function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                        // absent
  if (typeof data === 'string') return data;                                        // raw calc() passthrough
  if (typeof data !== 'object') return undefined;                                   // unknown primitive
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                             // explicit CSS keyword
  if (o.type === 'expression' && typeof o.expr === 'string') return o.expr;         // pre-normalised calc
  if (o.type === 'keyword' && typeof o.keyword === 'string') {                       // CSS-wide keyword wire
    const kw = o.keyword.trim().toLowerCase();                                       // parser emits lowercase
    if (kw === 'none') return 'none';                                                // defensive: same as above
    return CSS_WIDE_KEYWORDS.has(kw) ? kw : undefined;                               // closed set, no fallthrough
  }
  if (o.type === 'functions' && Array.isArray(o.list)) {                             // the common case
    const tokens = o.list.map(fnToCss).filter((t): t is string => t !== undefined); // drop unknown fns
    if (tokens.length === 0) return 'none';                                         // empty list == no-op
    return tokens.join(' ');                                                         // spec: space-separated
  }
  return undefined;                                                                  // unrecognised shape
}

// Entry point — last-write-wins across all Transform declarations.
export function extractTransform(properties: IRPropertyLike[]): TransformConfig {
  return { value: foldLast(properties, TRANSFORM_PROPERTY_TYPE, parseOne) };
}
