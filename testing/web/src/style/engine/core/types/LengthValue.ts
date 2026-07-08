// LengthValue.ts - Discriminated-union length type + extractor.
// Handles every shape documented in examples/primitives/lengths-*.json:
//   lengths-absolute.json   -> { type:'length', px:N, original?:{v,u:'PX'|'PT'|...} }
//   lengths-font-relative.json -> { type:'length', original:{v,u:'EM'|...} } (no px)
//   lengths-viewport.json   -> { type:'length', original:{v,u:'VW'|'SVW'|...} }
//   lengths-container.json  -> { type:'length', original:{v,u:'CQW'|...} }
//   lengths-intrinsic.json  -> bare string "auto" | "min-content" | "max-content"
//   lengths-special.json    -> { type:'percentage', value:N } or { fr:N }
//   Many property shapes also pass raw { px:N } (no 'type' wrapper).

// Exhaustive list of CSS length units we recognise (lowercased per IR convention).
export type LengthUnit =
  | 'px' | 'pt' | 'cm' | 'mm' | 'in' | 'pc' | 'Q'                  // absolute
  | 'em' | 'rem' | 'ex' | 'ch' | 'cap' | 'ic' | 'lh' | 'rlh'       // font-relative
  | 'percent'                                                       // %
  | 'vw' | 'vh' | 'vmin' | 'vmax' | 'vi' | 'vb'                    // viewport classic
  | 'svw' | 'svh' | 'svmin' | 'svmax' | 'svi' | 'svb'              // small viewport
  | 'lvw' | 'lvh' | 'lvmin' | 'lvmax' | 'lvi' | 'lvb'              // large viewport
  | 'dvw' | 'dvh' | 'dvmin' | 'dvmax' | 'dvi' | 'dvb'              // dynamic viewport
  | 'cqw' | 'cqh' | 'cqi' | 'cqb' | 'cqmin' | 'cqmax'              // container-query
  | 'fr';                                                           // grid fraction

// Discriminated-union result of parsing a length shape.
// 'none' is legal for max-width/max-height etc. ({"type":"none"} IR shape).
// 'intrinsic' optionally carries a `bound` for fit-content(<length>) — the Kotlin
// CSS parser emits {"fit-content":{...length...}} for the bounded form.
export type LengthValue =
  | { kind: 'exact'; px: number }
  | { kind: 'relative'; value: number; unit: LengthUnit; pxFallback?: number }
  | { kind: 'auto' }
  | { kind: 'none' }
  | { kind: 'intrinsic'; intrinsicKind: 'min-content' | 'max-content' | 'fit-content'; bound?: LengthValue }
  | { kind: 'fraction'; fr: number }
  | { kind: 'calc'; expression: string }
  | { kind: 'unknown' };

// Set for O(1) validation of unit strings produced by toLowerCase() on IR 'u'.
const VALID_UNITS = new Set<string>([
  'px','pt','cm','mm','in','pc','q',
  'em','rem','ex','ch','cap','ic','lh','rlh',
  'percent',
  'vw','vh','vmin','vmax','vi','vb',
  'svw','svh','svmin','svmax','svi','svb',
  'lvw','lvh','lvmin','lvmax','lvi','lvb',
  'dvw','dvh','dvmin','dvmax','dvi','dvb',
  'cqw','cqh','cqi','cqb','cqmin','cqmax',
  'fr',
]);

// Normalise an IR unit token ('PX', 'Q', 'PERCENT', ...) into our LengthUnit alphabet.
function normaliseUnit(u: unknown): LengthUnit | null {
  if (typeof u !== 'string') return null;                           // guard against missing/odd inputs
  const lc = u.toLowerCase();                                       // IR emits upper-case tokens
  // Map 'q' (lowercased) back to the canonical 'Q' so LengthUnit literal stays consistent.
  if (lc === 'q') return 'Q';
  // Reject anything outside our recognised alphabet to keep downstream code total.
  return VALID_UNITS.has(lc) ? (lc as LengthUnit) : null;
}

// Intrinsic keyword strings we accept as bare 'data'.
const INTRINSICS = new Set(['min-content','max-content','fit-content']);

// Main entrypoint — never throws; returns {kind:'unknown'} on anything unparseable.
export function extractLength(data: unknown): LengthValue {
  if (data === null || data === undefined) return { kind: 'unknown' };// null/undefined -> unknown, not crash

  // Bare string: 'auto' | intrinsic keyword. Seen in lengths-intrinsic.json.
  if (typeof data === 'string') {
    if (data === 'auto') return { kind: 'auto' };                   // quirk #3: bare string as data
    if (INTRINSICS.has(data)) {
      return { kind: 'intrinsic', intrinsicKind: data as 'min-content' | 'max-content' | 'fit-content' };
    }
    return { kind: 'unknown' };                                     // 'fit-content' accepted even if not yet parsed
  }

  // Bare number shorthand — Phase-2 finding: emitted for padding/margin percentages
  // (e.g. Margin_Percent_10 fixture).  Treat as a percentage so spacing round-trips.
  if (typeof data === 'number') return { kind: 'relative', value: data, unit: 'percent' };

  // Everything below expects an object shape.
  if (typeof data !== 'object') return { kind: 'unknown' };
  const obj = data as Record<string, unknown>;

  // 'none' envelope: {"type":"none"} — emitted by the Kotlin CSS parser for
  // max-width/max-height "none" (examples: MaxWidth_None, MaxHeight_None).
  if (obj.type === 'none') return { kind: 'none' };

  // Bounded fit-content: {"fit-content": <LengthValue-shape>} — IR emits this
  // for the CSS `fit-content(<length>)` functional form (width-intrinsic fixture).
  if ('fit-content' in obj) {
    const inner = obj['fit-content'];
    // Recursively parse the bound so every unit the inner length supports works.
    const bound = extractLength(inner);
    return { kind: 'intrinsic', intrinsicKind: 'fit-content', bound };
  }

  // Grid fraction: { fr: N } — quirk #4, NOT a length even though related.
  if (typeof obj.fr === 'number') return { kind: 'fraction', fr: obj.fr };

  // calc() support — two IR shapes seen in the wild:
  //   { type:'calc', expression:'...' }   (primitive fixtures)
  //   { expr:'calc(10px + 5px)' }         (spacing fixtures, per Phase-2 survey)
  if (obj.type === 'calc' && typeof obj.expression === 'string') {
    return { kind: 'calc', expression: obj.expression };
  }
  if (typeof obj.expr === 'string') {
    // Strip redundant 'calc(...)' wrapper if IR already includes it, so callers can wrap uniformly.
    const raw = obj.expr.trim();
    const inner = raw.startsWith('calc(') && raw.endsWith(')') ? raw.slice(5, -1) : raw;
    return { kind: 'calc', expression: inner };
  }

  // Percentage envelope: { type:'percentage', value:N } — quirk #2.
  if (obj.type === 'percentage' && typeof obj.value === 'number') {
    return { kind: 'relative', value: obj.value, unit: 'percent' };
  }

  // 'type:length' wrapper (quirk #1): unwrap and continue with the inner shape.
  // After unwrapping we still have px / original keys on the same object, so we fall through.

  // Canonical pixel value present -> exact. Original is ignored because px is already normalised.
  if (typeof obj.px === 'number') return { kind: 'exact', px: obj.px };

  // No px but 'original' carries v+u -> context-dependent relative length.
  if (obj.original && typeof obj.original === 'object') {
    const orig = obj.original as Record<string, unknown>;
    if (typeof orig.v === 'number') {
      const unit = normaliseUnit(orig.u);                           // 'EM' -> 'em', reject garbage
      if (unit) return { kind: 'relative', value: orig.v, unit };
    }
  }

  // Some properties emit raw {v,u} without an 'original' wrapper — handle defensively.
  if (typeof obj.v === 'number') {
    const unit = normaliseUnit(obj.u);
    if (unit) return { kind: 'relative', value: obj.v, unit };
  }

  return { kind: 'unknown' };                                       // fall-through: no recognised shape
}

// Convert a parsed LengthValue into a CSS string suitable for inline styles.
// Used by StyleBuilder/Phase-2 wiring; kept here so tests can exercise it.
export function toCssLength(v: LengthValue): string {
  switch (v.kind) {                                                 // discriminant switch
    case 'exact':     return `${v.px}px`;                           // canonical pixel output
    case 'relative':  return `${v.value}${v.unit === 'percent' ? '%' : v.unit}`;
    case 'auto':      return 'auto';                                // passthrough keyword
    case 'none':      return 'none';                                // valid on max-*
    case 'intrinsic': return v.bound                                // fit-content can carry a bound
      ? `fit-content(${toCssLength(v.bound)})`                      // -> fit-content(200px) etc.
      : v.intrinsicKind;                                            // unbounded: 'min-content' / ...
    case 'fraction':  return `${v.fr}fr`;                           // grid tracks
    case 'calc':      return `calc(${v.expression})`;               // raw passthrough
    case 'unknown':   return 'auto';                                // safe fallback
    default: {                                                      // exhaustiveness guard
      const _exhaustive: never = v;
      return _exhaustive;
    }
  }
}
