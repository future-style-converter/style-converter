# wave-50 lane B1 — the declaration-validity oracle in the extractor

Ranked-queue item **0(a)**. Lane B1 owned `tools/titan/extract-fixture.mjs`
this wave and landed the change IN TREE (it is not a handover patch). No
device gate ran in wave 50, so every cell number below is a measurement of
the frozen `tools/titan/runs/wave49-final/` captures and the frozen refs at
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`,
never a new render. Deltas attribute against `corpus-v6.15`.

This `_note.md` was written by **wave-50 fix lane F3** (the lane shipped its
two JSON artifacts without one — skeptic S1's finding); it is the prose index
to them, and it also records the three oracle corrections F3 applied on top.

## Files here

| file | what it is |
|---|---|
| `recovert-differential.json` | the full 1435-test re-extract + re-convert differential: method, the one changed document, the corpus census, the three predicted cell flips with their computed metric blocks, the reproduce recipe, and 11 mutation proofs |
| `duplicate-declaration-census.json` | every rule block in the corpus that declares the same property twice — 4073 blocks scanned, **40 shadow sites in 36 tests**. This is the population for the oracle AND for the importance defect deferred below. |
| `_note.md` | this file |

---

## 1. The defect

`tools/titan/extract-fixture.mjs` collapsed a repeated declaration with a
bare `props[k] = v` — last-wins, no validity question asked — at the per-rule
collapse, at the cross-rule merges in `propsForElement` / `propsForBodyRoot`,
and in the `style=""` bag.

css-syntax-3 §2.2 (Error Handling) says an invalid declaration is **ignored,
leaving the previously declared value in force**. The converter implements
exactly that (`converter/src/main/kotlin/app/parsing/css/properties/PropertiesParser.kt`
— `Dropped invalid declaration … (css-syntax-3 §2.2)`, the `InvalidDeclaration`
sentinel) but never got the chance: the extractor had already deleted the
earlier valid declaration before the converter saw the document.

Carrier: `wave49-final css-values/angle-units-001` — web f 0.9990 · iOS
f 0.9974 · Android f 0.9966, `colorFailed` on all three, novelPx 10000.

## 2. The fix, in two pieces

* **`provablyInvalidDeclaration(prop, value)`** — a CONSERVATIVE oracle:
  it returns a reason only for a value invalid in *every* CSS context, and
  `null` otherwise. One rule today (**R1**): a `<dimension-token>` whose unit
  is outside the CLOSED CSS unit tables (css-values-4 §6.1.1 / §6.1.2 / §6.2 /
  §7.1 / §7.2 / §7.3 / §7.4, css-grid-2 §7.2.3 `<flex>`, the css-contain-3
  container-relative lengths, and — see §5 — css-speech-1 `<semitones>`).
* **`assignDeclaration(target, key, value)`** — the guarded write. It refuses
  an incoming value ONLY when that value is provably invalid AND the value it
  would overwrite is not. Invalid-over-invalid still ends on the last member;
  a valid incoming value always wins. A refused declaration contributes
  neither its value nor its `!important` flag.

**No silent fallthrough.** Every refusal is pushed onto `invalidShadowDrops`
and printed on that test's `extract.log` line as
`[validity: N shadowing declaration(s) refused — <prop>: kept '…', dropped '…' (<reason>)]`.

## 3. Blast radius — one document, and it is the intended one

From `recovert-differential.json` (both sides re-extracted AND re-converted
from one fixed converter build, HEAD `747b28e4` vs HEAD + only this lane's
hunks, each side in its own runner dir so no other wave-50 lane's in-flight
edit could enter either):

| compared | changed |
|---|---:|
| fixture files (incl. `__ref`) | 1 of **2870** |
| per-test IR documents | 1 of **1435** |
| components | **1** |

`css/css-values/angle-units-001.html`'s 100×100 div regains
`background-image: linear-gradient(green, green)`, which four INVALID later
declarations (`90degree` / `100gradian` / `1.57radian` / `0.25turns`) had
deleted. Height/Width unchanged, no other property and no other component
touched.

Corpus census: **15 774 declarations scanned, 4 provably invalid, all four in
that one test**; 4073 rule blocks, 40 shadow sites, **1** with an invalid
later value. Independently re-derived by skeptic S5 over its own scanner
(15 797 declarations, same 4 fires, same single test) and by skeptic S2's
full-corpus bisected differential
(`tools/titan/results/wave50-S1/extractor-head-differential.mjs`,
`tools/titan/results/wave50-S2/changed-documents.json`) — **both found exactly
this one changed document and nothing else.**

### Predicted flips (computed, NOT observed — no gate ran)

Scored with the campaign's own scorer (`diffWebVsRef` from
`tools/titan/inject-wpt-block.mjs`), first re-scoring each frozen capture —
which reproduced the recorded manifest numbers exactly — then re-scoring a
simulated post-fix capture built by repainting the capture's 10 000 pure-red
pixels in the ref's own rgb(0,128,0):

| cell | before | after |
|---|---|---|
| `css-values/angle-units-001` web | f 0.9990, colorFailed, novelPx 10000 | **P 0.9990**, vetoes clear, novelPx 0 |
| …iOS | f 0.9974, colorFailed, novelPx 10000 | **P 0.9974** |
| …Android | f 0.9966, colorFailed, novelPx 10000 | **P 0.9967** |

Capability evidence: `angle-units-002/003/004/005` are the SAME document with
a valid winning declaration and already pass on all three at the same SSIMs.
Skeptic S7 replayed the flip independently and called it **PASS-PLAUSIBLE, the
strongest in the wave** — the repainted web capture is *pixel-identical* to
the already-passing sibling `-002`.

**Ring-fence:** `filter-effects/backdrop-filter-basic-blur` is untouched — the
differential's one changed file is not in `filter-effects`.

## 4. Deferred, deliberately, and where the population is

* **The importance defect of the same collapse.** `a: x !important; a: y`
  still ends on `y`, where css-cascade-5 §6.4.4 says the important
  declaration wins. Skeptic S2 sharpened it: `div { color: red !important;
  color: green }` ends on `green` **and keeps `important: {color: true}`** —
  the surviving non-important declaration is promoted to important. **Zero
  corpus carriers**; the population for a future lane is
  `duplicate-declaration-census.json`'s 40 rows (e.g.
  `css-cascade/important-prop`, selector `from, to`, `border-color:
  ["green", "red !important"]`).
* **The LAYERED cascade path.** `resolveLayeredCascade` keeps the unguarded
  sort: it orders candidates by css-cascade-5 §6.4.4 and resolves
  `revert-layer` recursively, so the refusal would have to become a candidate
  filter inside that sort. All four provably-invalid declarations in the
  corpus sit in ONE *unlayered* rule block, so a layered-path filter would
  move nothing and could only risk the css-cascade cells. Stated in the code,
  not left as a silent gap.
* **A blind spot the oracle does not catch** (pre-existing, both sides, zero
  corpus carriers — S2 defect 6): `url(data:image/svg+xml;base64,…)` is torn
  at the `;` by the declaration splitter, so the value becomes
  `url(data:image/svg+xml` and can silently delete a valid earlier
  `background-image`. That is a *splitter* defect upstream of the oracle.

## 5. Wave-50 fix lane F3 — three classes of VALID CSS the oracle refused

Skeptic S5's 30-declaration adversarial probe
(`tools/titan/results/wave50-S5/oracle-probe.mjs.txt`) found the oracle
refusing three kinds of perfectly valid declaration. None had a corpus
carrier, so none was costing ink — but a "prove it or say nothing" oracle
claiming a proof it does not have is exactly the failure its own banner
forbids. All three are closed at the point named, not by widening the rule:

1. **`voice-pitch: 2st`** — `st` (css-speech-1 `<semitones>`) was missing from
   the closed unit table. The converter parses and types it (`VoicePitch`), so
   refusing it deleted a declaration the pipeline models end to end.
2. **Custom properties** (`--x: 3bananas`) — css-variables-1 §2 gives them
   `<declaration-value>`, so no unit can make one invalid.
   `provablyInvalidDeclaration` now returns `null` for any `--`-prefixed
   property, by name, before the scan.
3. **`unicode-range: U+0-7F`** — the range's tail tokenises as the number `-7`
   plus the "unit" `F`. The `<urange>` token is now stepped over whole in
   `unknownDimensionUnit`, like a string or a `url()` body. (The old banner
   called this a blind spot that "cannot bite"; "cannot bite today" is not
   "is right".)

Pinned in `tools/titan/extract-fixture.test.mjs` (`wave50-F3: …` ×3), each
mutation-proved: dropping the custom-property early return, the `<urange>`
step-over, or `'st'` turns exactly its own pin red.

Re-measured after the change: S5's probe now refuses all 10 genuinely invalid
declarations, accepts all 10 valid ones, and accepts all three previously
mis-refused adversarial cases; S5's independent corpus sweep still reports
**15 797 declarations scanned, oracle fired 4, in 1 test** — the blast radius
of the correction is zero.

**One docstring correction** landed with them, on `assignDeclaration`. It used
to justify the all-invalid case with "byte-identical to the historical
behaviour, **since the converter drops it either way**". The second half is
false: the converter does NOT drop an unknown-unit declaration, it emits a
`GenericProperty` `_unmapped` passthrough (S5 converted all ten unknown-unit
declarations of `tools/titan/results/wave50-S5/adversarial-oracle-fixture.json`
and every one came back Generic). The byte-identity claim itself survives, for
a weaker reason: in the all-invalid case this function's own behaviour is
unchanged, so the converter receives the identical input.

## 6. Mutation proofs

`recovert-differential.json → mutationProofs` lists 11, each applied to a
copy, run, and reverted — M1 oracle always null (kills 4 pins), M2 guard
removed (3), M3 ident not consumed whole (1), M4 `turn` removed from the angle
table (2), M5 escape branch removed (1), M6 `!important` recorded on a refused
write (1), M7/M8/M9 string / url() / hash bodies no longer opaque (1 each),
M10 oracle always fires (4), M11 oracle over-reaches onto
`calc()`/`attr()`/`color-mix()` (2). Plus F3's three above.

## 7. Reproduce

`recovert-differential.json → reproduce` carries the four recipes verbatim
(shadow-site census · oracle sweep · the two-runner-dir differential · the PNG
adjudication). One gotcha a repeat will hit, from S2: `split-combined-ir.mjs`
must be invoked at its REPO path — its `IS_CLI` guard compares
`fileURLToPath(import.meta.url)` against `resolve(process.argv[1])`, and a
symlinked invocation silently no-ops.
