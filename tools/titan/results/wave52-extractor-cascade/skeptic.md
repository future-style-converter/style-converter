# wave-52 lane L5 · extractor-cascade — executed-repro skeptic (2026-10-05)

Tree: shared `campaign/wave52` worktree, HEAD `7d9c22a7`. Read: PLAN.md §0, §2 L5, §3, §4, §5, §9; `_note.md` (it ends
`STATUS: COMPLETE`, so the lane is complete); every file in this directory. All of my working was done in my own scratch copies.
The two tree files I touched under the lock are back at their starting hashes (`extract-fixture.mjs` `eb9742dc…5f60`,
`AngleParser.kt` `07654926…7bec`). I ran no device and committed nothing.

## Verdict

The code is sound, and its blast radius is now independently confirmed:
- the seam chain applies cleanly on the bases it names;
- the pins pass and are not vacuous;
- every mutation I executed reproduces;
- my own census reproduces the lane's numbers exactly: static path 0/2/5/5/6/22 cumulative; post-load path the same 22
  documents; F4 zero carriers in every converter input.

There is **one must-fix**, in the lane's prediction and landing record for F-E rather than in its code. Unmeasured and
contradicted, the note says F-E "moves the 9 passing cssom families toward the ref". On both native runtimes, F-E on its
own collapses the cssom inside markers onto one band. The note never records that `seam-6-FE-li` must land together with
L6's `seam-3`/`seam-4`. There are also three robustness or coverage should-fixes and four nits.

## Executed repros

1. **Seam patches, `git apply --check` on the stated bases.** Each patch was checked against a HEAD copy of the extractor
   (blob `104ef672`) with its predecessors applied in order. All six pass `--check` and apply. The stage hashes are
   s1 `944cb68f`, s2 `27bb2d9a`, s3 `9c6e0654`, s4 `4ce45bbf`, s5 `40fe4509` and s6 `02780840…e307`. The s6 hash equals
   the note's.
2. **Lane pins with the seam applied in the shared tree, under the lock.** The lock
   `tools/titan/runs/wave52-lock/extract-fixture.mjs` was taken from 18:14:10 to 18:14:13.
   - Hash before: `eb9742dc`. I ran `git apply --check` and then applied seam-1 through seam-6, giving `02780840`.
   - Results:

     | suite | pass | fail |
     |---|---:|---:|
     | `extract-fixture` | 483 | 0 |
     | `extract-fixture-root-scope` | 6 | 0 |
     | `extract-fixture-col-placeholder` | 4 | 0 |
     | `extract-fixture-replaced-src` | 14 | 0 |
     | `extract-fixture-ua-hr` | 7 | 0 |
     | `extract-fixture-table-text` | 5 | 0 |
     | `attr-bake` | 53 | 0 |
     | `bidi-bake` | 50 | 0 |
     | `widget-appearance-bake` | 13 | 0 |

   - I restored the file with `git show HEAD:… >`. The hash after was `eb9742dc` (equal), `git diff --quiet` was clean, and
     I removed the lock with `rmdir`.
   - With HEAD's extractor, the tree's test file is 463 pass / 20 fail, and the 20 failures are exactly the L5 pins. That
     matches "20 of 22 red by design". The two anchors pass at HEAD and are killed by M2 and M4, so neither is vacuous.
3. **HEAD's whole titan tooling suite, stage 0 versus stage 6.** I ran `node --test tools/titan/*.test.mjs` from a
   `git archive HEAD` copy. Stage 0 gives 1643 tests, 1587 pass, 22 fail. Stage 6 gives the same counts, and the set of
   failing test names is IDENTICAL. All 22 failures are environmental: the frozen copy has no `apps/` or `runtimes/`. So
   the seam breaks no existing tooling test.
4. **Mutations.** I wrote my own executor (`sk-mutate.mjs`). It requires each replacement to hit exactly once, runs the
   whole extractor suite, and restores the file with a sha check. Every one of the 13 restores came back sha-EQUAL to
   `02780840`.
   - Three lane mutations, re-executed. Each reproduces the lane's red set exactly:

     | mutation | what it breaks | pins red |
     |---|---|---:|
     | M7 | the gate widening | 5 |
     | M11 | host bucket merged unsorted | 2 |
     | M24 | `li` not exempt | 1 |

   - Ten mutations of my own:

     | mutation | what it breaks | pins red |
     |---|---|---:|
     | SK2 | R2 upper bound raised to 1000 | 2 |
     | SK3 | specificity direction reversed in the layered resolver | 1 |
     | SK5 | a normal write never clears the importance flag | 1 |
     | SK7 | the F3 set-aside log is dropped | 2 |
     | SK9 | the `:nth-child(… of S)` argument is ignored | 1 |
     | SK10 | `summary` not exempt | 1 |
     | **SK1** | R2 reads only the FIRST colour operand | **0 — survives** |
     | **SK4** | `:not()` counted as a plain pseudo-class | **0 — survives** |
     | **SK6** | `keepAll` ignored | **0 — survives** (equivalent mutant: `keepAll` is dead logic) |
     | **SK8** | the bad-string newline rule is dropped | **0 — survives** |

   - F4, run in an isolated HEAD copy of `converter/` that carries only L5's five files, so L1's in-flight `ColorParser.kt`
     is absent:
     - Clean focused run: 42 tests (HslHue 14, Angle 4, GradientPrefixGuardSoundness 9, InvalidDeclarationDrop 15), all
       green.
     - F4-a (drop the exponent group): 4 RED, exactly the lane's set.
     - F4-b (drop `IGNORE_CASE`): 1 RED.
     - My SK-F4c (no sign allowed in the exponent): 2 RED.
     - Every restore was sha-EQUAL to `07654926`, and the clean re-run was green.
     - The angle-consuming classes (`*Transform*`, `*Rotate*`, `*Filter*`, `*Gradient*`, `*Color*`, `*Angle*`, `*Hue*`,
       `*Conformance*`) ran 97 tests in 12 classes with 0 failures.
5. **Corpus census with my OWN scripts.** I wrote `sk-l5-census.mjs` (a fixture-level leaf diff) and `sk-wire-census.py`.
   I did not read the lane's JSON or `differential.sh`.
   - **Static path.** Seven sides, each a frozen HEAD `tools/titan` plus one stage extractor, over the 1435-test union;
     every side reported 1435 ok. Docs changed, per stage:

     | stage | F3 | F1 | F-D | F2 | F-C | F-E |
     |---|---:|---:|---:|---:|---:|---:|
     | new this stage | 0 | 2 | 3 | 0 | 1 | 16 |
     | cumulative | 0 | 2 | 5 | 5 | 6 | **22** |

     The documents are the same as the lane's:
     - F1: revert-val-002 `display revert → block`, and -024 `9px dotted blue → 10px solid pink`.
     - F-D: import-conditional-001 and -002 `background red → green`, and important-prop `color red → #00f`.
     - F-C: color-mix-percents-02, components 6 and 7.
     - F-E: 15 `cssom/*` documents plus `override-in-shadow-dom`.
     - No ref fixture moved.
   - **Post-load path** (`POST_LOAD_EXTRACT=1`, stage 0 versus stage 6). Both sides report 1434 ok and the same single
     FAIL (`first-letter-of-html-root-refcrash`), with 277 post-load extracted. The **same 22** documents change.
     `override-in-shadow-dom` changes in KEY ORDER ONLY (position, width, height), which I verified.
   - **F4.** Across all 5738 extracted fixtures (static and post-load), no file contains an exponent-form angle or an
     upper- or mixed-case angle unit. F4 is therefore byte-inert on the corpus, which independently confirms the lane's
     jar differential.
   - **Wire census.** The [Width 100, Height 100] placeholder appears 88 times by tag: `li` 51 (15 docs, all `cssom/`),
     untagged 21, `slot` 6, `section` 5, and 1 each for `template`, `img`, `wbr`, `p` and `html`. Out-of-range color-mix
     percentages: components 6 and 7 of color-mix-percents-02 only. Exponent-form angles on the wire: 0.
   - **Source census.** 10 sources carry `!important`, and the list is identical to the lane's. 2 sources have a `;`
     inside `url(` (the first-letter pair). 15 sources use `@layer` or `revert-layer`, where the lane says 13 (see nit N2).
6. **PNG review.** I compared the wave51-fix captures for all three platforms with the frozen ref (`9b5435e5…/…-htmlpins`):
   - **import-conditional-002 ×3.** A red 100×100 square at (16,88) where the ref is green. The IR goes to green, and the
     natives already paint the `background` shorthand. The cells fail only on `colorFailed` (colorComposite 0.937 / 0.933 /
     0.932). **The predicted f→P is plausible (HIGH).**
   - **import-conditional-001 ×3.** The same square, with green "FAIL" text that becomes invisible on green. Plausible
     (HIGH).
   - **color-mix-percents-02 ×3.** All three platforms paint 5 of the ref's 7 purple rows; rows 6–7 are white. Row t1
     already paints the same `rgb(68.4898% …)` on every platform. **Plausible** (web HIGH, natives MED).
   - **flex-gap-decorations-024 ×3.**
     - Every platform draws blue dots where the ref has a solid 10 px pink rule.
     - I sampled the intersection (x 62–80, y 62–80) on all four images. On every platform the green row rule is painted
       OVER the column rule, exactly as in the ref.
     - So the lane's replay assumption ("pink under the green row rule") matches each runtime's z-order. **P→P ≈1.0 is
       plausible.**
   - **revert-val-002 web.** Today this is a degenerate P (a mostly red box at 0.999). After the fix the span becomes a
     block and fills the box green. Plausible. The natives already paint the box fully green.
   - **important-prop ×3.** The lane did not replay this cell, so I did (`sk-replay-ip.mjs`, using HEAD's `diffWebVsRef`).
     I recoloured the red "FAIL" text inside the 80×80 content box (x 26–105, y 98–177; 177 / 185 / 158 px) to blue over
     green:

     | platform | SSIM | colorComposite |
     |---|---|---|
     | web | 0.999 → 0.9966 | 0.9753 → 0.9729 |
     | ios | 0.9973 → 0.995 | 0.9718 → 0.9695 |
     | android | 0.9966 → 0.9946 | 0.9709 → 0.9689 |

     The movement stays below the 0.005 mover band and far from the colour-failure level, so the cell holds P. The
     picture was already wrong (red border from the un-baked keyframes).
   - **cssom name-setter ×3 (F-E).** Markers today sit at a 100 px pitch, where the ref uses 20 px. See defect D1.
7. **Ownership.**
   - Every file in `git diff --name-only` / `git status` that carries an L5 fingerprint is on the lane's `own:` list:
     `AngleParser.kt`, the new `AngleParserTest.kt`, `InvalidDeclarationDropTest.kt`, `ColorParserHslHueTest.kt`,
     `GradientPrefixGuard.kt` (KDoc only, code unchanged) and `extract-fixture.test.mjs` (one +538 hunk).
   - The seam is clean in the tree, sha = HEAD.
   - **No foreign edit.**
   - The other checks pass:
     - No test-name carve-out in any seam code line; names appear in comments only.
     - The ring-fenced `backdrop-filter-basic-blur` appears nowhere.
     - The new files stay under 200 lines: `AngleParserTest.kt` has 84, and the lane scripts have 87–134.
     - No device A/B is staged, so no hash sentence is owed.
     - The watchlist additions pass the check (`WATCH=… watchlist-check.mjs` → `unmatched 0`).
8. **Seam coexistence.** The plan says L5 is "the only lane on this seam", but two other lanes deliver hunks to
   `extract-fixture.mjs`:
   - L1's comment-only `hunk-for-L5-1.patch`. It was written 35 s after L5's note.
   - L11's `seam-3.patch`, at `:10054`.

   Both `git apply --check` cleanly ON TOP of L5's stage 6. The combined file passes `node --check`, and L5's pins stay
   at 481 pass / 0 fail on it.

## Defects

- **D1 — must-fix — F-E native prediction and landing coupling.**
  - **What the note claims.** `_note.md` lines 139–140 say "F-E … 0 flips alone; the 9 passing cssom families move toward
    the ref (20-px rows); falsifier: any of them drops below 0.95". Line 177 says "Received: none".
  - **It was never measured.** `png-replay.json` contains no F-E cell.
  - **The evidence contradicts it:**
    - L6's Catalyst probe (`wave52-counters-and-lists/_note.md` §T7 item 2 and §6) found that after F-E the 51 empty
      inside `<li>` put "all three markers on ONE band `[(16,28)]`" on both natives.
    - L6 states that the 18 passing native cssom cells (additive, fallback, name and range ±invalid, plus
      symbols-invalid, P 0.976–0.988) "are then at P→f risk".
    - L6 states that `seam-3`/`seam-4` "must land WITH L5's seam-6-FE-li".
    - HEAD's code agrees. In `ComponentRenderer.swift`, the `insideOverlay` branch (`.overlay` never participates in its
      host's sizing) paints an inside marker on a text-less item onto a zero-height item; the Compose overlay is its
      stated twin.
  - **Why it matters.** PLAN §4 lands L5 at step 3 and L6 at step 5. Applying `seam-6` on its own produces a wrong native
    render on 18 passing cells.
  - **Fix.**
    - Amend the note's F-E prediction for the natives.
    - Label the 18 native P cells "at risk" until L6 T7 lands.
    - State the atomic landing (seam-6 together with L6 seam-3/seam-4, or L6's pair first).
    - Record L6's hand-off under Received.
- **D2 — should-fix — `selectorSpecificity` throws on valid CSS.**
  - **Failure.** `readIdent` does `m[0]` on a null regex match when a selector's trimmed text ends in a lone backslash.
    `parseCss('.foo\\ { color: red }')` (class "foo ", an escaped trailing space that `sel.trim()` strips) throws
    `TypeError: Cannot read properties of null (reading '0')` at stage 6. At stage 0 it parses.
  - **Scope.** Three probes all threw. `parseCss` now stamps specificity on every rule, so the throw fails the whole
    test's extraction.
  - **Corpus impact.** No carrier (1435/1435 ok on both sides).
  - **Fix.** One line: `i += m ? m[0].length : 1`.
- **D3 — should-fix — F4 makes non-finite angles reachable.**
  - **Failure.** `rotate(1e999deg)` and `linear-gradient(1e999deg, red, blue)` now convert to
    `{"_serializationError": "Unexpected special floating-point value Infinity …"}`, so the property is lost on every
    runtime. At HEAD the same inputs are a Generic or Raw passthrough. Executed both ways in the scratch converter.
  - **Scope.** No corpus carrier. The hazard existed before through 309-digit numbers; F4 makes it reachable with eight
    characters.
  - **Fix.** In `AngleParser`, return null, or clamp, when `!numValue.isFinite()` or the converted degrees are not
    finite (css-values-4 range clamping).
- **D4 — should-fix — pin gaps (surviving mutants).**
  - **SK1:** R2 is never exercised with only the SECOND operand out of range. Add `color-mix(in srgb, red, blue 150%)`.
  - **SK8:** the splitter's bad-string newline rule, which the doc-comment promises, is unpinned.
  - **SK4:** `:not(#id)` is unpinned, because `div:not(.x)` gives (0,1,1) under both rules.
- **N1 — nit.** `keepAll` in `resolveLayeredCascade` is dead logic. When every `all` candidate is `revert-layer`,
  `resolveOne('all')` already returns undefined (SK6 is an equivalent mutant). Its comment says it is needed.
- **N2 — nit.** The note's "F3: 13 sources use @layer / revert-layer" undercounts. My count is 15: `revert-layer-009` and
  `revert-layer-012` carry `revert-layer` in `style=""`. This census is descriptive only; the differential, which agrees
  with mine, is the authority.
- **N3 — nit.** The note says BIDI_BAKE / VT_BAKE "neither reads the cascade code L5 changed". Both bakes re-enter
  `extractFixture` for their static pass, and `bidi-bake.mjs:1089` calls `parseCss`. In practice this is inert: they
  post-process the static fixture the differential measured, and none of the 22 changed documents is bidi- or VT-baked
  (wave51-fix manifest flags are 0 for all). Reword the claim.
- **N4 — nit.** The test-file banner says the mutations run "M1…M23"; there are M1–M25.
- **O1 — should-fix, for the orchestrator.** `tools/titan/results/wave52-extractor-cascade/` is untracked, and the six
  seam patches are the ONLY copy of an implementation that was already lost once to the scratchpad purge. PLAN §0 says to
  commit it now. Also note L1's and L11's extractor hunks (item 8): they are compatible with L5's chain if applied after it.

## Appendix — the census script (scratchpad is purgeable, so it is kept here)

```js
// sk-l5-census.mjs A B label — leaf-level diff of two extracted-fixture trees
import fs from 'node:fs'; import path from 'node:path';
const [,, A, B, label] = process.argv;
const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).flatMap((e) => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);
const rel = (r) => new Map(walk(r).filter((f) => f.endsWith('.json')).map((f) => [path.relative(r, f), f]));
const leaves = (o, p = '', out = new Map()) => { if (o && typeof o === 'object') { for (const k of Object.keys(o)) leaves(o[k], p + '/' + k, out); if (!Object.keys(o).length) out.set(p, Array.isArray(o) ? '[]' : '{}'); } else out.set(p, JSON.stringify(o)); return out; };
const a = rel(A), b = rel(B); const changed = [];
for (const [k, fa] of a) { if (!b.has(k)) continue; const ta = fs.readFileSync(fa, 'utf8'), tb = fs.readFileSync(b.get(k), 'utf8'); if (ta === tb) continue;
  const la = leaves(JSON.parse(ta)), lb = leaves(JSON.parse(tb)); const d = [];
  for (const [p, v] of la) if (!lb.has(p)) d.push(`- ${p}`); else if (lb.get(p) !== v) d.push(`~ ${p}: ${v} -> ${lb.get(p)}`);
  for (const [p] of lb) if (!la.has(p)) d.push(`+ ${p}`); changed.push({ k, orderOnly: !d.length, d }); }
console.log(label, 'changed', changed.length); for (const c of changed) console.log(c.k, c.orderOnly ? '[KEY ORDER ONLY]' : '', c.d.slice(0, 4));
```

Each side is built with `git archive HEAD` (`tools/titan`, `tools/visual`; results, runs, fixtures and fonts excluded),
with that stage's extractor swapped in. It then runs
`env -u POST_LOAD_EXTRACT -u BIDI_BAKE -u VT_BAKE WPT_DIR=<repo>/tools/wpt WPT_FIXTURES_ROOT=<side>/out node <side>/tools/titan/extract-fixture.mjs $(cat tests.all)`.
For the post-load sides, set `POST_LOAD_EXTRACT=1` and link `apps/web-harness/public/fonts` and `tools/titan/fonts`.

## Re-verify (2026-10-05, after the lane's fix pass for D1)

Tree: shared `campaign/wave52`, HEAD `7d9c22a7`. The note still ends `STATUS: COMPLETE`. I ran no device and committed
nothing. Every tree file I touched is back at its starting hash: extractor `eb9742dc…5f60` (= HEAD), test file
`1f78fcef…50db` (L5's tree copy, blob `e8b95f1`). Scratch work lives in the session scratchpad. The one script that
matters is kept below.

### Verdict
**D1 is FIXED, and I found no regression.**
- I re-derived every number in the amended F-E prediction independently: the 18 native P → f when F-E lands alone,
  and 18 P → P when it lands with L6's seam-3/seam-4.
- I confirmed the one-band geometry with my own Catalyst raster of the HEAD renderer.
- The landing coupling is mechanical. The F-E pin travels inside seam-6, so step 3 (seam-1…5) is green without it.
  A tree that has the pin but not the exemption is red.
- No must-fix remains. D2–D4 and N1 stay open as the note's "Open" list says (D2 re-executed: still throws at stage 6).

### Executed checks for D1
1. **Seam chain on its stated bases** (a scratch copy: HEAD extractor + the tree's test file).
   - Each patch passed `git apply --check` and then applied. The stage hashes are s1 `944cb68f`, s2 `27bb2d9a`,
     s3 `9c6e0654`, s4 `4ce45bbf`, s5 `40fe4509`, and s6 `02780840…e307` with test file `8e5b9e0c…c2b9`. These equal
     the note's hashes and my first review's s1–s6.
   - The extractor hunks of seam-6 are therefore byte-unchanged.
   - seam-6 now touches two files. It also passes `--check` on bare HEAD, so it does not depend on seam-1…5. That
     makes the step-5 landing order-robust.
2. **Pins, frozen `git archive HEAD` copy:**

   | extractor | test file | tests | fail | what it shows |
   |---|---|---:|---:|---|
   | HEAD | tree | 482 | **19** | exactly the F1/F2/F3/F-C/F-D pins, by name; there is no F-E pin in the tree (21 `wave52-L5` tests there, 22 with seam-6) |
   | s5 | tree | 482 | 0 | the step-3 landing |
   | s6 | tree | 482 | 0 | |
   | s6 | s6 | 483 | 0 | the step-5 landing |
   | **s5** | **s6** | 483 | **1** | the F-E pin without the exemption is RED, so the pin is not vacuous |

3. **Under the lock, in the shared tree** (`wave52-lock/extract-fixture.mjs` and `…/extract-fixture.test.mjs`).
   - First attempt, 19:01:10–19:01:11. zsh did not split my suite list, so no suite ran. The patches were applied and
     restored sha-equal anyway.
   - Real run, 19:01:24–19:01:30. Seam-1…5 gave `40fe4509`. Seam-6 on top gave `02780840` and `8e5b9e0c`.

     | suite | step 3 pass | step 5 pass |
     |---|---:|---:|
     | `extract-fixture` | 482 | 483 |
     | root-scope | 6 | 6 |
     | col-placeholder | 4 | 4 |
     | replaced-src | 14 | 14 |
     | ua-hr | 7 | 7 |
     | table-text | 5 | 5 |
     | `post-load-extract` | 100 | 100 |
     | `attr-bake` | 53 | 53 |
     | `bidi-bake` | 50 | 50 |
     | `widget-appearance-bake` | 13 | 13 |

     Every suite had 0 failures at both steps.
   - Restore: the extractor came back via `git show HEAD:… >`, and the test file from my saved copy. Both are
     sha-equal, `git diff --quiet` on the extractor is clean, and both locks were released.
4. **Mutations, re-executed with my own runner** on s6 + the s6 test file. Each replacement must hit exactly once, the
   whole suite runs, and every restore is sha-EQUAL to `02780840`.

   | mutation | pins red |
   |---|---:|
   | M24 (drop `li`) | 1, the F-E pin |
   | M25 (add `div`) | 5: A-RC1 part 2, B-RC2, wave30 A4 ×2, F-E |
   | RV-a (delete the `!LIST_ITEM_TAGS` guard line) | 1 |
   | RV-b (exempt by "tag starts with `l`") | 1, killed by the `<summary>` arm |

   M24 and M25 match `mutations.json` exactly.
5. **F-E native replay, re-derived with my OWN script** (`rv-fe-ink.mjs`, below).
   - **Method.** It does not use the lane's band-crop or darkest-pixel compositing. It re-implements the scorer's ink
     definition (any channel more than 8 off white, magenta sentinel excluded). It assigns each ink pixel to its list
     item by the 100-px placeholder pitch, and counts the UNION of the item masks with every item at the list's top
     (zero-size overlay) or at a 16-px pitch (L6's pair). The ratio is count over count, because the scorer pads both
     sides to one shared canvas.
   - **Apparatus.** My "today" ink equals the manifest's `semanticPresence` for all 30 native cssom cells. My first cut
     was wrong on name-setter, because I ignored its 680-px capture; I corrected it.
   - **Results:**

     | scenario | native P cells | coverage ratio (mine) | lane's ratio | coverage veto (ratio > 2) |
     |---|---:|---|---|---|
     | F-E alone | 18 | 2.23–2.83 | 2.22–2.87 | **18 / 18 vetoed (P → f)** |
     | with L6's pair | 18 | 1.20–1.44 | 1.20–1.43 | 0 / 18 |

     Every cell is within 0.09 of the lane's number. The 12 native f cells stay f in both scenarios.
   - The lane's own `fe-native-replay.mjs`, re-run with a HEAD copy of `inject-wpt-block.mjs`, reproduces its JSON
     byte for byte (summary and replays). Its summary: `feAloneLost` 18, `feSeam34Lost` 0, `webLost` 0, and the
     11 web P cells reach 0.992–0.998.
6. **The one-band geometry, measured myself on Catalyst** in an isolated `git archive HEAD` copy of `Package.swift` +
   `runtimes/swiftui`. This did not touch the shared tree; the `ComponentRenderer.swift` lock is held by another lane.
   - I added only L6's `ListMarkerEmptyItemRasterTests.swift`, extracted from `seam-4.patch`. That is the verbatim
     post-F-E cssom-pad `<ol>` with three empty inside `<li>`.
   - HEAD renderer: `bands: [(top: 16, bottom: 28)]`, so ONE band; the test went RED, exit 65.
   - Same copy + L6's `ListMarkerEmptyItem.swift` (copied read-only from the tree) + seam-4's renderer hunk:
     `[(16,27),(32,43),(48,59)]`, `TEST SUCCEEDED`.
   - So L6's log is reproduced independently. On Android (no JVM raster) I read HEAD's code:
     - `RenderListItemMarker` takes the zero-size overlay for an inside item without text.
     - The composed-WPT path skips the 50×30 placeholder floor.
     - `PlaceholderContent` renders nothing in WPT mode.
     - So an empty `li` is 0×0 and its markers overlap. That agrees with the lane.
7. **PNGs.**
   - I opened the frozen ref and the wave51-fix ios capture of `cssom-name-setter`. The ref has six rows at a 20-px
     pitch (A–C, X–Z). The capture has rows at a 100-px pitch.
   - I painted my predictions:
     - **F-E alone:** two smudged `1./2./3.` clusters, about one third of the ink. A coverage failure is the right reading.
     - **With L6's pair:** six rows at a 16-px pitch, the same ink as today and nearer the ref's rows. P holds, and SSIM
       rises plausibly.
   - The additive capture is the same shape: `0.`/`1.`/`2.` at a 100-px pitch against A./B./C. at 20 px.
8. **Hand-off and coupling partners.**
   - L6's `seam-3.patch` and `seam-4.patch` both pass `git apply --check` on HEAD.
   - L6's `ListMarkerEmptyItem.kt` lists `Height` in `DECLARED_BLOCK_SIZE`. So L6's pair applied FIRST is inert on
     today's 100×100 cssom items, and the note's "order inside step 5 is free" holds.
   - L6's note §6 and §8 carry the reciprocal coupling.
   - L1's `hunk-for-L5-1.patch` and L11's `seam-3.patch` (extractor part) compose with the re-cut chain in both orders
     and give the same file `831aa02d43df`. `node --check` passes, and the L5 pins are 483 tests, 0 fail.
9. **Watchlist.** `WATCH=watchlist-additions.txt watchlist-check.mjs` reports 21 lines, every one matched, and
   `unmatched 0`. The plan's `css-counter-styles/cssom/` prefix line (`watchlist.txt:107`) also covers the 18 cells.
10. **Ownership, re-checked.**
    - Every changed or untracked file carrying an L5 fingerprint is on the `own:` list: `AngleParser.kt`,
      `AngleParserTest.kt`, `InvalidDeclarationDropTest.kt`, `ColorParserHslHueTest.kt`, `GradientPrefixGuard.kt` and
      `extract-fixture.test.mjs`.
    - The fix pass touched only the test file in the tree, plus this directory. `AngleParser.kt` is still `07654926`.
    - No foreign edit.
    - The fix pass staged no device A/B, so no hash sentence is owed.

### Residual (none must-fix)
- **D2, D3, D4 and N1: open, as the note declares.**
  - D2 was re-executed: `parseCss('.foo\\ { color: red }')` throws `Cannot read properties of null (reading '0')` at
    s6 and parses at HEAD.
  - None of the four has a corpus carrier.
- **R1 — nit, for the orchestrator.** PLAN §2 L5 still reads "F-E 0 alone" and "the 9 passing cssom tests (F-E moves
  them toward the ref)". §4 still lists L5 whole at step 3. The plan is the orchestrator's, not the lane's, and the
  lane's "Landing" section plus the first lines of the seam-6 header supersede it. The orchestrator must apply
  seam-1…5 at step 3 and seam-6 at step 5 with L6's seam-3/seam-4.
- **R2 — nit.** The note's "F-E alone, confidence HIGH" for Android rests on code reading. I agree with that reading
  (item 6), but it is not a raster. Because the coupling forbids that state, nothing ships on it.

### Appendix — `rv-fe-ink.mjs` (the independent replay; the scratchpad is purgeable)
```js
// usage: node rv-fe-ink.mjs <repo>  — ink = any channel > 8 off white, magenta sentinel excluded
import fs from 'node:fs'; import { createRequire } from 'node:module';
const R = process.argv[2]; const { PNG } = createRequire(R + '/package.json')('pngjs');
const SD = R + '/tools/titan/runs/wave51-fix/sections/css-counter-styles';
const m = JSON.parse(fs.readFileSync(SD + '/manifest.json', 'utf8')).wpt.results;
const isInk = (d, i) => !(d[i] === 255 && d[i + 1] === 0 && d[i + 2] === 255) && (255 - d[i] > 8 || 255 - d[i + 1] > 8 || 255 - d[i + 2] > 8);
function analyse(img) { const { width: W, height: H, data: d } = img; let tot = 0; const ink = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = (y * W + x) * 4; if (d[i] === 255 && d[i + 1] === 0 && d[i + 2] === 255) continue; tot++; if (isInk(d, i)) ink.push([x, y]); }
  return { tot, ink }; }
function bands(ink) { const ys = [...new Set(ink.map((p) => p[1]))].sort((a, b) => a - b); const out = []; for (const y of ys) { const b = out[out.length - 1]; if (b && y - b.bot <= 4) b.bot = y; else out.push({ top: y, bot: y }); } return out; }
for (const [k, r] of Object.entries(m)) { if (!/\/cssom\//.test(k)) continue; const stem = k.split('/').pop().replace('.html', '');
  const ref = analyse(PNG.sync.read(fs.readFileSync(R + '/' + r.browserRef.path)));
  for (const [plat, dir] of [['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    const cap = analyse(PNG.sync.read(fs.readFileSync(`${SD}/${dir}/wpt__css-counter-styles__cssom__${stem}.png`)));
    const lists = []; for (const b of bands(cap.ink)) { const L = lists[lists.length - 1]; if (L && L.length < 3 && Math.abs(b.top - L[L.length - 1].top - 100) <= 3) L.push(b); else lists.push([b]); }
    const which = (y) => { for (const L of lists) for (let j = 0; j < L.length; j++) if (y >= L[j].top && y <= L[j].bot) return { L, j }; return null; };
    const sim = (pitch) => { const set = new Set(), sh = []; let acc = 0; lists.forEach((L) => { sh.push(acc); acc += L.length * (100 - pitch); });
      for (const [x, y] of cap.ink) { const w = which(y); if (!w) { set.add(x + ',' + y); continue; } set.add(x + ',' + (y - sh[lists.indexOf(w.L)] - w.j * (100 - pitch))); }
      return set.size; };
    const refN = ref.ink.length, ratio = (n) => +(Math.max(n, refN) / Math.min(n, refN)).toFixed(2);
    console.log(stem, plat, r.browserRef.diffs[plat + '-ref'].wptPass ? 'P' : 'f', 'alone', ratio(sim(0)), 'seam34', ratio(sim(16)), 'today', ratio(cap.ink.length));
  } }
```
