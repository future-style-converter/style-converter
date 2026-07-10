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
}

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
 * Droppable renderer hints, grouped (v2 home of v1's `_tag` / `_role`).
 * Omitted entirely when empty; strict (`{sourceTag?, role?}` only) when
 * present — see schema/spec/01-envelope.md.
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
  /** Style declarations from the originating pseudo rule. */
  properties?: IRProperty[];
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
