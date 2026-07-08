// _filter_shared.ts — shared serialiser for filter-function chains, reused by
// both Filter and BackdropFilter (identical grammar per Filter Effects §18.1).

import { extractLength, toCssLength } from '../../core/types/LengthValue';          // length parser
import { extractAngle } from '../../core/types/AngleValue';                         // angle parser
import { extractColor } from '../../core/types/ColorValue';                          // color parser
import { colorToCss } from '../../color/DynamicColorCss';                            // CSS color emitter

// Serialise one filter-function object.  IR numeric `v` is already a percentage
// value (100 == 100% == identity), matching the CSS serialisation.
export function filterFnToCss(raw: unknown): string | undefined {
  if (!raw || typeof raw !== 'object') return undefined;                            // defensive
  const f = raw as Record<string, unknown>;                                         // widen
  switch (f.fn) {                                                                    // discriminator
    case 'blur': {                                                                   // blur(<length>)
      const r = extractLength(f.r);                                                  // shared length
      return `blur(${r.kind === 'unknown' ? '0' : toCssLength(r)})`;
    }
    case 'brightness': return `brightness(${f.v ?? 100}%)`;                          // %-based
    case 'contrast':   return `contrast(${f.v ?? 100}%)`;
    case 'grayscale':  return `grayscale(${f.v ?? 0}%)`;
    case 'saturate':   return `saturate(${f.v ?? 100}%)`;
    case 'sepia':      return `sepia(${f.v ?? 0}%)`;
    case 'invert':     return `invert(${f.v ?? 0}%)`;
    case 'opacity':    return `opacity(${f.v ?? 100}%)`;
    case 'hue-rotate': {                                                             // hue-rotate(<angle>)
      const a = extractAngle(f.a);                                                   // degrees
      return `hue-rotate(${a ? a.degrees : 0}deg)`;
    }
    case 'drop-shadow': {                                                            // drop-shadow(<off-x> <off-y> [<blur>] [<color>])
      const x = extractLength(f.x), y = extractLength(f.y);                          // required offsets
      const tokens: string[] = [
        x.kind === 'unknown' ? '0' : toCssLength(x),                                 // default 0 per spec
        y.kind === 'unknown' ? '0' : toCssLength(y),
      ];
      if (f.r !== undefined) {                                                       // optional blur
        const r = extractLength(f.r);
        if (r.kind !== 'unknown') tokens.push(toCssLength(r));
      }
      if (f.c !== undefined) {                                                       // optional color (IR legacy 'c' key)
        const c = extractColor(f.c);
        if (c.kind !== 'unknown') tokens.push(colorToCss(c));
      }
      return `drop-shadow(${tokens.join(' ')})`;
    }
    default: return undefined;                                                        // unknown filter fn: drop
  }
}

// Serialise a top-level IR value into a CSS filter string.  Accepts the same
// shape flavors for both `filter` and `backdrop-filter`.
export function filterValueToCss(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                         // absent
  if (typeof data === 'string') return data;                                         // 'none' / pre-rendered
  if (Array.isArray(data)) {                                                         // fn list (common)
    if (data.length === 0) return 'none';                                            // parser-gap: [] == none
    const tokens = data.map(filterFnToCss).filter((t): t is string => t !== undefined);
    return tokens.length ? tokens.join(' ') : 'none';                                // space-separated chain
  }
  if (typeof data === 'object') {                                                    // {url:'#id'} form
    const o = data as Record<string, unknown>;
    if (typeof o.url === 'string') return `url("${o.url}")`;                         // SVG filter reference
  }
  return undefined;
}
