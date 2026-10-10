# wave54 L1 · rtl-marker-bake — lane note

Contract: `tools/titan/results/wave54-plan/PLAN.md` §2 "### L1 · rtl-marker-bake" (brief `rtl-marker-bake.md`).
Base: HEAD **db6e8aa0** (= dev 7cce3b22 + the committed wave-54 plan). Owned files at start = HEAD bytes
(`tools/titan/bidi-bake.mjs` sha256 864cf317…, `tools/titan/bidi-bake.test.mjs` e235dd20…; `tools/titan/bidi-marker-bake.mjs`
absent). No resume state existed (fresh lane). No seam file was edited, no seam patch is needed, nothing was committed.

Final owned bytes (shared tree):

| file | sha256 | lines (HEAD) |
|---|---|---|
| `tools/titan/bidi-bake.mjs` | `98ad650e42193e90c558e901e0a859ccd90c07c0b4e69774ac3551c7fdffc6a3` | 1273 (1172) |
| `tools/titan/bidi-marker-bake.mjs` (new) | `4ded3874bba89a8868454cd0d1da11b768ec3e7c3bda805bce2baa1d2a56fbea` | 199 |
| `tools/titan/bidi-bake.test.mjs` | `d85f66366524ae0ce1a86a155a40e0fc6d495fa08f070a935f672f014a2a3d47` | 1247 (758) |

## 1. What changed and why

**Looked at first** (ref | web | iOS | Android, wave53-final and wave53-probe; frozen refs
`tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`):
- counter-suffix rows 9-12: the ref reads `foo .1 / bar .2 / foo .א / bar .ב` (markers RIGHT). web wave53-final hangs the RTL
  markers LEFT (`1.  foo` …); iOS final paints no RTL marker; Android final paints no marker AND the RTL text sits ~48 px
  right (x152). Android at wave53-probe (the reverted li-owned shape): `foo` (no marker) / `bar ·` / `foo ·` / `bar .א` /
  a lone `ב.` below — exactly the brief §2 (+20 px per run, third run never painted). web/iOS at the probe read like the ref.
- bidi-lines-001/-002 and anchor-center-safe-rtl: Android final draws the baked text +4 px right of the ref (`français` x33 vs
  x29); Android at wave53-probe (P bytes) puts the line starts on the ref's x. bidi-lines-002 keeps its top and bottom orange
  `!` on the LEFT on web and Android in every run (the ref has them right): a bake-measurement defect, not P — that cell
  stays DEGENERATE. anchor-center-safe-rtl is wrong on every platform before and after (no green `Anchor`, boxes misplaced;
  unscored).

**Unit P — `tools/titan/bidi-bake.mjs`: a bake root's SPENT padding is zeroed.** Code byte-identical to wave-53 hunk P
(`git show b0edb788^:tools/titan/bidi-bake.mjs`, verified by a comment-stripped diff: the only remaining code differences are
the M′ lines). Five hunks (`unit-P.edits.py`):
- `inPageBidiWalker` records `padding` (resolved top/right/bottom/left px), `backgroundClip`, `backgroundOrigin`, `overflow`;
- `export function paddingIsSpent(el)` — non-zero resolved padding, not under a `content-box` clip/origin, not under a
  non-`visible` overflow (CSS 2.1 §10.1 item 4 / css-position-3 §3.1: abspos insets resolve against the PADDING box, and the
  root is `border-box` at its used size, so zeroing moves nothing on a conforming runtime; Android anchors at the CONTENT box,
  `PositionedParentFlowSlot.kt:74-76`, so it moves there by the padding);
- `rootProperties(rect, position, el = null)` emits `padding: '0'` when spent;
- `planBidiBake` passes the walk record (`rootProperties(e.rect, e.position, e)`);
- `applyBidiBakePlan` deletes every `padding-*` key first, then `Object.assign` overwrites an authored shorthand in place.

**Unit M′ — RTL `::marker` runs OWNED BY THE BAKE ROOT.**
- `tools/titan/bidi-marker-bake.mjs` restored from `b0edb788^` (fix-pass state), CODE byte-identical (comment-stripped diff
  empty); only the banner and `planMarker`'s doc comment are re-trued for wave 54 (`unit-Mprime.module.py` regenerates it
  from git). 199 lines.
- `tools/titan/bidi-bake.mjs` call sites (`unit-Mprime.edits.py`): banner clause, import, the markers map in `planBidiBake`,
  `boxProps`/`lossy` on the item box, the marker runs pushed after the item's text runs, the `_lossyReasons` merge in
  `applyBidiBakePlan`, `collectMarkerFacts` after the mapping check in `bidiBakeFixture`. Against wave 53 the code differs at
  exactly the two registered lines (PLAN §2 L1):
  - l.925 → `const rootPath = rootKeys.find((rp) => isDescendantPath(rp, e.path));`
    `planMarker(walk.markers?.[k], originOf.get(rootPath.join('.')), runsByPath.get(k)?.[0])` — measured from the ENCLOSING
    ROOT's padding-box origin (roots never nest: `selectBakeRoots` drops nested roots);
  - l.974 → `plan.runs.push({ ownerPath: markers.get(key).rootPath, … })` — owned by the root, after the root's own runs, so
    every text run keeps its pre-bake child id and each `<li>` keeps ONE child.
- Why it fixes Android without runtime code (read, not run): `ComponentRenderer.kt` :3475-3521 — a RELATIVE parent is
  `isPositionedContainer`; every ABSOLUTE child, whatever its tag, goes through `RenderAbsoluteChild` (no Column cursor).
  That is the path bidi-lines-002's root-owned `Hello`/`سلام` runs take, device-proven at wave53-probe.
- Static audit of "M′ adds non-`<li>` children to an `<ol>`" (brief risk 3), read only: Compose paints a list marker only for
  `child._tag == "li"` (`ComponentRenderer.kt` :3521/:3570 `liKeepsItsMarker`) or an own `display: list-item`
  (`ListItemMarkerGate.rendersOwnLeadingMarker` needs `isListItemDisplay`); SwiftUI's children loop marks only
  `meta.sourceTag == "li"` or a baked `meta.markerText` (`ComponentRenderer.swift` :3836-3866); the counter-style bake stamps
  `_markerText` only on `_tag: li` children (`counter-style-bake.mjs` :346). The runs carry no `_tag`, no display, no
  `_markerText` (pin V4 asserts the last on the real extraction). Blink generates `::marker` only for `display: list-item`.
  Device-unmeasured until stage 1.

## 2. REVERT UNITS (one commit each; `land-units.sh` source)

The two units split hunks of the SAME two files, so they are landed by REPLAYING two patches on HEAD's bytes (the wave-53 L3
recipe), proven byte-exact by `make-units.sh` (`make-units.out.txt`: exit 0 — unit-P.patch applies on HEAD, the P state equals
`state-P/*.txt` sha256 f266b46b… / fac220e9…, has NO `bidi-marker-bake.mjs` and its focused suites are green ALONE
(115 pass, 0 fail, 6 skipped in a git-archive export with no `tools/wpt`/`fixtures/wpt`); unit-Mprime.patch applies on the P
state and reproduces the three shared-tree files sha256-exact; suites 127 pass, 0 fail, 7 skipped there).

### Unit P (lands first, after L7 U1; PLAN §4 step 3.2)
Commit holds exactly:
- `tools/titan/bidi-bake.mjs` and `tools/titan/bidi-bake.test.mjs` **at their unit-P bytes** (`unit-P.patch` applied on HEAD)
- `tools/titan/results/wave54-rtl-marker-bake/` (this lane dir: evidence, scripts, both unit patches)

### Unit M′ (lands second, never without P; `revertOrder` = [Mprime, P])
Commit holds exactly:
- `tools/titan/bidi-marker-bake.mjs` (new), `tools/titan/bidi-bake.mjs`, `tools/titan/bidi-bake.test.mjs` (`unit-Mprime.patch`
  applied on the unit-P state = the shared-tree bytes)

Landing snippet (for `land-units.sh`, same `apply`/`commit` helpers as wave 53):
```bash
R=tools/titan/results; L1=$R/wave54-rtl-marker-bake
SAVE="$(mktemp -d)"; for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do mkdir -p "$SAVE/$(dirname $p)"; cp "$p" "$SAVE/$p"; done
git checkout -q HEAD -- tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs; rm -f tools/titan/bidi-marker-bake.mjs
apply "$L1/unit-P.patch"
commit "wave54 L1 P: a bidi-bake root's spent padding is zeroed (paddingIsSpent; CSS 2.1 §10.1 item 4)" "Revert unit L1-P ($L1/_note.md). Reverting moves bidi-lines-001/-002 and anchor-center-safe-rtl android back by the padding (and counter-suffix android's RTL text back to x152). Revert M′ first." \
  tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs "$L1"
apply "$L1/unit-Mprime.patch"
commit "wave54 L1 M′: RTL list markers baked as runs owned by the bake ROOT (bidi-marker-bake.mjs restored; css-lists-3 outside marker on the inline-start side)" "Revert unit L1-Mprime ($L1/_note.md). Reverting undoes the counter-suffix RTL markers on all three platforms and the +6 id shadow on the 15 later css-counter-styles documents." \
  tools/titan/bidi-marker-bake.mjs tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs
for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do cmp -s "$p" "$SAVE/$p" || { echo "L1 replay differs: $p" >&2; exit 1; }; done; rm -rf "$SAVE"
```
If either owned file changes after this note (a fix lane), re-cut both patches and re-run `make-units.sh` before landing.

## 3. Census (my own; method differs from the brief's)

`census.mjs` (read-only) — reach = every test whose `<run>/sections/*/manifest.json` says `bidiBaked: true` (P and M′ edit only a
baked test's plan; the only new bail they could add is the run budget). Per baked test it reads the LIVE extracted fixture
(`fixtures/wpt/…`, written by wave54-open — mtimes 2026-10-08 13:18-14:28Z, sha1 recorded per file) for roots
(`_lossyReasons ∋ baked-bidi-visual-order`), tags, padding/guard keys, UA list padding, list items in scope with their
effective list-style-type (bullets included: `planMarker` would bake them), and cross-reads each finding in the wave53-final
per-test IR (Padding* data; `meta.markerText` + Position). `crosscheck.py` compares with `expectations.json`
lanes["L1-rtl-marker-bake"]. Outputs: `census.wave53-final.out.txt` (+ `.json`), `census.wave54-open.out.txt` (identical),
`crosscheck.wave53-final.out.txt` (exit 0).

- bidiBaked tests: **17** (40 scored cells: see the per-doc lines).
- **P carriers: 6 roots in 4 docs** — counter-suffix `__0__4`/`__0__5` (`0 3em`), bidi-lines-001 `__1` and -002 `__1`
  (`0 0.5ch`), anchor-center-safe-rtl `__1`/`__3` (`10px`; IR `__2`/`__4`). Roots with a UA list padding and no authored one:
  **0**. Guarded roots: attachment-local-positioning-3/-4 (`overflow: hidden`, no padding). Zero-padding roots: 8
  (dir-style-02a ×6, dir-selector-change-003/-004; IR Padding* all `{px:0}`) — untouched by construction (pin V3b).
- **M′ carriers: 4 list items, all counter-suffix** (`__0__4__0/1` decimal, `__0__5__0/1` hebrew; IR markerText `1.` `2.`
  `א.` `ב.`, Position ABSOLUTE). No bullet item, no list-item in any other bake root. The arabic-indic `<li>` are ROOTS.
- Run budget: max 24 runs (css3-counter-styles-102), unchanged with markers (limit 300) — no new bail.
- Id shadow: counter-suffix is entry 33 of 48 in css-counter-styles; the converter's id counter continues across the section,
  so the **15** later (cssom) documents renumber by +6 under M′ (none under P). Verified on wave53-probe's real IR:
  `w2-check.wave53-probe.css-counter-styles.out.txt` → identical 32 · renumbered 15 · content-changed 1.
- Cross-check vs the plan: capture carriers web/iOS {counter-suffix}, Android {counter-suffix, bidi-lines-001, -002,
  anchor-center-safe-rtl}; wire carriers the same 4; per-unit carriers; shadow 15 — **all equal**. Must-not-move re-derived
  (every scored cell of a baked doc not carried on that platform + the 15 shadowed docs ×3): **80 cells, 64 P today = the
  plan's 80 exactly** (none only-mine, none only-plan).
- Carrier cells today (wave53-final = wave54-open): counter-suffix web P 0.9818 · ios P 0.9802 · android P 0.9547;
  bidi-lines-001 android f 0.8934; bidi-lines-002 android P 0.9534; anchor-center-safe-rtl android unscored → **5 scored,
  4 passing** (3 of the 4 passes are DEGENERATE: counter-suffix ios/android, bidi-lines-002 android; counter-suffix web is a
  wrong-side picture).
- P's wire, on REAL converter output: wave53-probe ran these exact P bytes, and `w2-check.mjs` over its sections
  (`w2-check.wave53-probe.{css-text,css-anchor-position,selectors}.out.txt`, all ALL PASS) shows the only changes are the six
  roots' Padding* → `{px:0}`, no component added, and the three zero-padding selectors documents byte-identical.

## 4. Pins and executed mutations

Suites (focused, the only ones run): `nice -n 19 node --test tools/titan/bidi-bake.test.mjs tools/titan/counter-style-bake.test.mjs
tools/titan/counter-bake.test.mjs` → **134 tests, 134 pass, 0 fail, 0 skipped** (bidi-bake alone 69/69; V4 and the proving
set RUN here because `tools/wpt` and `fixtures/wpt` exist on this host). Also `tools/titan/wpt-white-canvas.test.mjs` (it
source-scans bidi-bake.mjs) → 21/21.

Pins (in `tools/titan/bidi-bake.test.mjs`):
- §9 unit P — read VERBATIM frozen bake outputs (`results/wave53-plan/bidi-baked-fixtures/`, sha1 asserted against
  PROVENANCE): **V3** verbatim `counter-suffix__0__4` → `padding: "0"` alone, same key order, every other value unchanged; plus
  longhand spelling; **V3 content-box guard**; **V3 overflow guard**; **V3** verbatim bidi-lines-002 root with its 5 hidden
  `<br>`; **V3b** verbatim zero-padding roots (dir-style-02a ×6, dir-selector-change-003/-004) deep-equal AND in order;
  **P wiring** (walker records the four fields).
- §10 unit M′ — wave 53's V1, V2, V4, V5, VF, VF1-3 ported to root ownership, plus **V6** (CDP-measured geometry from
  `wave53-lists-bakes/marker-probe.out.txt`: every marker run owned by its enclosing root at root-relative left 116.3 / 120.59
  ±0.05, top 2 / 26, widths 4.3/10.38/14.62/13.31; applied to the VERBATIM frozen counter-suffix fixture: +6 components, root
  children `[li, li, runs…]`, each item ONE child) and **V7** (no item box owns more runs than its text runs, CDP / model /
  analytic facts).

Every mutation executed by `mutate.py` (red → in-memory byte-exact restore, sha256 verified → green), full log
`mutations.log`, JSON `mutations-*.result.json`. **30/30 OK** (P×7 at the P state + P×7 at final + M-1..15 + M-4b; the four result JSONs, every `verdict` OK; corrected at the wave54-S1 fix pass, nit N5 — it said 29/29). (sha256 prefixes: before → mutated → restored.)

| mutation | state | file sha256 | red run | red tests | restored |
|---|---|---|---|---|---|
| P-1 remove the `padding: 0` line | P state | bidi-bake.mjs f266b46bb9ec → 05f9992f712c → f266b46bb9ec | fail 2 | V3 ×2 | 56/56 |
| P-2 drop the content-box guard | P state | f266b46bb9ec → e8c00dacf012 → f266b46bb9ec | fail 1 | V3 content-box | 56/56 |
| P-3 drop the NON-zero guard | P state | f266b46bb9ec → 068ab7c3bd9c → f266b46bb9ec | fail 1 | V3b | 56/56 |
| P-4 drop the overflow guard | P state | f266b46bb9ec → a1b726a6588e → f266b46bb9ec | fail 1 | V3 overflow | 56/56 |
| P-5 drop the padding-* delete loop | P state | f266b46bb9ec → 9427a70d5454 → f266b46bb9ec | fail 1 | V3 counter-suffix | 56/56 |
| P-6 planBidiBake drops the walk record | P state | f266b46bb9ec → 141d40caf20d → f266b46bb9ec | fail 2 | V3 ×2 | 56/56 |
| P-7 walker stops recording padding | P state | f266b46bb9ec → 35806d734bde → f266b46bb9ec | fail 1 | P wiring | 56/56 |
| P-1 … P-7 again | final | bidi-bake.mjs 98ad650e4219 → (fe4cbe36, 474464c4, 98324f59, f5f4ecbd, b13b0e31, 99081590, ff75ad86) → 98ad650e4219 | fail 3/1/1/1/1/3/1 | same pins (+V1 on P-1/P-6) | 69/69 |
| M-1 drop `list-style-type: none` | final | bidi-marker-bake.mjs 4ded3874bba8 → 6bbbefa88327 → 4ded3874bba8 | fail 3 | V1, V6, V4 | 69/69 |
| M-2 analytic RTL edge hangs LEFT | final | 4ded3874bba8 → 8e4892f56ddf → 4ded3874bba8 | fail 2 | V1, V5 | 69/69 |
| M-3 no per-grapheme split | final | 4ded3874bba8 → 55b12ed75f5b → 4ded3874bba8 | fail 4 | V1, V6, V4, VF3 | 69/69 |
| M-4 a ROOT item is planned a marker (crashes: TypeError on the root's own lookup) | final | bidi-bake.mjs 98ad650e4219 → c7d69cdece6f → 98ad650e4219 | fail 13 | incl. V2 | 69/69 |
| M-4b same, without the crash (root owns/measures itself) | final | 98ad650e4219 → aa4e357dd9f4 → 98ad650e4219 | fail 1 | V2 | 69/69 |
| M-5 probe self-check deleted | final | bidi-marker-bake.mjs → 5adeec1df544 → restored | fail 1 | V5 | 69/69 |
| M-6 mismatch → whole-test bail | final | bidi-bake.mjs → ea6cb4cb40fc → restored | fail 1 | V5 | 69/69 |
| M-7 collectMarkerFacts rethrows | final | bidi-marker-bake.mjs → cd7e7c570299 → restored | fail 1 | VF | 69/69 |
| M-8 (FX1) not-re-found item skipped | final | bidi-marker-bake.mjs → d7719302abe0 → restored | fail 1 | VF1 | 69/69 |
| M-9 (FX2) error-fact filter dropped | final | bidi-marker-bake.mjs → aca20c7c8da4 → restored | fail 1 | VF1 | 69/69 |
| M-10 (FX3) box `_lossyReasons` merge deleted | final | bidi-bake.mjs → 013d6e59effb → restored | fail 1 | VF2 | 69/69 |
| M-11 (FX4) `- q0` dropped | final | bidi-marker-bake.mjs → 8c6ef4281866 → restored | fail 1 | VF3 | 69/69 |
| **M-12 owner = li (the wave-53 shape; = "push the runs to the box")** | final | bidi-bake.mjs → a9aed43aa607 → restored | fail 4 | **V6, V7**, V1, V4 | 69/69 |
| **M-13 origin = li's padding box** | final | bidi-bake.mjs → e73b4d960ef8 → restored | fail 2 | **V6**, V1 | 69/69 |
| **M-14 runs ALSO pushed to the item box** | final | bidi-bake.mjs → cd16f690fbf8 → restored | fail 4 | **V7**, V6, V1, V4 | 69/69 |
| M-15 marker facts read before the mapping check | final | bidi-bake.mjs → 7d741fcbaff7 → restored | fail 1 | M′ wiring | 69/69 |

Instrument self-tests (the window scripts are proven able to pass AND fail before any window runs):
- `marker-probe.selftest.mjs` (`marker-probe.selftest.out.txt`): the [W1] half-A checks, offline, on the real static
  extraction baked in-process with the CDP-measured facts: as planned 0 FAIL; wave-53 li-owned shape 6 FAIL; M′ without P 2 FAIL.
- `w2-check.mjs`: on wave53-probe's real css-counter-styles IR (li-owned) → exactly the 10 ownership FAILs
  (`w2-check.selftest-wave53-probe.out.txt`); on a document synthesised from that real IR with the runs re-parented to the
  roots → ALL PASS (`w2-check.selftest.mjs` / `.out.txt`); on wave53-final selectors → 48 identical, ALL PASS
  (`w2-check.wave53-final.selectors.out.txt`).

Geometry: `python3 tools/titan/results/wave54-plan/geometry-gate.py wave53-final --base wave53-open --lanes L1 --self-test` →
**HOLDS, exit 0** (gating 7 FAIL · control 7 PASS · report 1 FAIL; `geometry-gate.wave53-final.selftest.out.txt`; re-run after
the orchestrator's in-flight plan fix pass, which only added `requires` to the L1 predictions — same verdict).

## 5. Predictions (from = wave53-final = wave54-open; unchanged from PLAN §2 L1 / expectations.json)

| cell | → predicted | confidence | gate floor · geometry | unit |
|---|---|---|---|---|
| bidi-lines-001 android | f 0.8934 → **P ≈0.9629** | HIGH (these exact P bytes were device-measured at wave53-probe; the wire is re-proven identical in §3) | 0.955 · `[P]` | P |
| bidi-lines-002 android | P 0.9534 → P ≈0.9818, **stays DEGENERATE** (orange `!` left) | HIGH | 0.975 · `[P]` | P |
| counter-suffix android | P 0.9547 → ≈0.9815 (P, replay) → ≈0.989 (M′, replay); RTL rows picture-correct ONLY if `[M]` android prints OK; **stays DEGENERATE on rows 5-6** | MED-HIGH (P floor) / MED (M′; root-owned runs never executed on a device) | 0.970 · `[M]` | P, M′ |
| counter-suffix web | P 0.9818 → **P 1** (faithful) | HIGH (Blink: 48 + 68.3 = 116.3 either owner) | 0.999 · `[M]` | M′ |
| counter-suffix ios | P 0.9802 → P ≈0.987, **stays DEGENERATE on rows 3-6** | MED-HIGH | 0.985 · `[M]` | M′ |
| anchor-center-safe-rtl android | moves (unscored); wrong before and after | — | — | P |

Pre-registered loss (never shipped): M′ without P → counter-suffix android f ≈0.947. Never label a native "picture-correct"
from a replay: only the stage-1 capture + `[M]` earns it.

## 6. Must not move

The 80 scored cells of `watchlist.txt`'s L1 block (= my re-derivation, §3): every platform of the 13 other bidi-baked documents
(their bake output is unchanged: no non-zero padding root, no non-root list item; `collectMarkerFacts` does run on the
arabic-indic walks but `planMarker` skips roots), bidi-lines-001/-002 web + iOS, the 15 `css-counter-styles/cssom/*` ×3
(id shadow only), plus counter-suffix rows y0-207 on all three (`rows 0-207 identical` in both probes); unscored but
controlled: attachment-local-positioning-3/-4 ×3, anchor-center-safe-rtl web/iOS. Wire: the per-test IR of dir-style-02a,
dir-selector-change-003/-004 byte-identical (pin V3b; `w2-check` ALL PASS on wave53-probe selectors).

## 7. Hand-offs

- **Seam patches: none** (`bidiBakeFixture(fixture, testRel)` keeps its signature and its call site in `extract-fixture.mjs`).
  Rule 2b holds trivially: no owned file imports a seam-created symbol.
- **`hunk-for-orchestrator-1.patch`** — the docs-pass comment re-true of
  `runtimes/swiftui/…/StyleEngine/lists/ListMarkerOutsideHang.swift` :33-41 (PLAN §9 D18), wave-53's text re-pointed at root
  ownership. Header: base db6e8aa0, file sha256 at base b50d9bd1…, anchor; `git apply --check` clean on the shared tree. Land
  it ONLY if M′ survives stage 1 (comment-only; no cell).
- BACKLOG re-true for the orchestrator (brief §11 item 1): "one row LATE" → "one RUN height (+20 px); the third run never
  painted; cause `CanvasRootHoist.LocalActive` false → `PositionedParentFlowSlot.mount` not applied in the abspos-parent
  Column loop" — and the runtime defect itself stays queued (10 host-inactive stack-shape parents in 6 documents).

## ORCHESTRATOR WINDOW REQUESTS

All start Chromium (and W2 also Gradle): run on a device-idle host, on the tree that holds unit P + M′ (shared tree before
landing, or the integrated tree), BEFORE stage 1 reads M′. JDK 21 for W2. Neither writes into the repo except as the gate
itself does (`fixtures/wpt/<section>/<stem>.json` of the tests extracted; W2's outputs go to gitignored `tools/titan/runs/`).

- **[W1] CDP marker probe** — `node tools/titan/results/wave54-rtl-marker-bake/marker-probe.mjs`
  - Expect: last line `MARKER PROBE: ALL PASS` (exit 0); `outcome {"status":"baked",…,"roots":2,"runs":10,…}`; both RTL roots
    `padding: 0` alone; root children `[li, li, …]` with runs `[".","1",".","2"]` / `["א.","ב."]`; each RTL `<li>`
    `list-style-type none`, NO `marker-*` stamp, ONE child; every run frame x ⊂ [131,148] and top = its item's top + 2 (±1);
    DOMSnapshot strings `'1. ','2. ','א. ','ב. '`; row-1 box left 112 ± 0.5.
  - Decision: any FAIL on ownership / one-child / x / top, or a `marker-not-baked` / `marker-*-modelled` stamp → **M′ does not
    land** (revert M′; P stays on its own rows). A `padding: 0` FAIL → P is broken: stop, send the printed root props back.
    A FAIL only in half B (snapshot strings) with half A passing → send the `snapshot marker` lines back; CDP shape drift.
- **[W2] converter hop (gate flags) for counter-suffix** —
  `bash tools/titan/results/wave54-rtl-marker-bake/w2-converter-hop.sh css-counter-styles css/css-counter-styles/counter-suffix.html`
  - Expect: extract line `[bidi-bake: baked — 2 roots, 10 runs] [counter-bake: baked — 6 markers, 2 declined]`;
    `converter: no warning lines`; `W2 CHECK: ALL PASS` — 23 → 29 components, roots' only change Padding* → `{px:0}`, the 4
    items `+ ListStyleType none / − meta.markerText` and ONE child each, 6 added components with `slot.parent` = the two
    `<ol>` roots at Left 116.3 / 120.59, Top 2 / 26, `FontVariantNumeric TABULAR_NUMS`, Direction RTL on the Hebrew runs.
  - Decision: any FAIL → M′ does not land (or P, if the FAIL is a root Padding* line); report the failing lines to the lane.
- **[W2b] (optional, recommended — pre-reads R4b for L1 before stage 1)** the same script over whole sections:
  `bash …/w2-converter-hop.sh css-counter-styles` (expect `identical 32 · renumbered 15 · content-changed 1` + ALL PASS),
  `bash …/w2-converter-hop.sh css-text` (expect content-changed exactly bidi-lines-001/-002, each only Padding* → `{px:0}`),
  `bash …/w2-converter-hop.sh selectors` (expect 48 identical, the three zero-padding docs byte-identical),
  `bash …/w2-converter-hop.sh css-anchor-position` (expect content-changed exactly anchor-center-safe-rtl, Padding* only).
  NOTE: on the integrated tree other lanes' wire changes (L3's converter/extractor units) will also show as content-changed
  in css-text; read the L1 lines only, or run before L3 lands. Decision: an L1-attributable change outside these = R4b leak.
- [W3] is stage 1 (`wave54-pre`, PLAN §6/§8) — not requested separately. Lane's stage-1 reading: `[P] … android → GEOMETRY OK`
  ×2; `[M] counter-suffix {web,ios,android} … | rows 0-207 identical to wave54-open → GEOMETRY OK`; web ≥ 0.999, iOS ≥ 0.985,
  android ≥ 0.970; then OPEN the three counter-suffix PNGs (rows 9-12 `foo .1 / bar .2 / foo .א / bar .ב`, nothing below y300).

## 8. What I could NOT verify (honest list)

- Nothing ran on a device, in Chromium or through the converter. The root-owned marker runs have **never been rendered** on
  any runtime: the Compose `RenderAbsoluteChild` path, the SwiftUI overlay and Blink are argued from code reading and from
  bidi-lines-002's root-owned runs at wave53-probe, not measured. Stage 1 is the evidence.
- The CDP facts (`marker-probe.out.txt`) are wave-53's; [W1] re-proves them on today's host/Chromium.
- V6's per-glyph span offsets are RECONSTRUCTED to reproduce the CDP-measured runs (the raw glyph rects were not logged at wave
  53); the box x/width and the run left/width they reproduce are measured. Glyph tops are 0 by construction (VF3 shows they
  cancel).
- The converter's handling of the new wire shape (6 root-owned runs) is checked only on a SYNTHESISED document ([W2] is the
  real check); the P wire IS checked on real converter output (wave53-probe ran the identical P bytes).
- iOS/Android `FontVariantNumeric TABULAR_NUMS` on the digit runs (brief risk 4, LOW) and iOS's Hebrew-period order on rows 3-4
  (stays DEGENERATE) — unchanged, out of family.
- `web-harness` separators between the `<ol>`'s abspos children (L6 RS touches `renderChildSeparator`): whitespace between
  out-of-flow siblings generates no line box; not measured.

## 9. Size disclosures (BACKLOG 0(d), PLAN §9 D8)

- `tools/titan/bidi-bake.mjs` 1172 → 1273 (+105 / −4 by `git diff --numstat 1cde1f48 d64d4c6e`, net +101 — corrected at the wave54-S1 fix pass, nit N5; wave 53 was +95 / −4 → 1259): call sites + `paddingIsSpent` only; the
  extra lines are comments. Every new function except `paddingIsSpent` lives in `bidi-marker-bake.mjs` (199 lines).
- `tools/titan/bidi-bake.test.mjs` 758 → 1247 (+489): the pins must live in an owned file; split by unit via the patches.
- `probe`/`Probe` grep of the owned diff: only domain words (the in-page marker probe span, `MARKER_PROBE_EPS`,
  `marker-probe-mismatch`, the `wave53-probe` run name, the `rtlProbe` fixture helper) — no debug leftover, no `console.`.

## Files in this lane dir

`_note.md` · `edit.py` · `unit-P.edits.py` · `unit-Mprime.edits.py` · `unit-Mprime.module.py` · `unit-P.patch` ·
`unit-Mprime.patch` · `state-P/{bidi-bake.mjs,bidi-bake.test.mjs}.txt` · `make-units.sh` + `.out.txt` · `mutate.py` ·
`mutations-{P,P-final,Mprime,Mprime-4b}.json` + `.result.json` · `mutations.log` · `census.mjs` + `census.{wave53-final,
wave54-open}.{json,out.txt}` · `crosscheck.py` + `.wave53-final.out.txt` · `geometry-gate.wave53-final.selftest.out.txt` ·
`marker-probe.mjs` · `marker-probe-checks.mjs` · `marker-probe.selftest.mjs` + `.out.txt` · `w2-converter-hop.sh` ·
`w2-check.mjs` · `w2-check.selftest.mjs` + `.out.txt` · `w2-check.selftest-wave53-probe.out.txt` ·
`w2-check.wave53-probe.{css-text,css-anchor-position,css-counter-styles,selectors}.out.txt` · `w2-check.wave53-final.selectors.out.txt` ·
`hunk-for-orchestrator-1.patch`.
(`w2-check.wave53-probe.css-counter-styles.out.txt` is EXPECTED to fail 10: that run holds the reverted li-owned shape.)

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (no Gradle or xcodebuild run;
the two `git archive` exports `make-units.sh` builds are throwaway, not worktrees)

STATUS: COMPLETE

## S1 fix pass (wave54-S1 should-fix S6, the L1 skeptic's should-fix 1): the marker bake's PAINT-EFFECT decline

**The defect** (S1 `_note.md` S6). Unit M′ moves an item's marker runs to the enclosing bake ROOT. A marker is the
item's (css-lists-3 §3.1; css-pseudo-4 §4, so it inherits from the item). So under the root the runs escaped the item's
and every between-box's opacity / transform / filter / clip-path / overflow clip, and the inherited visibility /
text-shadow that `runProperties` does not restate. Nothing declined, stamped or TODO'd it.

**The fix** (no commit; the orchestrator commits after the verifier):
- **NEW `tools/titan/bidi-marker-paint.mjs` (159 lines).**
  - `MARKER_PAINT_INITIAL`: the 8 read keys (`opacity transform filter clipPath overflowX overflowY visibility textShadow`)
    mapped to their initial computed spellings.
  - In-page `inPageMarkerPaintChain`: re-finds the item by tag + rect, the same way the probe does. It reads the
    `::marker` pseudo first, then the item and each DOM ancestor up to (not including) `<body>`.
  - `readMarkerPaintChains(page, facts, items)`: attaches `paintChain` to every MEASURED fact and never throws. A fault
    gives `{ error }`.
  - Pure `markerPaintLoss(f, elements, itemPath, rootPath)` returns the first loss as a reason string, or null. It reads
    the pseudo, the item and each box strictly BETWEEN the item and the root, never the root itself (the root's effects
    still reach root-owned runs). Each entry's tag is checked against the walk record at that path prefix. An unmeasured,
    failed, short or misaligned chain declines too.
  - The banner holds the why and the spec anchors. It ends with a TODO: `mask-image` / `mix-blend-mode` are the same
    class but are NOT read. Their computed spellings could not be confirmed without Chromium, and a key Chromium does not
    expose would decline every marker.
- **`bidi-marker-bake.mjs` (199 → 200).** `collectMarkerFacts` returns `await readMarkerPaintChains(page, facts, items)`.
  That is the CDP side, so the reads come right after the probe. The banner's decline list names the paint effect.
- **`bidi-bake.mjs` (1273 → 1283, +13 / −3, call site + banner only).** In `planBidiBake` a PLANNED marker (`m.runs`)
  whose chain has a loss becomes `{ lossy: [MARKER_STAMPS.notBaked] }`. The item is left as it was: no
  `list-style-type: none`, none of its runs, and `_lossy` + `marker-not-baked` on the wire. The line
  `if (m) markers.set(k, { ...m, rootPath });` is unchanged byte for byte, so the earlier M-6 anchor still applies.
- **`bidi-bake.test.mjs` (+157 / −2).**
  - `CS_CHAIN` (all-initial `::marker, li, ol, div`) goes into the `rtlProbe` / `cdpFacts` fact helpers: Chromium's
    counter-suffix chain.
  - With it all 69 earlier pins stay green unchanged, including V1 / V4 (the real counter-style bake) / V5 / V6 deep-equals.
    So **the landed corpus behaviour is unchanged**.
  - New §11 pins, S6-1 … S6-5 (69 → **74** tests, +5 tooling tests for the doc restamp):
    - **S6-1:** the verbatim counter-suffix plan, plus the frozen-fixture wire, with item 0.4.0 at `opacity: 0.5`. Result:
      declined + stamped, the root keeps only 0.4.1's `.`/`2`, +4 components rather than +6, and the other three items
      deep-equal the clean plan.
    - **S6-2:** each of the 8 keys on the li AND on its `::marker` declines. The ROOT's / wrapper's own effect does not.
    - **S6-3:** a `div` between item and root is read; the root is not.
    - **S6-4:** unmeasured, error, short, misaligned and null-key chains all decline.
    - **S6-5:** the REAL in-page reader through `collectMarkerFacts` on a fake DOM. It takes 2 evaluates, reads pseudo
      first, item → ol → div, and leaves `<body>` out. Error facts stay byte-identical. A chain fault gives `{ error }`,
      which is declined, and no-measured-facts makes no round-trip.

**Corpus radius** (`s1-fix/paint-radius.py`, output `paint-radius.out.txt`). It reads the [W-L3] pre / post trees and
looks at every list item the bidi bake boxed, under a root carrying rootProperties' four keys:
```
pre:  1435 docs · 4 bidi-baked li boxes in 1 docs {'wpt__css-counter-styles__counter-suffix.json': 4} · 0 with a paint effect on the item→root chain
post: 1435 docs · 4 bidi-baked li boxes in 1 docs {'wpt__css-counter-styles__counter-suffix.json': 4} · 0 with a paint effect on the item→root chain
sanity: paint-effect IR types present on the post wire: {'ClipPath': 52, 'Filter': 10, 'MaskImage': 4, 'MixBlendMode': 2, 'Opacity': 76, 'OverflowX': 1997, 'OverflowY': 2005, 'TextShadow': 2, 'Transform': 138, 'Visibility': 5}
```
The 4 RTL items of counter-suffix (`__0__4__0/1`, `__0__5__0/1`, chain = the li alone) carry none of these effects. The
`sanity` line shows the type names are live, so "clean" is not a misspelling.

Three static sources agree:
- the test source sets only margin / padding / line-height / list-style-type / width (`tools/wpt/css/css-counter-styles/counter-suffix.html`);
- the canvas frame (`capture-browser-ref.mjs canvasFrameCss`) styles only html / body;
- Chromium source, read via WebFetch (not run): `style_adjuster.cc` `AdjustStyleForMarker` sets only display,
  white-space and margins, and `html.css` sets none of the 8 keys on li / ol / div.

**Mutations** (`s1-fix/mutate.py` = the lane's runner with ROOT one level up and its log as `mutations-S6.out.txt`, since
`*.log` is gitignored, S1 S2; spec `mutations-S6.json`; result `mutations-S6.result.json`):
```
OK S6-m1 (REQUIRED) the decline is disabled at planBidiBake's call site: red # pass 71 # fail 3 [S6-1, S6-2, S6-4] | restored # pass 74 # fail 0 byte-exact=True
OK S6-m2 the chain bound reaches the ROOT (its own effects judged):    red # pass 72 # fail 2 [S6-2, S6-3]       | restored # pass 74 # fail 0 byte-exact=True
OK S6-m3 a box BETWEEN item and root is not read (item only):           red # pass 73 # fail 1 [S6-3]             | restored # pass 74 # fail 0 byte-exact=True
OK S6-m4 an unmeasured chain reads as clean:                            red # pass 72 # fail 2 [S6-4, S6-5]       | restored # pass 74 # fail 0 byte-exact=True
OK S6-m5 the tag alignment check is dropped:                            red # pass 73 # fail 1 [S6-4]             | restored # pass 74 # fail 0 byte-exact=True
OK S6-m6 opacity is not judged:                                         red # pass 71 # fail 3 [S6-1, S6-2, S6-4] | restored # pass 74 # fail 0 byte-exact=True
OK S6-m7 text-shadow is dropped from the read set:                      red # pass 72 # fail 2 [S6-2, S6-5]       | restored # pass 74 # fail 0 byte-exact=True
OK S6-m8 the in-page reader skips the ::marker pseudo:                  red # pass 73 # fail 1 [S6-5]             | restored # pass 74 # fail 0 byte-exact=True
OK S6-m9 collectMarkerFacts stops attaching the chain:                  red # pass 73 # fail 1 [S6-5]             | restored # pass 74 # fail 0 byte-exact=True
OK S6-m10 the in-page walk does not stop below <body>:                  red # pass 73 # fail 1 [S6-5]             | restored # pass 74 # fail 0 byte-exact=True
BYTE-IDENTICAL: all four files before == after the mutation run
```
sha256 before = after (`sha256.before-mutations.txt` = `sha256.after-mutations.txt`):

| file | sha256 |
|---|---|
| bidi-marker-paint | `9a7850a3…` |
| bidi-marker-bake | `c8e530bd…` |
| bidi-bake | `4d9e5359…` |
| bidi-bake.test | `f9fae712…` |

All 32 earlier L1 / S1 mutation anchors (M-1…15, M-4b, P-1…7 ×2, S1 `lit/l1-m12`, `l1-p3`) still match exactly once on
the fixed files (`old-anchors.out.txt`: 0 bad), so the recorded mutations stay replayable.

**Focused suites** (`focused.final.out.txt`):

| suite | result | why it was run |
|---|---|---|
| `bidi-bake.test.mjs` | 74 / 74, 0 skipped (V4 ran) | the owned suite |
| `wpt-white-canvas.test.mjs` | 21 / 21 | it source-scans bidi-bake.mjs |
| `extract-fixture.test.mjs` | 483 / 483 | it imports the bake gate |

`comments.py` (S1's heuristic, same regexes, over this pass's added lines) finds 1 uncommented line. It is the
continuation of `near`'s condition, which sits under the two-line comment describing all four terms. "engine" appears in
no added line.

**Note counts (S1 N5).** The plan fixer already corrected both lines: §4 reads "30/30 OK" and §9 reads "+105 / −4 …
net +101". I did not edit them. After this pass, §9's "1172 → 1273" is superseded by **1283** (this pass +13 / −3, shown
above).

**Could NOT verify:** no Chromium run. Whether the live probe really reads all-initial values on counter-suffix (the
static evidence above says it does) shows only at the next bake / gate. If it did not, counter-suffix's 4 markers would
come out DECLINED and stamped (the pre-M′ wire plus `marker-not-baked`), never silently wrong.

STATUS (S1 fix pass): COMPLETE
