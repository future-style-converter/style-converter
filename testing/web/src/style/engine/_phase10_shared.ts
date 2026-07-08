// _phase10_shared.ts — primitives reused across every Phase-10 category
// (scrolling non-timeline, svg, speech, rendering, print, regions, interactions,
//  performance, columns, paging, table, shapes, rhythm, navigation, images,
//  appearance, counters, lists, container, math, experimental, content, global).
//
// Web is the privileged platform for Phase 10: the overwhelming majority of
// these properties pass through straight to native CSS.  The helpers below
// cover the four shapes every IR emits:
//
//   1. bare SHOUTY_SNAKE enum string          -> kebab-case CSS keyword
//   2. {value:'RAW_VALUE' | number}           -> serialise directly
//   3. {keyword:'auto'} / Kotlin sealed Raw   -> flat keyword passthrough
//   4. numeric primitive                       -> stringified
//
// Each helper returns `undefined` when the input shape is unrecognised so the
// last-write-wins fold can safely skip it (mirrors engine/effects/_shared.ts).

import { extractLength as extractLengthRaw, toCssLength } from './core/types/LengthValue';
import { extractColor as extractColorRaw } from './core/types/ColorValue';
import { colorToCss } from './color/DynamicColorCss';

// Minimal IR shape — kept decoupled from IRModels so engine doesn't pin a version.
export interface IRPropertyLike { type: string; data: unknown; }

// SHOUTY_SNAKE → kebab-case (shared with Phase-8/9 effects _shared).  Strings
// that are already lower-case come through untouched because replace is a no-op.
export function kebab(v: unknown): string | undefined {
  if (typeof v !== 'string' || v.length === 0) return undefined;
  return v.toLowerCase().replace(/_/g, '-');
}

// Last-write-wins fold — matches the cascade used by every other engine phase.
// Always walks the full list; the parser already rejected impossible values so
// we trust its `undefined` returns to mean "skip this entry".
export function foldLast<T>(
  properties: IRPropertyLike[],
  type: string,
  parse: (data: unknown) => T | undefined,
): T | undefined {
  let out: T | undefined;
  for (const p of properties) {
    if (p.type !== type) continue;
    const v = parse(p.data);
    if (v !== undefined) out = v;
  }
  return out;
}

// The tightest pass-through: IR emits either a string or a wrapper with
// `.keyword`/`.value`/`.raw`.  Used by the dozens of "one enum, one CSS key"
// properties.
export function keywordOrRaw(data: unknown): string | undefined {
  if (data === undefined || data === null) return undefined;
  if (typeof data === 'string') return kebab(data);
  if (typeof data === 'number') return String(data);
  if (typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  // Kotlin sealed variant: { type: 'Keyword' | 'Raw' | ..., value/keyword/raw }
  if (typeof o.keyword === 'string') return kebab(o.keyword);
  if (typeof o.value === 'string')   return kebab(o.value);
  if (typeof o.raw === 'string')     return o.raw.trim() || undefined;
  if (typeof o.value === 'number')   return String(o.value);
  if (typeof o.type === 'string') {
    // Tag-only variants like {type:'None'} / {type:'Auto'}.
    const t = o.type.toLowerCase();
    if (t === 'none' || t === 'auto' || t === 'normal') return t;
  }
  return undefined;
}

// Length-or-keyword: lengths come through as {original:{v,u}} or {px}; we
// delegate to the core LengthValue helpers and fall back to keywordOrRaw.
export function lengthOrKeyword(data: unknown): string | undefined {
  if (data === undefined || data === null) return undefined;
  if (typeof data === 'object') {
    const parsed = extractLengthRaw(data);
    if (parsed.kind !== 'unknown') return toCssLength(parsed);
    const o = data as Record<string, unknown>;
    if (typeof o.px === 'number')  return `${o.px}px`;
    if (typeof o.pct === 'number') return `${o.pct}%`;
  }
  return keywordOrRaw(data);
}

// Color passthrough for SVG paint-ish values (fill-color, stroke-color, flood-color, …).
export function colorOrKeyword(data: unknown): string | undefined {
  if (data === undefined || data === null) return undefined;
  if (typeof data === 'object') {
    const c = extractColorRaw(data);
    if (c) return colorToCss(c);
  }
  return keywordOrRaw(data);
}

// Cast helper for csstype-widened keys.  All speech, regions, navigation, math,
// rhythm (block-step-*), some rendering / shapes / container / footnote Level-4
// keys use this.  Takes the kebab-case property name and value, returns a
// properly-widened CSSProperties fragment.
export function widen(prop: string, value: string | number | undefined): Record<string, string | number> {
  if (value === undefined) return {};
  return { [prop]: value };
}

// Re-exports
export { extractLengthRaw as extractLength, toCssLength, extractColorRaw as extractColor, colorToCss };
