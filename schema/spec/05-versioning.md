# IR v1 — 05: Versioning policy

**Status: normative.**

## v1 is implicit

The current wire format carries **no version field**. Any IR document
without an `irVersion` key IS a v1 document, and this schema
(`schema/ir-v1.schema.json`) is its contract. v1 is a *descriptive*
freeze: it documents what
`converter/src/main/kotlin/app/irmodels/IRDocument.kt` +
`IRPropertySerializer.kt` emit today, warts included (the known defects
listed in 02-values.md are part of v1, scheduled for repair at v2 — not
before, because the three runtimes are coded against today's bytes).

## Tolerance rules (apply to every reader, v1 and later)

1. **Unknown property `type` values MUST be tolerated**: skip the
   property, log a warning (PropertyTracker on the runtimes), keep
   rendering. This is what lets a newer converter talk to an older
   renderer within the same major version. Never crash, never drop the
   whole component. Pinned by
   `schema/conformance/fixtures/unknown-property-tolerance.json` on all
   platforms.
2. **Unknown ENVELOPE keys are an error**: an unrecognized key on the
   document, component, selector/media bucket, or property wrapper means
   the document claims a contract the reader doesn't speak — validators
   MUST reject it (`additionalProperties: false` in the schema).
   *Caveat:* the Android harness currently decodes with
   `ignoreUnknownKeys = true` and therefore under-enforces this rule —
   documented as current-behavior-with-caveat in 04-metadata-fields.md;
   CI's schema check is the enforcement point until the v2 freeze.
   New underscore metadata fields are the one sanctioned extension point,
   and each addition is itself a (minor) schema change that must land
   here first.
3. **Unknown `data` leaf shapes** inside a known property type: readers
   should degrade per the engine contract ("no silent fallthroughs" —
   log via PropertyTracker or TODO; see CLAUDE.md per-property rules).

## The v2 freeze (planned, NOT this contract)

flat-IR v2 is the next **major** version. Its headline changes, recorded
here so nobody implements them piecemeal against v1:

- **`irVersion` + `minReaderVersion`** appear at the document top level.
  `irVersion` states what the writer produced; `minReaderVersion` states
  the oldest reader that can safely consume it. A reader MUST refuse a
  document whose `minReaderVersion` exceeds what it implements.
- **Flat component table with slot/placement** replaces nested
  `children` (the planned change pointed to from 03-children.md).
- **Leaf strictness**: per-property `data` schemas become enforced
  (v1 keeps them permissive on purpose — the 550-property surface is
  still moving).
- **Defect repairs** from 02-values.md: the phantom `"u"` discriminator
  readers, and the fully-qualified-class-name discriminator leak.

Until the v2 freeze lands, "v2" appears in this repo **only** as prose in
this file and the pointer in 03-children.md. Any PR that emits an
`irVersion` field before the freeze is wrong by definition.

## Change process for v1

- Additive, omit-when-absent fields (new `_metadata`) → allowed; update
  `ir-v1.schema.json`, the spec section, and add a golden fixture in the
  same PR.
- Any change to an existing emitted byte shape → **not allowed** in v1;
  it belongs to v2. The conformance suite
  (`node schema/conformance/run.mjs`) plus the per-platform conformance
  tests exist precisely to make such a change fail CI on all four
  codebases at once.
