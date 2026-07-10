/**
 * IRDecode — the web runtime's irVersion gate (schema/spec/05-versioning.md).
 *
 * `decodeIRDocument` is the single entry point between raw fetched JSON and
 * the typed v2 {@link IRDocument} the rest of the web stack consumes:
 *
 *   - v2 documents (`irVersion: 2`) pass through with light normalization
 *     (slot.name default reconstruction) and hard validation (`children`
 *     anywhere is a decode error; `minReaderVersion > 2` is refused).
 *   - v1 documents (no `irVersion`) are accepted for ONE deprecation
 *     window: a console.warn fires and the nested tree is translated to
 *     the flat v2 shape — `_text`→`text`, `_pseudo`→`pseudos`,
 *     `_tag`/`_role`→`meta`, `children` nesting → flat list + `slot`
 *     stamping in pre-order (identical order/ids to the converter's
 *     IRFlattener, so captures are order-stable across the flip).
 *
 * Output is ALWAYS a canonical v2 doc (irVersion/minReaderVersion = 2),
 * which makes the gate idempotent — safe on the hot-reload re-fetch path.
 */

import type {
  IRDocument,
  IRComponent,
  IRSlot,
  IRMeta,
  PseudoElements,
  IRProperty,
  IRSelector,
  IRMedia,
} from './IRModels';

/** Highest wire version this runtime implements (spec 05 discovery rule). */
export const WIRE_READER_VERSION = 2;

/** Default slot name the wire omits (spec 03: `name` defaults "content"). */
const DEFAULT_SLOT_NAME = 'content';

/** Loose record view of an untyped JSON object. */
type Raw = Record<string, unknown>;

/**
 * Decode raw parsed JSON into a canonical v2 IRDocument.
 * Throws on: non-object input, missing components array, unsupported
 * minReaderVersion, or a `children` key inside a v2 document.
 */
export function decodeIRDocument(raw: unknown): IRDocument {
  // The wire is always a JSON object envelope — anything else is corrupt.
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('IR document must be a JSON object with a components array');
  }
  const doc = raw as Raw;
  // Both versions carry a top-level components array; refuse without it.
  if (!Array.isArray(doc.components)) {
    throw new Error('IR document has no components array');
  }
  // Version discovery (spec 05): missing irVersion IS a v1 document —
  // legal only during the deprecation window, handled by translation.
  if (doc.irVersion === undefined) {
    return translateV1(doc.components as unknown[]);
  }
  // Reader refusal rule (spec 05): a doc that declares it needs a newer
  // reader than we implement MUST be refused, not half-rendered.
  const minReader =
    typeof doc.minReaderVersion === 'number' ? doc.minReaderVersion : Number(doc.irVersion);
  if (!(minReader <= WIRE_READER_VERSION)) {
    throw new Error(
      `IR document requires reader version ${String(minReader)}; ` +
        `this runtime implements ${WIRE_READER_VERSION}`,
    );
  }
  // v2 path: normalize each component (slot default, children hard error).
  const components = (doc.components as unknown[]).map(normalizeV2Component);
  // Canonical envelope out — version pair pinned to 2 (spec 01).
  return { irVersion: 2, minReaderVersion: 2, components };
}

/**
 * Normalize one v2 wire component. Enforces the two decode-side rules the
 * schema pins: `children` is a hard error (spec 03 §1) and `slot.name`
 * reconstructs its default so consumers never branch on absence.
 */
function normalizeV2Component(entry: unknown): IRComponent {
  // Component entries are objects by schema; refuse primitives outright.
  if (entry === null || typeof entry === 'object' === false || Array.isArray(entry)) {
    throw new Error('IR v2 component entry must be a JSON object');
  }
  const c = entry as Raw;
  // Hard error: `children` does not exist in v2 — its presence means the
  // producer mixed wire versions (matches converter-side encode error).
  if ('children' in c) {
    throw new Error(
      `IR v2 component "${String(c.id)}" carries a "children" key — ` +
        'nested children are a v1 shape and a hard error on the v2 wire (spec 03)',
    );
  }
  // Rebuild the component with the slot default filled in. All other
  // fields pass through as-is: the property/selector/media envelopes are
  // identical in v1 and v2 and stay untyped-permissive at the leaves.
  const out: IRComponent = {
    id: String(c.id ?? ''),
    name: String(c.name ?? ''),
    // Defensive: properties is mandatory on the wire but tolerate absence
    // (unknown-tolerance posture) rather than crash the whole document.
    properties: Array.isArray(c.properties) ? (c.properties as IRProperty[]) : [],
  };
  // omit-when-empty keys: only attach when the wire carried them, so a
  // re-encode of the decoded doc stays shape-faithful.
  if (Array.isArray(c.selectors)) out.selectors = c.selectors as IRSelector[];
  if (Array.isArray(c.media)) out.media = c.media as IRMedia[];
  if (typeof c.text === 'string') out.text = c.text;
  if (c.pseudos && typeof c.pseudos === 'object') out.pseudos = c.pseudos as PseudoElements;
  if (c.meta && typeof c.meta === 'object') out.meta = c.meta as IRMeta;
  // slot: `{parent, name?}` — reconstruct the omitted default name.
  if (c.slot && typeof c.slot === 'object') {
    const s = c.slot as Raw;
    // A slot without a string parent is malformed; treat as root rather
    // than crash (composer-side dangling handling covers the rest).
    if (typeof s.parent === 'string' && s.parent.length > 0) {
      const slot: IRSlot = { parent: s.parent, name: DEFAULT_SLOT_NAME };
      // Explicit non-default name round-trips verbatim (multi-slot reserve).
      if (typeof s.name === 'string' && s.name.length > 0) slot.name = s.name;
      out.slot = slot;
    }
  }
  return out;
}

/**
 * Translate a legacy v1 nested document to the canonical flat v2 shape.
 * Pre-order walk (parent before children, siblings in array order) —
 * the exact order the converter's IRFlattener produces, so capture
 * indices and composed DOM order are stable across the v1→v2 flip.
 */
function translateV1(components: unknown[]): IRDocument {
  // One warning per document (not per component): the window is temporary
  // and the fix is upstream (re-emit with the converter's default v2).
  console.warn(
    '[IR] Legacy v1 document (no irVersion) — accepted during the deprecation ' +
      'window only. Translating _text/_tag/_pseudo/_role and nested children ' +
      'to the v2 flat shape; re-emit with `--emit-ir v2` (the default).',
  );
  const flat: IRComponent[] = [];
  // Recursive visitor: emit the translated component, then its children.
  const visit = (entry: unknown, parentId: string | null, index: number): void => {
    // Skip malformed entries defensively; v1 tolerance was always loose.
    if (entry === null || typeof entry !== 'object' || Array.isArray(entry)) return;
    const c = entry as Raw;
    // v1 components always carry ids from the converter; synthesize a
    // stable fallback for hand-authored docs so slot refs stay valid.
    const id =
      typeof c.id === 'string' && c.id.length > 0
        ? c.id
        : `${parentId ?? 'root'}-child-${index}`;
    const out: IRComponent = {
      id,
      name: typeof c.name === 'string' ? c.name : id,
      properties: Array.isArray(c.properties) ? (c.properties as IRProperty[]) : [],
    };
    // v1 always emitted selectors/media (possibly empty); carry them over
    // only when non-empty to match the v2 omit-when-empty emission rule.
    if (Array.isArray(c.selectors) && c.selectors.length > 0) {
      out.selectors = c.selectors as IRSelector[];
    }
    if (Array.isArray(c.media) && c.media.length > 0) out.media = c.media as IRMedia[];
    // Field renames — `_text` → `text` (empty string is meaningful and
    // preserved), `_pseudo` → `pseudos` (payload forwarded verbatim).
    if (typeof c._text === 'string') out.text = c._text;
    if (c._pseudo && typeof c._pseudo === 'object') out.pseudos = c._pseudo as PseudoElements;
    // `_tag`/`_role` group into `meta` — attached only when non-empty,
    // matching the v2 wire's omit-when-empty + minProperties:1 rule.
    const meta: IRMeta = {};
    if (typeof c._tag === 'string' && c._tag.length > 0) meta.sourceTag = c._tag;
    if (typeof c._role === 'string' && c._role.length > 0) meta.role = c._role;
    if (meta.sourceTag !== undefined || meta.role !== undefined) out.meta = meta;
    // Composition: nested position becomes a child-side slot ref; roots
    // carry no slot at all (spec 03).
    if (parentId !== null) out.slot = { parent: parentId, name: DEFAULT_SLOT_NAME };
    flat.push(out);
    // Recurse AFTER emitting the parent — pre-order, IRFlattener-identical.
    if (Array.isArray(c.children)) {
      (c.children as unknown[]).forEach((child, i) => visit(child, id, i));
    }
  };
  components.forEach((c, i) => visit(c, null, i));
  // Canonical v2 envelope so a second decode pass is a no-op (idempotent).
  return { irVersion: 2, minReaderVersion: 2, components: flat };
}
