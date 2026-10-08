# wave53-lists-bakes (L1) — adversarial skeptic

Tree: the shared tree, HEAD `e330e255` (campaign/applier-campaign = dev cdb8a845 + the wave-53 plan, gate record and tools).
Read: `_note.md`, PLAN §0/§1/§2 L1/§3/§4/§6/§10, `expectations.json` `lanes.L1-lists-bakes`, the diff on every owned path,
`seam-1.patch`, `u2-narrow.patch`, `mutations.log`, `seam-1.verify.log`, and the lane's probe and differential scripts.
Every repro below was executed. My scripts and logs are in `tools/titan/results/wave53-lists-bakes/skeptic/`: `smut.py` is
my own mutation runner (not the lane's `mutate.sh`), and `smut.log` holds full sha256 before, mutated and restored for every
mutation.

## Verdict: MIXED. The lane's claims hold; there are 0 must-fix and 7 should-fix defects

What holds:
- Every pin the plan names fails under an executed mutation and passes again after a byte-exact restore. I used my own
  mutation strings.
- My own census matches the lane's blast radius in every count. It is **not** under-reported.
- Every quoted score matches `cells.mjs`.
- The PNGs show the geometry the lane describes.
- `seam-1.patch` applies clean on HEAD, and the lane's pins pass with it applied (I ran them again under the lock).
- No seam file is left modified and no lock directory is left behind.
- No edit falls outside L1's ownership list.

What is wrong:
- One hard-rule violation: a **silent fallthrough** in hunk M, contradicting the module's own banner.
- One orchestrator-window instruction that cannot work as written (the wire differential's pre-tree).
- Five mechanism branches that no pin covers: mutations survive them.
- One latent geometry bug in the marker glyph tops.
- One honesty gap: the counter-suffix native cells stay partly wrong after U2.

## Executed repros (outputs abridged; full lines in `skeptic/smut.log`, `skeptic/seam.log`)

### (a) Pin mutations, replayed with my own strings: red, byte-exact restore, green

| id | file | my mutation | red (exactly) | restored |
|---|---|---|---|---|
| SN1 | extract-fixture.mjs (seam-1 applied, lock) | `findImpliedClose` ignores depth | helper-2, N2, N1 (3/4) | byte-exact, 4/4 |
| SN2 | same | revert the `scanOwnText` site only | N2 only | byte-exact |
| SN6 *(extra)* | same | revert the `walkChildren` site only | N1 only, so each site is pinned on its own | byte-exact |
| SN7 *(extra)* | same | boundary closers never un-nest | helper-2 | byte-exact |
| SN3a | counter-bake.mjs | drop the step-4 term | nested + 008b | byte-exact, 27/27 |
| SN3b | same | always add the step-4 term | `counter-list-item` ANCHORS + li-value-reversed-013 | byte-exact |
| S-N4a | PseudoTextFold.swift (Catalyst, private DerivedData) | restore the blanket runs refusal | N4 + child-first + after-refused (3 cases) | byte-exact, TEST SUCCEEDED 24/24 |
| S-N4b *(extra)* | same | no text-slot prefix | N4 | byte-exact |
| S-N5a | PseudoTextFold.kt (JVM) | keep `before` in the copy | N5 (53 run, 1 fail) | byte-exact, 53/53 |
| S-N5b / S-N5c *(extra)* | same | no `_text` prefix / no runs[0] prefix | N5 | byte-exact |
| S-sv1a | bidi-marker-bake.mjs | `boxProps: {}` (no `none`) | V1 + V4 | byte-exact |
| S-sv1b | same | analytic edge `contentStart − w` for rtl | V1 + V5 | byte-exact |
| S-sv1c | same | no per-grapheme split | V1 | byte-exact |
| S-sv5a | same | use the CDP box whatever its width | V5 | byte-exact |
| S-svf | same | rethrow in `collectMarkerFacts` | VF | byte-exact |
| S-sv2 | bidi-bake.mjs | emit markers for roots | V2 | byte-exact |
| S-sv3a / 3b / 3c | same | no `padding: '0'` / no content-box guard / no longhand delete | V3 (×2 / ×1 / ×1) | byte-exact |
| S-sv3d | same | drop the NON-zero guard | V3b | byte-exact |
| S-sv5b | same | a mismatch becomes a whole-test `{ bail }` | V5 | byte-exact |
| S-svx7 *(extra)* | same | push no marker runs | V1 | byte-exact |
| S-V3c | bidi-bake.mjs + `u2-narrow.patch` (in an EXPORT copy, never the shared tree) | drop the marker-host condition | V3c | byte-exact; `patch -R` returns both files to the U2 sha256 `5bce8710…` / `91ac67dd…` named in the patch header |

The seam was applied under `tools/titan/runs/wave53-lock/extract-fixture.mjs` (mkdir mutex, 12:20:37 to 12:20:39):
- sha256 before `831aa02d…ee721`, patched `845a3d26…fe5c9` (the same as the lane's), restored `831aa02d…ee721`;
  `git diff --quiet` clean.
- With the seam applied, the lane's six focused node files run **638/638**. Without it, 634/634; the 4 implied-close pins are
  red by design.

### Mutations that SURVIVE (no pin can see them), so there are untested branches

| id | file | mutation | result | what is unpinned |
|---|---|---|---|---|
| SN3C | counter-bake.mjs | pseudo `who` = `nodeIdx` instead of `` `${nodeIdx}::${name}` `` | 114/114 green (counter-bake + generated-content + bidi) | the pseudo-attribution key. `skeptic/pseudo-setter-repro.mjs` P1 prints `12,10,9` (= the §4.4.2 arithmetic) and **`11,10,9` under SN3C**, with no pin red |
| S-svx2 | bidi-bake.mjs | delete the box `_lossyReasons` merge in `applyBidiBakePlan` | 98/98 green | every marker stamp (`marker-not-baked`, `-box-modelled`, `-probe-mismatch`, `-text-modelled`) can vanish from the FIXTURE unseen. V1/V5 assert only `plan.boxes[].lossy` |
| S-svx5 | bidi-marker-bake.mjs | `modelMarkerTexts` without the ". " space restore | 98/98 green | the string fallback; the analytic-box glyph x would shift 4.48 px |
| S-svx6 | bidi-marker-bake.mjs | `textModelled: false` | 98/98 green | the `marker-text-modelled` stamp is never computed under test |
| S-V3c′ | u2-narrow (export) | `hostsMarker` ignores `m.runs?.length` | green | under P-narrow, a root hosting only a DECLINED marker would count as a host |

### (b) Blast-radius census: my own scripts, against the lane's

| quantity | lane | skeptic | method (mine) |
|---|---|---|---|
| per-test IR wave52-ship ≡ wave53-open | 1435/1435, 0 differ | **1435/1435, 0 differ** | `sk-census-u1-runtime.py` (sha1 per file) |
| U1 static extraction reach | 1 (`counter-reset-reversed-nested`) | **1, 0 errors** | `sk-differential.mjs`: TWO processes, one per tree. Base = a `git archive` export of HEAD; fix = that export + seam-1 + the lane's files. Each runs `main()`'s static half (extractFixture + counter-style bake) and compares sha1 of `[fixture, refFixture]` (`sk-differential.out.json`) |
| U1 via post-load structure re-extraction | not counted | **0**: none of the 31 `extracted+structure` tests (wave52-ship logs) has an `<li>` or a script-made list | grep of sources |
| U1 runtime reach (runs + ::before) | 2 (target + `display-contents-dynamic-before-after-001__1__3`) | **2**; runs + `before._text` = **1**; runs + after = 0; child-first + before = 0 | `sk-census-u1-runtime.py`, all 1435 docs × 2 runs |
| U2 M reach | 4 abspos `li`, counter-suffix only | **4**. The 17 baked docs (extract logs = frozen set) hold 0 other marker-capable non-root items (li/summary/display list-item); arabic-indic's 35 `li` are ROOTS | `sk-census-u2.py` |
| U2 P reach | 14 roots with padding keys; 6 non-zero in 4 docs; computed = authored | **14 / 6 in 4 docs**. Only counter-suffix's `ol` roots have a UA-padded tag (author `0 3em` overrides it); no other UA-padded tag in the 17 sources | `sk-census-u2.py` + grep of sources |
| bidi-bake bails that U1/U2 could flip | not counted | **0**: none of the 22 wave52-ship bails involves a list or a nested-list mapping | extract-log reasons |
| post-fix shape of the N4/N5 payload | substituted wave52-ship line | **matches**: the fixed extractor emits `Two` `_text "Two"`, `_runs [{text:"Two "},{child:…__0__1__0}]`, `::before _text "2. "`, and 3/2/(11/9/8)/1 | `skeptic/nested-shape.mjs` |

My carrier set equals `expectations.json` `captureCarriers` / `wireCarriers`: web/iOS `{nested, suffix}`; Android adds
`{bidi-lines-001, -002, anchor-center-safe-rtl}`.

### (c) Scores (`cells.mjs`, wave52-ship and wave53-open): every one the lane quotes is right

- nested: web P 0.9506, iOS P 0.9508, Android P 0.9509.
- counter-suffix: web P 0.9818, iOS P 0.9802, Android P 0.9547.
- bidi-lines-002 Android: P 0.9534.
- bidi-lines-001 Android: f 0.8934.
- `anchor-center-safe-rtl`: unscored.
- That is 7 P and 1 f among the 8 scored carrier cells.

### (d) PNGs (`skeptic/look-*.png`; ink columns measured on decoded pixels)

- **nested**: the ref is nested at x97 and reads 3/2/11/9/8/1. All three captures are flat at x57 and read 12/11/10/9/8, with
  no `1. One`. iOS row 2 has no marker. This matches the brief.
- **counter-suffix, RTL rows**: the ref's dot ink is at x133-134 and the digit/letter at x138-145. Web's markers are at x46-58.
  iOS has no marker and its text sits at x103-126 (correct). Android has no marker and its text sits at x151-174 (+48).
- The V1 model numbers put '.' at frame x132.48 and '1' at x136.9. That is consistent with the ref's ink.
- **The LTR rows also differ on the natives**:
  - iOS rows 3-4 swap the Hebrew period (`.` at x47-48, before aleph at x51-58);
  - iOS and Android rows 5-6 draw the CJK marker 4 px left (x29-42 against the ref's x33-46).
- No wave-53 lane touches those rows, so they survive U2 (defect 6).
- `lists-bakes.geometry.py wave53-open wave53-open` prints GEOMETRY WRONG on all six capture rows and OK on both refs, with
  `rows 0-207 identical` on counter-suffix. The probe works.

### (e)/(f) Other checks

- Seam patch: `git apply --check` is clean on HEAD.
- The u2-narrow patch applies clean on the U2 files and reverses byte-exact.
- `git status`: the four seam files are unmodified. `tools/titan/runs/wave53-lock/` is empty.
- Grepping the owned diff for leftover debug "probe" code or scratchpad paths finds only real mechanism words ("probe
  span"). The note's `/tmp/…` paths are commands for the orchestrator, not evidence pointers.

## Defects, ranked (none blocks the gate verdict; fix #1 and #2 before the U2 window and commit)

1. **should-fix: silent fallthrough in hunk M** (`tools/titan/bidi-marker-bake.mjs` `inPageMarkerProbe`, `if (!el) continue;`).
   - When the walked rect does not re-find the `<li>`, the item gets no facts. `planMarker(undefined)` returns `null`, and the
     baked item keeps its old marker with **no `marker-not-baked` stamp**.
   - This contradicts the banner ("anything it cannot honour is stamped … never dropped silently") and the hard rule.
   - Repro `skeptic/silent-skip-repro.mjs` (the real `collectMarkerFacts` + `planBidiBake` on the verbatim geometry):
     `facts {} | li box list-style-type (unchanged) | li stamp null | runs ["foo"]`.
   - Fix: emit `{ error: 'list item not re-found by rect' }` for that key, as the catch path does, so the item is declined
     and stamped.
   - At the gate, the 10-run log line and the geometry probe would still expose it, so the failure would not stay silent
     there.
2. **should-fix: window request 2 cannot run as written** (`_note.md` "ORCHESTRATOR WINDOW REQUESTS" 2 and
   `wire-differential.sh`).
   - The pre-tree recipe (`git worktree add --detach … cdb8a845`, symlink `node_modules` and `tools/wpt`) omits
     `tools/titan/wpt-buckets.json`, which is gitignored (`.gitignore:79`).
   - Without it, `extractFixture` throws for every test. Executed in a HEAD export: `extract-fixture:
     css/css-counter-styles/counter-suffix.html is not in bucket A or B`.
   - The pre side would write nothing, and the script would print `ONLY-IN-POST: <every fixture>`. That wastes a device
     window and blocks §4 step 3.
   - Fix: add `ln -s <main>/tools/titan/wpt-buckets.json <pre>/tools/titan/`.
3. **should-fix: the marker stamps are unpinned on the fixture side** (S-svx2 survives).
   - The honesty contract of hunk M (and the pre-registered "`marker-not-baked` ⇒ U2 must not land" reading of the extract
     log and fixture) rests on `applyBidiBakePlan` merging `lossy` into `_lossyReasons`. No pin asserts it.
   - Likewise, S-svx6 (the `textModelled` computation) and S-svx5 (the `modelMarkerTexts` suffix restore) survive.
     `skeptic/model-texts.mjs` shows the fallback is correct today on counter-suffix: `"1. ","2. ","א. ","ב. "`.
   - Fix: add one `applyBidiBakePlan` assertion on a stamped box, and one `collectMarkerFacts` case with a fake page whose
     snapshot has no text.
4. **should-fix: the step-4 attribution key for a PSEUDO is unpinned, and the setCounter comment is false inside pseudo bags**
   (`tools/titan/counter-bake.mjs`).
   - SN3C survives all 114 pins. P1 changes from 12,10,9 to 11,10,9.
   - `setCounter`'s comment says "the bake steps before it sets", but `pseudoSet` applies a bag's `counter-set` BEFORE its
     `counter-increment` (pre-existing order).
   - So a ::before that both steps and sets a reversed counter gets the PREVIOUS box's step as its step-4 term. Repro P2
     prints `11,8,7`; the §4.4.2 arithmetic gives initial 15 ⇒ 14,…. The comparison is spec arithmetic only; Chromium was not
     run.
   - 0 corpus carriers: only `counter-list-item` and the target hold reversed setters, and both are element setters.
   - Fix: pin P1, and name the P2 corner in the banner (or reorder `pseudoSet` to increment-then-set, a separate change).
5. **should-fix (latent, 0 carriers): marker glyph tops add the probe span's half-leading** (`planMarker`:
   `y: first.run.y + q.y`).
   - The span inherits **body**'s line-height, so `q.y` is that line's half-leading. `first.run.y` is already a content-area
     top.
   - `skeptic/halfleading-repro.mjs`: q.y 0 → tops 2px/2px (= text); q.y 14 (a body `line-height: 48px`) → **16px**.
   - Counter-suffix is safe: body inherits the pinned 1.25, which gives 20 px, equal to the 20 px content area. The probe's
     "top ±1" check would also catch it there.
   - Fix: `q.y − (first glyph's q.y)`.
6. **should-fix (honesty): counter-suffix iOS and Android stay partly wrong after U2.**
   - The RTL half becomes right, but iOS rows 3-4 and iOS + Android rows 5-6 keep wrong ink (measured above).
   - `expectations.json` labels both cells `degenerate->faithful`, and PLAN's lane table counts them among "6 cells
     picture-correct".
   - The lane note neither repeats nor corrects this.
   - Label them "RTL rows picture-correct; cell stays DEGENERATE on rows 3-6 (iOS) / 5-6 (Android)" in the note and the PR.
     Never call them faithful.
7. **should-fix (verification gap): the post-fix converter hop of `counter-reset-reversed-nested` is neither run nor
   requested.**
   - N4/N5 pin a wave52-ship line with substituted strings. I confirmed the fixture-level shape matches.
   - Add the nested document to window request 3, the converter hop.
8. **nit: Compose's ::before fold reuses `PseudoTextBridge.inlineRun`, whose refusal breadcrumbs are hard-wired to `::after`**
   (`PseudoText::after/<key>`, `::after declaration …`).
   - A ::before refusal under `meta.runs` is therefore logged as an ::after one. The Swift bridge uses `\(role)`.
   - 0 corpus hits: the control's bucket refuses nothing.
9. **nit: `bidi-marker-bake.mjs` reaches 200 lines by packing.**
   - Five lines run 124-165 characters. There are code stretches of 17 and 20 lines without a comment (`inPageMarkerProbe`
     l.92-108, l.110-129) against the "every line commented" rule.
   - New logic also went into oversized or now-over-target files: `bidi-bake.mjs` 1172 → 1259 (`paddingIsSpent` plus the
     pre-pass, disclosed in the note), `PseudoTextFold.swift` 228 → 291, `.kt` 201 → 269.
10. **nit: `paddingIsSpent` has guards the corpus never needs** (0 carriers each):
    - `mask-clip`/`mask-origin` and a `clip-path` `content-box` reference box;
    - a root at the walker's depth cap whose children stay in flow;
    - a list-item ROOT whose outside marker hangs off the padding edge.
11. **nit (plan, for the orchestrator): `expectations.json` contradicts itself on counter-suffix iOS geometry.**
    - `predictions[…ios].gating: true`, but `geometryProbe.geometryGating` omits it.
    - PLAN §2 says "0.95 · report".

## What I could NOT check (honest)

- Everything Chromium does: the DOMSnapshot `::marker` node shape, `getComputedStyle(li,'::marker')`, the probe span's
  glyph boxes, the gate-flag (`--post-load --bidi-bake --vt-bake`) wire differential and the CDP probe. I ran no Chromium, by
  rule. Defect 1 and defect 5 are shown on the pure functions.
- Device captures: whether iOS/Compose paint `Two` through the run plan or the leading label; `font-variant-numeric` on
  native runs; every predicted score. All 30 cells' predictions remain the lane's reasoning and replays.
- The converter hop (`:converter:run`) for either target document.
- The P2 "spec" numbers are hand arithmetic on css-lists-3 §4.4.2. I did not run Chromium against them.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (Gradle ran in its
apps/android-harness for the N5 mutations. xcodebuild used a private `-derivedDataPath` outside the repo for N4. The node-only
export copies of `tools/titan` + `tools/visual` used for the differential and V3c were outside the repo and ran no Gradle.)

STATUS: COMPLETE
