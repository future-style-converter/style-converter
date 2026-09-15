# wave-50 skeptic lane S2 — the extractor differential

`tools/titan/extract-fixture.mjs` carries SIX lanes' changes on top of HEAD
(747b28e4): B1's validity oracle, B4's omitted-end-tag autoclose, B7's
ua-hr / root-scope / table-duplicate-text repairs, and B5's seam-S2 `<col>`
placeholder term. This lane re-ran B1's full-corpus differential over all
**1435** gate-corpus tests with the working tree on one side and
`git show HEAD:tools/titan/extract-fixture.mjs` on the other, then bisected
every changed document to the change-set that produced it.

Artifacts: `changed-documents.json` (the full changed-document list with
per-document lane attribution, the prediction accounting, the independent
censuses and the mutation-proof table).

## Method (reproduce)

Two runner dirs; every `tools/titan/` entry symlinked from the repo except
`extract-fixture.mjs` (a real copy per side) and `build-combined-fixture.mjs`
(a real copy per side, because it resolves `REPO_ROOT/fixtures/wpt` from its
own `__dirname` and a symlink would resolve back into the repo).
`WPT_DIR=<repo>/tools/wpt`, `WPT_FIXTURES_ROOT=<side>/fixtures/wpt`, STATIC
path on both sides (`POST_LOAD_EXTRACT` / `BIDI_BAKE` / `VT_BAKE` unset).
Then `build-combined-fixture.mjs --tests <section>/tests.list` per section,
`java -cp <converter/build/install/converter/lib/*.jar> app.MainKt convert
--from css --to ir -i <combined> -o <dir>` from ONE snapshot of
`converter/build` taken before lane S5 rebuilt it (both sides), then
`split-combined-ir.mjs` and `diff -rq`.

Note for a repeat: `split-combined-ir.mjs` must be invoked at its REPO path,
not through a symlink — its `IS_CLI` guard compares `fileURLToPath(import.meta.url)`
against `resolve(process.argv[1])` and a symlinked invocation silently no-ops.

**Apparatus cross-check.** My BASE per-test IR differs from
`tools/titan/runs/wave49-final`'s in **615 of 1435** documents — B1's exact
number — and my base `angle-units-001` IR is byte-identical to wave49-final's,
exactly as B1 claimed. The `gradient-hue-direction` base-vs-wave49 delta is
component-id ordinal shift only (0 differing property leaves).

## Result

**29 fixture files changed (17 distinct tests + 1 ref-only), 16 per-test IR
documents changed, 0 components added or removed, 0 of 2870 per-test IR
documents invalid against `schema/ir-v2.schema.json`.**

Every lane's prediction materialised. One document changed OUTSIDE the union:
`css/css-tables/border-collapse-dynamic-col-001.html` (B5 seam-S2), which
carries two currently-PASSING cells.

## Defects

1. **B5 seam-S2's census is one test short on the path the predicate runs on,
   and the missing test carries two passing cells.** The in-code comment says
   "exactly ONE test carries a 100x100 stamp on one of these tags". My
   independent census over the STATIC fixtures says **two**:
   `direction-upright-002` (10 `<col>`) and `css-tables/border-collapse-dynamic-col-001`
   (3 `<col>` in the test fixture, 4 more in its `__ref` fixture).
   B5 censused wave49-final's per-test IR instead, where that test was
   `[post-load: extracted+structure]` and its `<col>`s carry computed props
   (Width 21px / Height 63px), so the stamp is invisible there. Post-load
   **bailed 49×** and **declined 9×** in wave49-final, so bail-to-static is a
   live path. Cells at risk: web **P 1.0000**, android **P 0.9812**
   (ios f 0.9404).

2. **B5 seam-S2 ships with no test pin.** Emptying `NON_BOX_GENERATING_TAGS`
   leaves `node --test tools/titan/extract-fixture*.test.mjs` at 489/489 pass.
   Every other wave-50 extractor seam fails pins under an equivalent one-line
   mutation (B1 4 · B4 2 · B7-hr 6 · B7-root 3 · B7-table 2).

3. **B7's `rootScopeOf` `'star'` branch is dead code, and its stated residual
   about `*` is false.** Instrumented over the synthetic case and all 1435
   corpus tests: `parsed.needTag === '*'` is never true (STAR_HIT 0; the
   no-tag path is taken 41×), so every `*` rule is bucketed as **html** scope
   and IS re-decided by the conflict pass. Measured vs HEAD:
   `body{background:black} *{background:red}` base→black, tree→**red**;
   `body{height:200px} *{height:300px}` base→300px, tree→**200px**. Corpus
   carriers: **0** (independent census; 0 corpus tests have a linked
   stylesheet, so the inline-`<style>` census is complete).

4. **B7's `table-duplicate-text` patch is byte-for-byte subsumed by B4's
   autoclose patch.** b4 vs b4+table over all 2870 fixtures: **0 changed
   files**. Its claimed "+6 measured-safe cells, +1–3 likely" is the SAME cell
   set B4 claims; the wave's flip accounting must not add them.

5. *(pre-existing, both sides)* `div { color: red !important; color: green }`
   ends on `green` AND keeps `important: {color: true}` — the surviving
   non-important declaration is promoted to important. Sharper than B1's
   "still ends on y". Zero corpus carriers.

6. *(pre-existing, both sides)* `url(data:image/svg+xml;base64,…)` is torn at
   the `;` (value becomes `url(data:image/svg+xml`) and can silently delete a
   valid earlier `background-image`; the validity oracle does not catch it.
   B1's (e) confirmed. Zero corpus carriers.

7. **Scope caveat that applies to every wave-50 extractor lane, mine included.**
   All of these differentials are STATIC-path only. At the gate,
   `active-selection-057` and `border-collapse-dynamic-col-001` run through
   post-load, and `counter-suffix` through `[bidi-bake: baked — 2 roots, 4
   runs] [stale-runs dropped: 2] [counter-bake: baked — 10 markers]`. B4's
   `counter-suffix` prediction ("loses 12 of its 24 painted text lines") is
   therefore not gate-faithful: the autoclose rewrites `_text`/`_runs` before
   the bidi bake measures the tree and before `dropStaleRuns` runs. Untestable
   here (no browsers).

## Claims I tried to break and could not

* B1 — exactly one changed document; the IR gain is exactly the green
  gradient; invalid-over-valid keeps the earlier value; valid-over-invalid
  takes the later; invalid-over-invalid still ends on the last member; the
  guard reaches the cross-rule merge AND the `style=""` bag; a refused
  declaration leaves no `!important` behind; the drop log names the reason.
* B4 — exactly 10 tests; exactly 51 components lose a duplicate `_text`; leaf
  text byte-identical; **no character present in any base fixture is absent
  from the tree fixture** (checked over every changed document). Adversarial
  shapes (`<p>A<p>B`, `<dl><dt><dd>`, `<select><option>`, `<table><tr><td>A<td>B`,
  nested lists, `<div>BEFORE<p>P</p>AFTER`, `<ol>HEAD<li>a<li>b</ol>`) all
  improve or are unchanged; the `<p>` before a `<table>` keeps its text and
  the table becomes a sibling, as the HTML parser requires.
* B7 ua-hr — exactly 4 `<hr>` tests (independent source census);
  `active-selection-057` is breadcrumb-only and its IR is byte-identical;
  `_lossy` never reaches the wire (0 hits over 1435 IR docs); the bake declines
  atomically on an author `height` / `border` / `margin` and still bakes on an
  unrelated author declaration; the 8px margin is right for all three baking
  tests (`direction-upright-001`'s `font: 20px/1` is scoped to `body > div`,
  not to the `<hr>`).
* B7 root-scope — exactly 2 changed tests; the patched `contain-html-overflow-002`
  root differs from the PASSING `-001` root in **exactly one token**
  (`Contain ["PAINT"]` vs `["LAYOUT"]`); `inline-box-border-vlr-001` byte-identical.
* B7 table — the 5-document census reproduces exactly on an independently
  written census; stray `<tr>` text is kept; `<td>` text and nested tables
  are untouched.
* Gate net — none of the 17 changed tests appears in `tools/visual/gate-fixtures.txt`,
  `tools/visual/cross-platform-expectations.json`, or `tools/visual/baseline/`.

## Tree safety

`tools/titan/extract-fixture.mjs` was copied out and sha256-pinned before every
mutation and restored + re-verified after each one. Final sha256
`adb1e195033b32beb3fcbd4fa84a0cdecfc5245c65f964d840530fd9164f4493` — identical
to the value at lane start. No other tracked file was written; no probe test was
left in the tree.
