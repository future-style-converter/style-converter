// _shared.ts — primitives shared by every Phase-8 effects triplet (clip,
// filter, mask).  Same contract as engine/layout/_shared.ts: every helper is
// pure, returns `undefined` on unknown shapes so last-write-wins cascade is
// safe, and the file stays ≤200 lines.

import { extractLength as extractLengthRaw, type LengthValue, toCssLength } from '../core/types/LengthValue';
import { extractColor as extractColorRaw } from '../core/types/ColorValue';
import { colorToCss } from '../color/DynamicColorCss';

// Minimal IR property shape — decoupled from IRModels so engine doesn't pin a version.
export interface IRPropertyLike { type: string; data: unknown; }

// Lowercase + kebab-case for IR SHOUTY_SNAKE enums (e.g. 'PADDING_BOX' -> 'padding-box').
// Used by every enum-only applier in the effects tree (MaskOrigin, MaskClip, MaskType,
// Visibility, Overflow*, TransformBox, TransformStyle, BackfaceVisibility, ...).
export function kebab(data: unknown): string | undefined {
  if (typeof data !== 'string') return undefined;                                 // unknown shape
  if (data.length === 0) return undefined;                                        // defensive
  return data.toLowerCase().replace(/_/g, '-');                                   // SHOUTY_SNAKE -> kebab
}

// Last-write-wins fold over IR properties of a given type.  Cascade semantics
// mirror the CSS specificity rule: later declarations override earlier ones.
export function foldLast<T>(
  properties: IRPropertyLike[],
  type: string,
  parse: (data: unknown) => T | undefined,
): T | undefined {
  let out: T | undefined;                                                          // default absent
  for (const p of properties) {                                                    // full pass (no early exit)
    if (p.type !== type) continue;                                                 // filter type
    const v = parse(p.data);                                                       // shape-tolerant parse
    if (v !== undefined) out = v;                                                  // record last valid hit
  }
  return out;
}

// Factory for the many enum-only properties whose extractor is literally
// `foldLast(props, TYPE, kebab)`.  Keeps per-property triplets ≤30 lines.
export function enumExtractor(type: string) {
  return (properties: IRPropertyLike[]) => ({ value: foldLast(properties, type, kebab) });
}

// Position / length token for `<position>` values (MaskPosition, TransformOrigin,
// PerspectiveOrigin).  IR emits either a keyword wrapper ({type:'top'|'center'|...}),
// a length wrapper ({type:'length', px}, {type:'percentage', value}), or a raw
// {px}/{pct}/number.  Returns the single CSS token for that axis.
export function positionAxis(data: unknown): string | undefined {
  if (data === undefined || data === null) return undefined;                      // absent
  if (typeof data === 'string') return kebab(data) ?? data;                       // bare keyword
  if (typeof data === 'number') return `${data}%`;                                // bare number → percent
  if (typeof data !== 'object') return undefined;                                 // unknown primitive
  const o = data as Record<string, unknown>;
  // Keyword-wrapped: {type:'top'|'bottom'|'left'|'right'|'center'}
  if (typeof o.type === 'string') {
    switch (o.type) {
      case 'top': case 'bottom': case 'left': case 'right': case 'center':        // positional keywords
        return o.type;
      case 'keyword':                                                              // {type:'keyword', value:'TOP'}
        return kebab(o.value);
      case 'percentage':                                                           // {type:'percentage', value}
        if (typeof o.value === 'number') return `${o.value}%`;
        if (typeof o.percentage === 'number') return `${o.percentage}%`;
        break;
      case 'length':                                                               // {type:'length', px}/{original}
        if (typeof o.px === 'number') return `${o.px}px`;
        break;
    }
  }
  // Non-typed numeric wrappers
  if (typeof o.pct === 'number') return `${o.pct}%`;
  if (typeof o.percentage === 'number') return `${o.percentage}%`;
  if (typeof o.px === 'number') return `${o.px}px`;
  // Fall back to the shared length extractor for {original:{v,u}} / calc() shapes.
  const parsed: LengthValue = extractLengthRaw(data);                             // shared parser
  if (parsed.kind !== 'unknown') return toCssLength(parsed);                      // generic length path
  return undefined;
}

// Re-exports so property files don't each have to depend on two locations.
export { extractLengthRaw as extractLength, toCssLength, extractColorRaw as extractColor, colorToCss };
