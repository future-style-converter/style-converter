/**
 * Core IR (Intermediate Representation) types for SDUI — IR v2 wire shape.
 *
 * These types mirror the Kotlin IR models (converter IRWireV2.kt) and the
 * normative wire contract in schema/spec/01-envelope.md + 03-children.md:
 * a FLAT component list where composition is expressed by the child-side
 * `slot` reference, never by nesting. Legacy v1 documents (nested
 * `children`, `_text`/`_tag`/`_pseudo` underscore fields) are accepted for
 * one deprecation window via the decode gate in IRDecode.ts, which
 * translates them into this v2 shape so downstream code sees ONE model.
 */

/**
 * Root document envelope (schema/spec/01-envelope.md).
 *
 * `irVersion` states what the writer produced; `minReaderVersion` the
 * oldest reader that can safely consume it — both `2` on today's wire.
 * They are optional in the TYPE only because a raw fetch of a legacy v1
 * document has neither; after `decodeIRDocument` both are always 2.
 */
export interface IRDocument {
  /** Writer version. 2 on the v2 wire; absent on a raw legacy v1 doc. */
  irVersion?: number;
  /** Oldest reader that may consume this doc (reader refuses if > 2). */
  minReaderVersion?: number;
  /** FLAT component list — every component at every composition depth. */
  components: IRComponent[];
  /**
   * Document-level named keyframe sets (schema/spec/07-animations.md §1.2)
   * — the wire twin of CSS `@keyframes` at-rules, which are document-scoped
   * in CSS too. ADDITIVE v2 minor-revision key, omit-when-empty: absent on
   * every pre-motion document (and structurally impossible on legacy v1
   * wire), so the committed-baseline path never sees it. Components opt in
   * with an ordinary `AnimationName` property naming a key of this map;
   * a dangling reference is a defined no-op (§1.3), never an error.
   */
  keyframes?: IRKeyframes;
}

/**
 * One keyframe stop (spec 07 §1.2). Stops arrive from the converter
 * SORTED ascending by offset (stable for equal offsets — the authoring
 * order decides the css-animations-1 §4.2 last-wins cascade); readers may
 * rely on sortedness and must not reorder.
 */
export interface IRKeyframeStop {
  /** Resolved offset fraction in [0, 1] (`from`/`to`/percent pre-resolved). */
  offset: number;
  /** Typed stop declarations — the SAME {type, data} envelopes components use. */
  properties: IRProperty[];
}

/** Named keyframe sets: animation-name ident → sorted stop list. */
export type IRKeyframes = Record<string, IRKeyframeStop[]>;

/**
 * Child-side composition reference (schema/spec/03-children.md).
 *
 * Present on a component that previews inside a container; omitted for
 * roots. Structural data that MUST round-trip — engines never read it
 * (composition-agnostic contract); only composers (the harness) do.
 */
export interface IRSlot {
  /** `id` of the container this component composes into. */
  parent: string;
  /**
   * Slot name within the parent; reserved for future multi-slot
   * containers. The wire omits it at the default — `decodeIRDocument`
   * reconstructs `"content"` so consumers never see `undefined`.
   */
  name?: string;
}

/**
 * One entry of the wave-22 `meta.decorations` list — a single decorating
 * box's contribution to a COLLAPSED inline run (schema/spec/
 * 04-metadata-fields.md; producer: the `_decorations` banner in
 * tools/titan/extract-fixture.mjs).
 */
export interface IRDecoration {
  /**
   * Exactly ONE css-text-decor-3 §2.1 line keyword. An element declaring
   * two contributes two entries sharing one colour, so consumers never
   * re-tokenise. `none`/`blink` never appear (they paint nothing).
   */
  line: string;
  /**
   * The decorating box's `text-decoration-color` AS AUTHORED ('blue',
   * '#00f', 'rgb(0,0,255)') — NOT the normalized sRGB leaf every CSS
   * *property* value carries. On web that is a feature: the token goes
   * straight back into a `text-decoration-color` declaration and the
   * browser parses it, so the round trip is lossless by construction.
   * Absent/null = `currentColor` (§2.2 initial).
   */
  color?: string | null;
}

/**
 * One entry of the wave-32 `meta.runs` list — either an anonymous inline
 * text run or the position of one child box in the inline flow
 * (schema/spec/03-children.md §4.1; producer: the `_runs` banner in
 * tools/titan/extract-fixture.mjs).
 *
 * EXACTLY ONE key is set. Modelled as a union rather than
 * `{text?, child?}` so a malformed both-keys entry is a type error at
 * every consumer instead of a silent precedence question.
 */
export type IRRun =
  /** A bare text node at this position in the content walk. */
  | { text: string; child?: undefined }
  /**
   * The child at this position, named by its AUTHORING KEY — which is the
   * child's `name` on the v2 wire and (extractor-direct pipeline) also its
   * `id`. NOT the converter-minted id: the converter re-ids at the flatten
   * boundary, so an id written by the producer would name nothing after
   * the hop (schema/spec/04-metadata-fields.md).
   */
  | { child: string; text?: undefined };

/**
 * Droppable renderer hints, grouped (v2 home of v1's `_tag` / `_role`).
 * Omitted entirely when empty; strict (`{sourceTag?, role?, attrs?,
 * decorations?, markerText?, runs?}` only) when present — see
 * schema/spec/01-envelope.md.
 */
export interface IRMeta {
  /**
   * Originating HTML element tag (lowercase: 'ol', 'li', 'p', …), the v2
   * rename of v1 `_tag`. Lets the web renderer choose a matching native
   * element so browser-default semantics (list markers, paragraph
   * spacing, table layout) apply. Absent → render as <div>.
   */
  sourceTag?: string | null;
  /** Freeform authoring role hint (v2 rename of v1 `_role`). Droppable. */
  role?: string | null;
  /**
   * wave-20 widget-identity attributes (v2 home of the extractor's
   * `_attrs`). Emitted only for form/widget sourceTags (a/button/input/
   * textarea/select/option/meter/progress), only for present-in-source
   * attributes among {type, value, checked, multiple, size, alt, min,
   * max, selected, disabled}; booleans are presence-`true`, min/max —
   * and value on meter/progress — are numbers where numeric, everything
   * else verbatim source strings (schema/ir-v2.schema.json meta.attrs).
   * Application policy lives in renderer/WidgetAttrs.ts.
   */
  attrs?: Record<string, string | number | boolean> | null;
  /**
   * wave-22 per-line decorations for a COLLAPSED inline run (v2 home of
   * the extractor's `_decorations`). ORDER is OUTERMOST-FIRST — the
   * css-text-decor-3 §2.1 propagation order — and the list is
   * AUTHORITATIVE when present: it is the complete line set for the run,
   * so a renderer that honours it MUST ignore the component's own
   * `text-decoration-line` (the surviving flat longhands are a root-wins
   * merged bag kept for readers that drop `meta`). A present-but-EMPTY
   * list still says "no lines" and must paint nothing.
   * Application policy lives in renderer/DecorationSpans.ts.
   */
  decorations?: IRDecoration[] | null;
  /**
   * wave-27 resolved list-marker string for one `<li>` (v2 home of the
   * extractor's `_markerText`) — representation + suffix per
   * css-counter-styles-3 §6, already resolved against `<ol start>` and
   * the item's ordinal. Modelled so the type mirrors the wire, but the
   * web renderer DELIBERATELY DOES NOT CONSUME IT: it emits a real
   * `<ol>`/`<li>` and lets the browser synthesise `::marker` from the
   * component's `list-style-type` plus the forwarded `start` attribute,
   * which is a better oracle than any string we could bake. The two
   * native runtimes — which have no `::marker` — are the readers.
   */
  markerText?: string | null;
  /**
   * wave-32 ORDERED inline-content list (v2 home of the extractor's
   * `_runs`) — the component's own text and its kept children INTERLEAVED
   * in document order, the one shape the single `text` string cannot
   * express (`the quick <u>brown</u> fox` with a surviving `<u>`).
   *
   * AUTHORITATIVE when present: NodeRenderer paints the entries in order,
   * does NOT also paint `text`, and does NOT paint a referenced child a
   * second time from the sibling walk. Children this list does not
   * reference still render, after the runs, in sibling order (spec 03
   * §4.1 rule 4) — so a partial list can never make a box disappear.
   *
   * Each `{text}` entry is an INLINE anonymous run: a bare text node in
   * the children walk, never a wrapper element. A block box inside an
   * inline box splits it (CSS 2.1 §9.2.1.1), which would re-break the
   * very line box this key exists to preserve — the measured wave-31
   * "span lesson".
   */
  runs?: IRRun[] | null;
}

/**
 * A single UI component with its styles — v2 flat shape.
 *
 * NOTE: there is NO `children` field. Its presence in a v2 document is a
 * hard decode error (spec 03 §1); composition is rebuilt by the harness
 * composer from `slot` refs (Mode A) or supplied externally (Mode B).
 */
export interface IRComponent {
  /** Unique identifier across the WHOLE document (e.g. "button-001"). */
  id: string;
  /** Component type/class name (e.g. "Button", "Card"). */
  name: string;
  /** List of CSS properties as IR; ITEM-scoped placement claims included. */
  properties: IRProperty[];
  /**
   * CSS custom-property definitions declared on this component
   * (`--name` → RAW declaration value, verbatim). Additive IR v2 minor
   * revision (spec 01 component table). Names are case-SENSITIVE
   * (css-variables-1 §2) and values are untyped token streams until a
   * var() reference substitutes them; the empty string is a legal value.
   * var() resolution walks element → slot-parent chain → fallback
   * (spec 02 custom-properties section) and happens at style-build
   * time, never at decode time. Omitted when the component defines none.
   */
  variables?: Record<string, string>;
  /** State-based styles (hover, focus, …). v2 omits the key when empty. */
  selectors?: IRSelector[];
  /** Responsive breakpoint styles. v2 omits the key when empty. */
  media?: IRMedia[];
  /** Child-side composition ref; absent for roots (spec 03). */
  slot?: IRSlot | null;
  /**
   * Element text content (v2 rename of v1 `_text`). Empty string is a
   * legal emitted value ("extracted, was empty"); absent means "no text
   * — placeholder behaviour applies" so the 327-pair baseline is stable.
   */
  text?: string | null;
  /**
   * Pseudo-element payload (v2 rename of v1 `_pseudo`). Forwarded
   * verbatim from the authoring extractor (spec 01: extractor-owned,
   * never flattened into the component list) — hence the tolerant
   * {@link IRPseudoNode} inner shape.
   */
  pseudos?: PseudoElements | null;
  /** Grouped droppable hints; v2 home of v1 `_tag`/`_role`. */
  meta?: IRMeta | null;
}

/**
 * Per-element pseudo-element payload — see {@link IRComponent.pseudos}.
 *
 * Each slot is independently optional: a `<li>` might only carry a
 * `marker`, a `<span>` only a `before`. Renderers MUST treat missing/null
 * slots as "no synthetic node for that pseudo".
 */
export interface PseudoElements {
  /** Synthetic ::before node — rendered as a leading inline span. */
  before?: IRPseudoNode | null;
  /** Synthetic ::after node — rendered as a trailing inline span. */
  after?: IRPseudoNode | null;
  /** Synthetic ::marker node — rendered as a list-item-marker span. */
  marker?: IRPseudoNode | null;
}

/**
 * One synthetic pseudo-element node. The wire forwards the extractor's
 * authoring shape VERBATIM (spec 01: `pseudos` is extractor-owned), so
 * every field is optional and BOTH text spellings are tolerated: the
 * legacy `_text` (what the WPT extractor emits today) and a future
 * `text`. Style declarations from the original CSS rule (`content`,
 * color, font-*) arrive in `properties` and reach buildStyles intact.
 */
export interface IRPseudoNode {
  /** Optional id, used only for React keys / debug attributes. */
  id?: string;
  /** Optional debug name. */
  name?: string;
  /**
   * Style declarations from the originating pseudo rule. The payload is
   * extractor-owned and forwarded verbatim (spec 01), so BOTH shapes are
   * legal on the wire: a typed IR list, or the raw declarations map the
   * WPT extractor emits today ({ display: 'block', … }). NodeRenderer
   * bridges each shape to inline styles accordingly.
   */
  properties?: IRProperty[] | Record<string, string> | null;
  /** Literal `content:` string in the extractor's legacy spelling. */
  _text?: string | null;
  /** Literal `content:` string in the v2 spelling (tolerated). */
  text?: string | null;
}

/**
 * A CSS property in IR format — identical envelope in v1 and v2.
 *
 * Uses generic `unknown` data to handle all 550 property types flexibly;
 * specific handling is done in the per-property Extractors.
 */
export interface IRProperty {
  type: string;
  data: unknown;
}

/**
 * Pseudo-class selector styles (e.g., :hover, :focus). `condition` is
 * stored WITHOUT the leading colon (spec 01).
 */
export interface IRSelector {
  condition: string;
  properties: IRProperty[];
}

/**
 * Media query styles (e.g., min-width: 768px). `query` is the raw media
 * query string, parentheses included (spec 01).
 */
export interface IRMedia {
  query: string;
  properties: IRProperty[];
}
