// _shared.ts — Phase-9 animations/transitions/timeline primitives.
//
// Every file in this folder stays ≤200 lines by delegating common parsing
// helpers here.  The key mental model:
//
//   1. Parsers emit mostly list-shaped IR (one entry per comma-separated CSS
//      value).  Web serialises the list verbatim, join on ", " — native CSS
//      has supported animation-* lists since 2012.
//   2. A handful of properties ship with only partial csstype coverage (view
//      transitions L2, scroll-timeline L2, animation-timeline L2, transition-
//      behavior, timeline-scope).  Appliers for those return
//      `Record<string,string>` and Object.assign into the React CSSProperties
//      accumulator — same pattern Phase 7 used for anchor-positioning.
//   3. Last-write-wins cascade is enforced by `foldLast` (imported from the
//      effects shared helpers) so multi-declaration blocks behave like the
//      browser does.

import { foldLast, type IRPropertyLike } from '../effects/_shared';
import { extractLength, toCssLength } from '../core/types/LengthValue';

export { foldLast };
export type { IRPropertyLike };

// Lower-case a SHOUTY_SNAKE IR enum (AnimationDirection, AnimationFillMode,
// AnimationPlayState, Axis, ...) into the CSS token.  Underscores become
// hyphens: "ALTERNATE_REVERSE" -> "alternate-reverse".
export function kebabEnum(v: unknown): string | undefined {
  if (typeof v !== 'string' || v.length === 0) return undefined;
  return v.toLowerCase().replace(/_/g, '-');
}

// Kebab a LIST of SHOUTY_SNAKE enums (what AnimationDirection etc. emit) into
// a comma-joined CSS value.  Returns undefined on empty input so the caller
// can skip setting the declaration entirely.
export function kebabEnumList(v: unknown): string | undefined {
  if (!Array.isArray(v) || v.length === 0) return undefined;
  const parts = v.map(kebabEnum).filter((s): s is string => !!s);
  return parts.length === 0 ? undefined : parts.join(', ');
}

// IRTime record -> CSS token.  IR always normalises to milliseconds in `ms`;
// we emit "ms" verbatim.  If `original.v+u` is present and the unit is S, we
// restore seconds to keep the output round-trippable with authored CSS.
export function timeToCss(t: unknown): string | undefined {
  if (typeof t === 'number') return `${t}ms`;                                   // bare number fallback
  if (!t || typeof t !== 'object') return undefined;
  const o = t as Record<string, unknown>;
  const orig = o.original as Record<string, unknown> | undefined;
  if (orig && typeof orig.v === 'number' && orig.u === 'S') return `${orig.v}s`; // round-trip seconds
  if (typeof o.ms === 'number') return `${o.ms}ms`;                             // normalised form
  return undefined;
}

// Parse an IR list-of-times (AnimationDelay, AnimationDuration.Durations,
// TransitionDuration, TransitionDelay).  Returns the CSS list value or
// undefined for empty/invalid lists.
export function timeListToCss(arr: unknown): string | undefined {
  if (!Array.isArray(arr) || arr.length === 0) return undefined;
  const parts = arr.map(timeToCss).filter((s): s is string => !!s);
  return parts.length === 0 ? undefined : parts.join(', ');
}

// Serialise ONE timing-function IR record to CSS.  Handles every variant the
// parser emits today — cubic-bezier (4-number tuple), steps (object with
// n + pos), linear (list of stops), and the plain-keyword passthrough via
// the `original` string hint.
export function timingFunctionToCss(raw: unknown): string | undefined {
  if (typeof raw === 'string') return raw;
  if (!raw || typeof raw !== 'object') return undefined;
  const o = raw as Record<string, unknown>;
  // `original` is set to a keyword ("ease", "step-start"...) when the author
  // wrote one — we round-trip to keep CSS legible.  The parser fills `cb`
  // with the keyword's canonical bezier too, but keywords read better.
  if (typeof o.original === 'string') return o.original;
  if (Array.isArray(o.cb) && o.cb.length === 4) {                               // cubic-bezier tuple
    return `cubic-bezier(${o.cb.join(', ')})`;
  }
  if (o.steps && typeof o.steps === 'object') {                                 // steps(n, pos)
    const s = o.steps as Record<string, unknown>;
    const n = typeof s.n === 'number' ? s.n : 1;
    const pos = typeof s.pos === 'string' ? s.pos : 'end';
    return `steps(${n}, ${pos})`;
  }
  if (Array.isArray(o.linear)) {                                                // linear(stops)
    // Each stop is {v} or {v, p(ercent)}.  Emit "<v>" or "<v> <p>%" per spec.
    const stops = (o.linear as Array<Record<string, unknown>>).map((st) => {
      const v = typeof st.v === 'number' ? st.v : 0;
      if (typeof st.p === 'number') return `${v} ${st.p}%`;
      return String(v);
    });
    return `linear(${stops.join(', ')})`;
  }
  return undefined;
}

// Serialise a list of timing functions (the top-level IR data shape).
export function timingFunctionListToCss(arr: unknown): string | undefined {
  if (!Array.isArray(arr) || arr.length === 0) return undefined;
  const parts = arr.map(timingFunctionToCss).filter((s): s is string => !!s);
  return parts.length === 0 ? undefined : parts.join(', ');
}

// Serialise a "pct | length | {px}" scalar to CSS.  Used by AnimationRange*
// and ViewTimelineInset sub-values.  Falls back to the shared length parser
// for rich {original:{v,u}} shapes.
export function scalarOrLengthToCss(raw: unknown): string | undefined {
  if (raw === null || raw === undefined) return undefined;
  if (typeof raw === 'number') return `${raw}%`;                                // bare number -> percent (spec)
  if (typeof raw === 'string') return raw;                                      // pre-serialised keyword / CSS
  if (typeof raw !== 'object') return undefined;
  const o = raw as Record<string, unknown>;
  if (typeof o.px === 'number') return `${o.px}px`;                             // normalised length
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;
  if (o.type === 'length' && typeof o.px === 'number') return `${o.px}px`;
  if (o.type === 'auto') return 'auto';
  const parsed = extractLength(raw);                                            // shared fallback
  return parsed.kind === 'unknown' ? undefined : toCssLength(parsed);
}
