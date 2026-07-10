# IR — 05: Versioning policy

**Status: normative.**

## Version discovery

- A document **with** `irVersion: 2` (and the mandatory
  `minReaderVersion: 2`) is an **IR v2** document — the flat-list
  slot/placement wire, contract `schema/ir-v2.schema.json`. This is what
  the converter emits **by default** since the v2 freeze.
- A document **without** an `irVersion` key IS an **IR v1** document,
  contract `schema/ir-v1.schema.json`. v1 was a *descriptive* freeze —
  it documented what the pre-v2 converter emitted, warts included. It is
  now **deprecated**: the converter only produces it via the legacy
  `--emit-ir v1` flag (byte-identical to the pre-v2 output), and that
  flag survives for exactly **one deprecation window** (one tagged
  release), after which the flag, the nested `children` branches in
  `IRComponentSerializer`, and the renderers' recursive branches are
  deleted.
- **A reader MUST refuse a document whose `minReaderVersion` exceeds
  what it implements.** Refusal is loud (an error naming both versions),
  never a silent partial render.

## Tolerance rules (apply to every reader, every version)

1. **Unknown property `type` values MUST be tolerated — with a log**:
   skip the property, log a warning (PropertyTracker on the runtimes),
   keep rendering. This is what lets a newer converter talk to an older
   renderer within the same major version. Never crash, never drop the
   whole component. Pinned by
   `schema/conformance/fixtures/unknown-property-tolerance.json` (v1)
   and `schema/conformance/fixtures/v2/unknown-property-tolerance.json`
   (v2) on all platforms.
2. **Unknown ENVELOPE keys MUST error**: an unrecognized key on the
   document, component, slot, meta, selector/media bucket, or property
   wrapper means the document claims a contract the reader doesn't
   speak — validators MUST reject it (`additionalProperties: false` in
   both schemas). The v2 freeze closes the v1-era Android caveat
   (`ignoreUnknownKeys = true` under-enforced this rule); platform
   readers are expected to enforce it natively as their v2 decoders
   land, with CI's schema check as the backstop.
3. **Unknown `data` leaf shapes** inside a known property type: readers
   should degrade per the engine contract ("no silent fallthroughs" —
   log via PropertyTracker or TODO; see CLAUDE.md per-property rules).
4. **`slot` MUST round-trip** through every v2 reader — it is
   structural, not a droppable hint (03-children.md §2). Dropping it is
   data corruption, not tolerance.

## The v2 contract (frozen — this IS the current wire)

Landed as one sanctioned coordinated break, bundling:

- **`irVersion` + `minReaderVersion`** at the document top level
  (both `2`).
- **Flat component list with child-side `slot` refs** replacing nested
  `children` (03-children.md). `children` in a v2 document is a hard
  error; duplicate ids are a convert-time error; sibling order in the
  flat array is composition order.
- **Renames:** `_text` → `text`, `_pseudo` → `pseudos`,
  `_tag` + `_role` → `meta: {sourceTag?, role?}`.
- **PropertyScope** (SELF / CONTAINER / ITEM) classification frozen in
  `converter/src/main/kotlin/app/irmodels/PropertyScope.kt` and mirrored
  by the platform registries. Scope is NOT a wire field — it is
  derivable from the property name (decision recorded in
  03-children.md §3).
- **Defect repairs** from 02-values.md: the phantom `"u"` discriminator
  readers in the seven composite value deserializers were replaced with
  honest readers matching actual emission (wire bytes unchanged —
  reader-only fix, pinned by `ValueTypesRoundTripTest`).
- **Strictness delta:** the envelope gained the strict `slot` and `meta`
  structures; per-property `data` leaves remain permissive (the
  550-property surface is still moving) — full leaf strictness is
  deferred to a future minor/major revision.

## Deprecation timeline for v1

1. **Now (the window):** converter emits v2 by default; `--emit-ir v1`
   produces the legacy bytes with a deprecation warning on stderr; v1
   goldens stay validated by `schema/conformance/run.mjs`.
2. **After one tagged release:** the flag, the v1 serializer branches,
   the renderers' nested-children recursion, and the v1 golden run are
   deleted. `schema/ir-v1.schema.json` and the v1 goldens remain in the
   repo as historical record only.

## Change process

- **v2 additive changes** (new omit-when-absent key inside `meta`, new
  optional envelope field) → minor revision: update
  `ir-v2.schema.json`, the spec section, and add a golden fixture in the
  same PR.
- **Any change to an existing emitted byte shape** → a new major
  version, gated by this file's process. The conformance suite
  (`node schema/conformance/run.mjs`) plus the per-platform conformance
  tests exist precisely to make such a change fail CI on all four
  codebases at once.
- Emitting an `irVersion` other than `2`, or any v3 construct, before a
  v3 freeze is wrong by definition.
