// _shared.ts — the shapes-category wire decoder (CSS Shapes 1).
//
// WHY THIS FILE EXISTS (wave-36 lane M5 — css-shapes 68-cell pool).
// `shape-outside` used to fold through `_phase10_shared.keywordOrRaw`, which
// only understands four wire shapes: a bare string, `{keyword}`, `{value}` and
// the tag-only `{type:'none'|'auto'|'normal'}`. But
// `converter/src/main/kotlin/app/irmodels/properties/shapes/ShapeOutsideProperty.kt`
// serialises a SEALED INTERFACE whose variants are discriminated by a
// kebab-case `@SerialName` tag and carry their payload under a variant-specific
// key (`shape`, `url`, `keyword`, `value`), e.g.
//   {"type":"basic-shape","shape":"circle(50% at left bottom)"}
//   {"type":"border-box"}
// `keywordOrRaw` matched NONE of those, returned undefined, and the applier
// emitted nothing — so every `circle()/ellipse()/inset()/polygon()` and every
// `<shape-box>` keyword silently vanished before it reached the DOM. Web is a
// passthrough platform for float exclusion (the browser owns the exclusion
// geometry), so the missing declaration WAS the whole geometry divergence.
//
// The decoder below is the single owner of that wire, shared by the
// shape-outside and shape-inside extractors (identical sealed-variant shape).

// Minimal IR node — same structural type the rest of the engine folds over.
export type ShapeWire = Record<string, unknown>;

// The four CSS Shapes 1 §2 `<shape-box>` keywords, tagged verbatim by the
// Kotlin @SerialName so the tag IS already the CSS keyword.
const SHAPE_BOXES = new Set(['margin-box', 'border-box', 'padding-box', 'content-box']);

/**
 * `IRUrl` → a CSS `url()` token.
 * ValueTypes.kt's IRUrlSerializer emits a BARE STRING for an ordinary URL and
 * `{url, data:true}` for a data: URL, so both shapes have to be accepted here.
 * The href is quoted (and quotes/backslashes escaped) because WPT shape-image
 * paths contain characters — parentheses, spaces — that an unquoted url()
 * token cannot carry.
 */
export function irUrlToCss(raw: unknown): string | undefined {
  // Ordinary URL: the serializer collapsed IRUrl to its href string.
  const href = typeof raw === 'string'
    ? raw
    // data: URL: the object form keeps the href under `url`.
    : (raw !== null && typeof raw === 'object' && typeof (raw as ShapeWire).url === 'string')
      ? (raw as ShapeWire).url as string
      : undefined;
  if (href === undefined || href.length === 0) return undefined;   // nothing to reference
  return `url("${href.replace(/(["\\])/g, '\\$1')}")`;             // quote + escape
}

/**
 * Decode one `ShapeOutside` / `ShapeInside` IR node to its CSS text.
 *
 * Variants mirror ShapeOutsideProperty.kt / ShapeInsideProperty.kt 1:1:
 *   {type:'none'}                          -> 'none'
 *   {type:'auto'}                          -> 'auto'            (shape-inside)
 *   {type:'<shape-box>'}                   -> the box keyword
 *   {type:'basic-shape', shape:'circle(…)'} -> the shape VERBATIM
 *   {type:'shape',       shape:'circle(…)'} -> ditto (shape-inside's tag)
 *   {type:'image-url',   url:…}            -> url("…")
 *   {type:'keyword',     keyword:'inherit'}-> the CSS-wide keyword
 *   {type:'raw',         value:'…'}        -> the value VERBATIM
 *
 * `basic-shape` and `raw` are returned UNTOUCHED (no kebab(), no lowercase):
 * the Kotlin parser stores the author's declaration text, which is the only
 * form the browser's own shape parser needs — and lower-casing would corrupt
 * gradient colour functions and case-sensitive url() paths.
 * Unknown tags return undefined so the last-write-wins fold skips them instead
 * of emitting a declaration the browser would drop anyway.
 */
export function shapeValueToCss(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;        // absent
  // Defensive: a bare string wire (hand-authored fixtures) passes straight through.
  if (typeof data === 'string') return data.trim() || undefined;
  if (typeof data !== 'object') return undefined;                   // numbers etc. are not shapes
  let o = data as ShapeWire;
  // The v2 emitter flattens the single-field `ShapeOutsideProperty(value=…)`
  // wrapper onto `data`; an UNflattened `{value:{type:…}}` is still legal wire
  // (schema/spec/02-values.md leaves property data permissive), so unwrap it
  // when the tag is missing one level up.
  if (typeof o.type !== 'string' && o.value !== null && typeof o.value === 'object') {
    o = o.value as ShapeWire;
  }
  const tag = typeof o.type === 'string' ? o.type.toLowerCase() : undefined;
  if (tag === undefined) return undefined;                          // untagged → not our wire
  if (SHAPE_BOXES.has(tag)) return tag;                             // §2 <shape-box>: tag == CSS keyword
  switch (tag) {
    // Tag-only variants — the tag is the CSS keyword.
    case 'none': return 'none';
    case 'auto': return 'auto';
    // §3 <basic-shape>: the parser kept the declaration text (including any
    // trailing `<shape-box>`, e.g. "circle(50% at left 40px) border-box").
    case 'basic-shape':
    case 'shape':
      return typeof o.shape === 'string' ? (o.shape.trim() || undefined) : undefined;
    // §4 <image>: url() reference to a shape image.
    case 'image-url': return irUrlToCss(o.url);
    // CSS-wide keyword (inherit/initial/unset/revert) — always lower-case.
    case 'keyword':
      return typeof o.keyword === 'string' ? (o.keyword.trim().toLowerCase() || undefined) : undefined;
    // Anything the Kotlin parser could not classify (gradients, var(), calc()).
    case 'raw':
      return typeof o.value === 'string' ? (o.value.trim() || undefined) : undefined;
    default: return undefined;                                      // unknown variant → skip
  }
}
