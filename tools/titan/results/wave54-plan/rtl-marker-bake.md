# Wave-54 family brief — `rtl-marker-bake` (BACKLOG 0(a)(1), 2(c⁴)): the re-do of the reverted L1 U2

Gate of record `wave53-final` (dev 7cce3b22). The reverted unit's pictures are in `wave53-probe`. Paths are relative to the
repo root (`.claude/worktrees/trusting-bohr-bd6fbf`). Nothing was built, captured, extracted or run on a device or in
Chromium, because the `wave54-open` gate was live. Every number below comes from a look-up that was actually run, tagged as follows:

- **[cells]**: `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open wave53-probe wave53-final`.
- **[census]**: `rtl-marker-bake.census.mjs`, read-only over `per-test-ir/`. It writes `rtl-marker-bake.census.json` (wave53-final) and
  `rtl-marker-bake.census.wave53-probe.json`, summarised in `rtl-marker-bake.census.out.txt`.
- **[ir-diff]**: `rtl-marker-bake.ir-diff.mjs` (wave53-open → wave53-probe per-test IR) and its `.out.txt`.
- **[look]**: `rtl-marker-bake.look.py` (PIL on the frozen PNGs) and its `.out.txt`. I also opened the PNGs themselves; §2 says which ones.
- **[ident]**: `rtl-marker-bake.capture-identity.py` (decoded-pixel sha1 per capture) and its `.out.txt`.
- **[geom]**: `rtl-marker-bake.geometry.py` (the new probe, §7) and its `.out.txt`.
- **[replay]**: `rtl-marker-bake.replay.py` plus `rtl-marker-bake.replay-score.mjs`, scored with the gate's own
  `inject-wpt-block.mjs diffWebVsRef`. Output is in `rtl-marker-bake.replay-score.out.txt`; the six calibration rows reproduce [cells] exactly.

All of these files sit beside this brief in `tools/titan/results/wave54-plan/`.

## 1. Target cells

| cell | wave53-open | **wave53-probe** (U2 = M + P in) | wave53-final (U2 reverted) | standing label |
|---|---|---|---|---|
| css-counter-styles/counter-suffix web | P 0.9818 | P 1 | P 0.9818 | RTL markers on the wrong (left) side |
| css-counter-styles/counter-suffix ios | P 0.9802 | P 0.9873 | P 0.9802 | DEGENERATE (BACKLOG 0(b)) |
| css-counter-styles/counter-suffix android | P 0.9547 | **P 0.9793, GEOMETRY WRONG** | P 0.9547 | DEGENERATE (BACKLOG 0(b)) |
| css-text/bidi/bidi-lines-001 android | f 0.8934 | P 0.9629 | f 0.8934 | hunk P's mover |
| css-text/bidi/bidi-lines-002 android | P 0.9534 | P 0.9818 | P 0.9534 | hunk P's mover |
| css-text/bidi/bidi-lines-001 web / ios | P 0.9998 / P 0.9926 | same | same | must not move |
| css-text/bidi/bidi-lines-002 web / ios | P 0.993 / P 0.989 | same | same | must not move |
| css-anchor-position/anchor-center-safe-rtl ×3 | unscored | unscored | unscored | `scoreEligible:false` (wave53-final manifest) |

Sources: [cells] for every scored entry. The wave53-final manifest `wpt.results` entry gives `scoreEligible:false` for
anchor-center-safe-rtl.

## 2. The defect as SEEN

**Pictures opened:**
- `tools/titan/runs/wave53-probe/sections/css-counter-styles/{android,ios}-screenshots/wpt__css-counter-styles__counter-suffix.png`
  and the ref `tools/wpt/refs/…/css-counter-styles/counter-suffix.png`, plus a 4× crop of x95-165 / y205-325 of all three;
- `selectors/dir-style-02a` ref/web/ios/android (wave53-final);
- bidi-lines-001/-002 and anchor-center-safe-rtl ref, plus Android at wave53-open and wave53-probe.

**What the pictures show:**
- **Ref, iOS and web at the probe** all read `foo .1 / bar .2 / foo .א / bar .ב` on rows 9-12.
- **Android at the probe** reads:
  - `foo` with no marker;
  - `bar ·` and `foo ·`, each with a mid-height dot;
  - `bar .א`, with the aleph glyph higher than the text;
  - a fifth line holding a lone `ב.`.

**Measured** ([look] A: marker column x129-165, y205-340):

| row | ref marker ink | Android probe ink | Δ |
|---|---|---|---|
| 1 `.1` | y214-225 x134-144 | `.` at **y244-245** x134. The `1` is drawn nowhere. | +20 |
| 2 `.2` | y238-249 x134-144 | `.` at **y268-269** x134. The `2` is drawn nowhere. | +20 |
| 3 `.א` | y264-273 x134-145 | **y285-293** x134-145 | +21 / +20 |
| 4 `.ב` | y289-297 x134-144 | **y309-317** x134-144 (below the last text row y287-297) | +20 |

The blob list for Android at x ≥ 129, y ≥ 205 is `y244-245 · y268-269 · y285-293 · y292-293 · y309-317 · y316-317`.
None of these blobs is a digit.

BACKLOG 0(a)(1)'s "one row LATE" is imprecise, and the orchestrator should re-true it (§11 item 1):
- **every painted marker run is exactly one RUN height (20 px) low, not one 24-px row late;**
- **the third run of an item (`1`, `2`) is never painted.**

The text runs are right on all three platforms, at x104-126 (rows y214-225 / y239-249 / y262-273 / y287-297, [geom]).

The old probe `wave53-plan/lists-bakes.geometry.py` caught this only on row 1's right edge (x126 vs x144). Its band scan
stops at y303, so it never saw the fifth line. The new probe (§7) names the defect directly:
`[M] counter-suffix android … GEOMETRY WRONG (rtl row 1 no marker ink on text row y214-225 (marker ink y244-245 below the line))` [geom].

## 3. Mechanism (traced to code; not the bake's measurement, not run order)

1. **The wire (reverted M).** The reverted M (`git show a8ffd1c6 -- tools/titan/bidi-bake.mjs`, fix-pass state
   `b0edb788^`, l.925 / l.974) pushed each item's marker runs AFTER its text runs, as children of the `<li>` BOX:
   `plan.runs.push({ ownerPath: e.path, … })`. Each RTL `<li>` therefore became one `Position ABSOLUTE` box with 2-3
   `ABSOLUTE` text children. [ir-diff] lists the added components:
   - `counter-suffix__0__4__0__1 "."` and `__0__4__0__2 "1"` under `counter-suffix__0__4__0`;
   - `"א."` and `"ב."` under the Hebrew items.

   Every run is `Top 2 / Height 20 / LineHeight 20px`, the same as the item's text run. The CDP side was right:
   `tools/titan/results/wave53-lists-bakes/marker-probe.out.txt` reports `MARKER PROBE: ALL PASS`, with the row-1 box at x112.
2. **The Compose host never activates for this document.**
   - `CanvasRootHoist.hostActivates` = `anyOutOfFlowBox`
     (`runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/CanvasRootHoist.kt` :650 / :553). It is
     true only when some box hoists or is a no-inset static-position absolute:
     - hoisting is `shouldHoistToCanvasRoot` :311: `FIXED`, or `ABSOLUTE` with **no positioned ancestor** and an inset;
     - static position is `rendersInFlowAsStaticPosition` :366: `ABSOLUTE`, **no positioned ancestor**, no inset.
   - Every out-of-flow box in counter-suffix sits under a bake root. `tools/titan/bidi-bake.mjs` `rootProperties` :531
     makes that root `position: relative`, so neither predicate fires.
   - [census] lists counter-suffix among the 16 of 17 bidi-baked docs whose host is inactive.
   - `Host` therefore takes its identity fast path `if (!activates) { content(); return }` (:1022), and `LocalActive`
     stays `false`.
3. **The `<li>` renders its runs through the plain Column loop.** In
   `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`:
   - The RELATIVE `<ol>` root is an `isPositionedContainer` (:3475). It renders through `Box(Modifier.fillMaxSize())`
     plus `RenderAbsoluteChild` (:3506 / :3520), so each `<li>` lands at its own Left/Top. That is why `foo` is right.
   - The `<li>` is `ABSOLUTE`, not `RELATIVE`, so it is NOT a positioned container. Its children go through
     `renderBlockChild` (:3565) from the frozen loop (:3974-3976).
   - There, `outOfFlowSlotMount` (:3617-3627) is `PositionedParentFlowSlot.mount(…)` **only when
     `CanvasRootHoist.LocalActive.current`** is true. Otherwise it is the identity `Modifier`.
   - So each absolutely positioned run keeps its full Column footprint. This is the defect
     `layout/position/PositionedParentFlowSlot.kt`'s own header describes ("an abspos child keeps its FULL flow
     footprint"). Its wave-46 fix is host-gated, so this document never gets it.
4. **The Column's remaining-space rule.** The same header documents it: Compose's Column hands each non-weighted child
   "the remaining main-axis space". The `<li>` has `Height 24`:

   | run | cursor | remaining height | result |
   |---|---|---|---|
   | 1 (`foo`) | 0 | 24 | footprint 20 |
   | 2 (`.` or `א.`) | 20 | 4 | painted with visible overflow, its own `Top 2` added: +20 px |
   | 3 (`1` / `2`) | 24 | 0 | painted nowhere |

   That a 0-px slot paints nothing is OBSERVED twice: here, and in the corroboration below. The exact skip inside
   Compose's text draw was not traced.
5. **Why iOS and web were right.**
   - SwiftUI mounts every out-of-flow child in a ZStack overlay, whatever position kind the parent has
     (`runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` `outOfFlowChildren` :793,
     `overlayChildren` :812, `positionedChildren` :2094).
   - Web is real CSS `position: absolute`.

   Neither reserves flow space for an absolutely positioned sibling.
6. **Corroboration from two documents already in the corpus** ([census] stack-shape table, [look] B / C):
   - **`selectors/dir-style-02a` has the same shape and its host is inactive.** It has three baked boxes, each
     `Height 20` and holding [`"This element is rtl"`, `"."`]. On Android at wave53-final:
     - none of the three `.` is painted (dot rows y130-131, y150-151 and y190-191 are missing; row 7 starts at x22 against the ref's x17);
     - nothing is painted one row lower.

     The second run has 0 px left, so it is the same zero-slot loss. The cell is android f 0.9425 [cells].
   - **`css-anchor-position/anchor-center-safe-rtl` is the same shape with the host ACTIVE** (its FIXED boxes activate
     it). Box `anchor-center-safe-rtl__3__0` holds 4 runs at Top 0/20/40/60. Android paints them at y57/78/97/117, the
     20-px pitch of their declared tops. The zero-footprint mount works when the host is on.

**Conclusion.** The defect is a host-gated Compose mount: a non-RELATIVE out-of-flow parent with ≥ 2 out-of-flow children,
in a document whose `CanvasRootHoist.Host` is inactive. The wave-53 lane produced that shape for the first time on a
scored target, because it parented the marker runs to the item.

## 4. The re-do: two units, pre-registered separately

### Unit P — padding kept on a bidi-bake root is zeroed (its OWN commit, lands first)

- **What changes.** Exactly hunk P of `a8ffd1c6`, in its fix-pass state (`git show b0edb788^:tools/titan/bidi-bake.mjs`):
  - `inPageBidiWalker` records `padding`, `backgroundClip`, `backgroundOrigin` and `overflow`;
  - `export function paddingIsSpent(el)` (l.573) is added;
  - the call becomes `rootProperties(rect, position, el)` (l.934), with `padding: '0'`;
  - `applyBidiBakePlan` deletes the `padding-*` keys first and overwrites an authored shorthand in place.

  The pins are wave 53's V3, V3b (the zero-padding roots of `dir-style-02a` and `dir-selector-change-003/-004` stay
  byte-identical) and the content-box / overflow guard pins. They move out of `b0edb788^:tools/titan/bidi-bake.test.mjs`
  into P's own commit, and every mutation must be re-executed.
- **CSS.** CSS 2.1 §10.1 item 4 and css-position-3 §3.1: an absolutely positioned box resolves its insets against the
  PADDING box of its containing block. The root is `border-box` with its used size, so zeroing the padding moves nothing
  on a conforming renderer. Android anchors at the content box (`PositionedParentFlowSlot.kt` header, "insets resolve from
  the content-box corner"), so on Android it moves every run by the padding.
- **Device evidence (not a replay).** At wave53-probe, P was the ONLY wire change on bidi-lines-001/-002 and
  anchor-center-safe-rtl. [ir-diff] shows only `Padding*` → `{px:0}` there, and component counts 13→13, 19→19, 14→14.
  - On those three documents the web and iOS captures are **byte-identical in decoded pixels** to wave53-open, and
    Android moved ([ident]).
  - [geom] at wave53-probe prints the following, where wave53-open / wave53-final print WRONG with x33 / x35:
    - `[P] bidi/bidi-lines-001 android line starts y117:x29 y159:x274 → GEOMETRY OK`;
    - `[P] bidi/bidi-lines-002 android line starts y159:x31 y239:x276 → GEOMETRY OK`.
- **Wire.** 4 content-changed documents (counter-suffix with 2 roots, bidi-lines-001, -002, anchor-center-safe-rtl with
  2 roots), so 6 roots in 4 docs [census `paddedRoots`]. No component is added, so there is **no id shadow**.
- **Log line.** Unchanged: `[bidi-bake: baked — 2 roots, 4 runs] [counter-bake: baked — 10 markers, 2 declined]` on counter-suffix.

### Unit M′ — the RTL `::marker` baked as runs owned by the bake ROOT (second commit, behind a device probe)

- **What changes.** Restore `tools/titan/bidi-marker-bake.mjs` (`git show b0edb788^:tools/titan/bidi-marker-bake.mjs`,
  200 lines, fix-pass state: the not-re-found item is declined and stamped, and the half-leading is removed) and its
  call sites. There is **one design change**, at the two call-site lines of `b0edb788^:tools/titan/bidi-bake.mjs`:
  - l.925: `planMarker(walk.markers?.[k], originOf.get(<enclosing root key>), runsByPath.get(k)?.[0])`. The marker runs
    are measured from the root's padding-box origin; P has made the root's padding box equal to its border box.
  - l.974: `plan.runs.push({ ownerPath: <enclosing root path>, props: r.props, text: r.text })`. The runs are appended
    after the root's own children, and the `<li>` keeps exactly one child, its text run.
  - The `<li>` still gets `list-style-type: none`, the stamps are unchanged, and a ROOT list item (arabic-indic) still
    gets nothing (V2).
- **Why this fixes Android without runtime code.** Root-owned runs take the RELATIVE root's `Box` branch: each one goes
  through its own `RenderAbsoluteChild` mount, with no Column cursor. That path is device-proven:
  - `bidi-lines-002`'s `Hello` and `سلام` runs are direct children of its RELATIVE root, and land on the ref's x at
    wave53-probe ([geom] above);
  - the `<li>` boxes themselves already sit correctly on that path.

  On iOS the overlay is the same kind of mount: the `<li>` are already out-of-flow children of the same `<ol>` and land
  right. On web, Blink places the absolutely positioned runs against the `<ol>`'s padding box: 48 + 68.3 = 116.3 either way.
- **Predicted wire.** counter-suffix goes from 23 to 29 components.
  - The 4 `<li>` gain `ListStyleType none`, lose `meta.markerText`, and keep ONE child each.
  - 6 runs get `slot.parent` = the `<ol>` roots. In root coordinates (the wave53-probe li-relative wire + the li's
    Left 48 / Top 0 or 24):

    | run | Left | Top | Width | other |
    |---|---|---|---|---|
    | `.` (row 1) | 116.3 | 2 | 4.3 | |
    | `1` | 120.59 | 2 | 10.38 | |
    | `.` (row 2) | 116.3 | 26 | 4.3 | |
    | `2` | 120.59 | 26 | 10.38 | |
    | `א.` | 116.3 | 2 | 14.62 | `Direction RTL` |
    | `ב.` | 116.3 | 26 | 13.31 | `Direction RTL` |

    Every run also carries `FontVariantNumeric TABULAR_NUMS`.
  - Id shadow: +6 over the 15 later `css-counter-styles/cssom/*` documents of `tests.list` (wave53-final `tests.list`:
    counter-suffix is entry 33 of 48). Their captures must be byte-identical; this is the class wave 53 window result 5
    named and `control-check` reports as "renumbered".
  - Log line: `[bidi-bake: baked — 2 roots, 10 runs] [counter-bake: baked — 6 markers, 2 declined]`.
- **Alternatives rejected for this lane:**
  - (a) One marker run per item under the `<li>`. That still gives 2 runs in a host-inactive Column: the second
    lands +20.
  - (b) The runtime fix: drop the `LocalActive` gate on `PositionedParentFlowSlot.mount`, or treat an
    ABSOLUTE/FIXED parent as a positioned container. It is the true defect, but its radius covers:
    - the 10 host-inactive stack-shape parents in 6 docs [census], with scored cells
      `anchor-position-multicol-004` android P 0.9686, `-nested-001` android f 0.9573,
      `dir-selector-change-003/-004` android P 0.9994 / 0.9944 and `dir-style-02a` android f 0.9425 [cells];
    - every host-inactive abspos parent's auto height;
    - a seam (ComponentRenderer.kt).

    That is a separate queue item (§11 item 2), not a bake lane.

## 5. Census of the corpus radius ([census], over 1435 wave53-final per-test IR docs)

- **Bidi-baked documents.** There are 17, holding 40 scored cells (31 P / 9 f) [cells]. anchor-center-safe-rtl and
  attachment-local-positioning-3/-4 are unscored. css3-counter-styles-102 is scored on web only.
- **Host state.** The host is inactive on 16 of the 17. The exception is anchor-center-safe-rtl.
- **Stack shape.** An ABSOLUTE/FIXED/STICKY parent with ≥ 2 out-of-flow children occurs **25 times** corpus-wide.
  - 15 of those sit under an active host. These are the wave-46 Y6 targets, which render right.
  - **10 sit under an inactive host**, in these documents:
    - anchor-position-multicol-004 (1) and -nested-001 (2);
    - content-visibility-auto-shared-element (2);
    - dir-selector-change-003 (1) and -004 (1);
    - dir-style-02a (3).
  - Only dir-style-02a's three are baked text-run boxes.
  - At wave53-probe the count was 12 under an inactive host, of which 4 were counter-suffix's `<li>` boxes (2 RTL
    lists × 2 items) — the reverted M created them. The probe ran 20 sections, so css-view-transitions' 2 are absent
    from that count.
  - **After M′: 0 new.** Every marker run is a child of a RELATIVE root, and every `<li>` box keeps a single run.
    Pin V7 (§8) asserts this.
- **Hunk M′ carriers.** `meta.markerText` on an out-of-flow box occurs on exactly **4 components**, all in
  counter-suffix (`__0__4__0` "1.", `__0__4__1` "2.", `__0__5__0` "א.", `__0__5__1` "ב."). These are also the only bake
  boxes that are list items; the arabic-indic `<li>` are roots.
- **Hunk P carriers.** Non-zero `Padding*` on a bake root occurs on **6 roots in 4 docs**:
  - counter-suffix: 2 × `0 3em`;
  - bidi-lines-001 and -002: `0 0.5ch` each;
  - anchor-center-safe-rtl: 2 × 10px.

  This matches the wave-53 padding census and the skeptic's.
- **Cells that could move.**
  - P: Android counter-suffix P 0.9547, bidi-lines-001 f 0.8934, bidi-lines-002 P 0.9534, and anchor-center-safe-rtl
    (unscored).
  - M′: counter-suffix ×3 (P 0.9818 / 0.9802 / 0.9547 today).

  Nothing else is a carrier.

## 6. Ownership and seams

- **Owned files** (disjoint from every other wave-54 family; if another brief claims `bidi-bake.mjs`, this family's two
  commits land first):
  - `tools/titan/bidi-bake.mjs` (P hunk, plus the two M′ call-site lines and the import);
  - `tools/titan/bidi-marker-bake.mjs` (restored, new to the tree);
  - `tools/titan/bidi-bake.test.mjs` (pins split per unit);
  - the lane dir `tools/titan/results/wave54-rtl-marker-bake/` (note, census re-run, mutation logs, and a COPY of
    `wave53-lists-bakes/marker-probe.mjs` with the ownership assertions changed: today it asserts `kids.slice(1)` of each
    `<li>`, which must FAIL under M′ by design).
- **Seams: none.**
  - `bidiBakeFixture(fixture, testRel)` keeps its signature and its call site in `extract-fixture.mjs`.
  - No edit to either `ComponentRenderer`, the web-harness `ComponentRenderer.tsx`, `counter-style-bake.mjs` (its
    `BULLET_STYLES` skip of `none` is used as-is), a runtime, the converter or the schema. Every emitted property
    already rides the wire.
- **Not this lane: the `ListMarkerOutsideHang.swift:33-41` comment re-true.** Wave 53 carried it in U2; that kept a
  runtime file in a bake unit. It goes to the orchestrator's docs pass when M′ lands.
- **Size note (BACKLOG 0(d)).** The 0(d) line "`tools/titan/bidi-bake.mjs` +91 (disclosed by L1)" is stale: the revert took
  the file back to 1172 lines (HEAD = `a8ffd1c6^` = 1172; the fix-pass state was 1259). Re-landing P + M′ adds about 87
  lines again. Put the new logic in `bidi-marker-bake.mjs` and keep only call sites plus `paddingIsSpent` in
  `bidi-bake.mjs`. Splitting the 1172-line file itself is a byte-identity refactor for another commit, not this lane.

## 7. Geometry probe — `tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py <run> [base]`

It runs pure PIL over 12 PNGs (+3 with a base). It imports `wave53-plan/geometry_common.py` and exits 1 if any ref row
fails. Each line ends in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<why>)`.

**[P] lines** (`bidi/bidi-lines-001`, `bidi/bidi-lines-002`, every platform):
- The left edge of one baked line per window must equal the ref's ±1:
  - 001: `français` x29, `فارسی` x273;
  - 002: `! Hello` x31, `سلام !` x276.
- The windows deliberately skip bidi-lines-002's first line (§10).
- The form is `[P] bidi/bidi-lines-001 android  line starts y117:x29 y159:x274 → GEOMETRY OK`.

**[M] lines** (counter-suffix, every platform). The four RTL text rows in x95-128 must:
- (a) have a left edge equal to the ref's ±3 (unit P);
- (b) carry marker ink in x129-150 that **overlaps that row's own text band** (±2), at least 5 px wide (a lone `.`
  is 1-2 px), with its right edge at x144 ±3;
- (c) leave **no ink in y300-335** below the last row;
- (d) leave no ink in x40-63 inside the RTL band.

With a base run it adds `| rows 0-207 identical to <base>`.

**Executed** ([geom]):

| run | [P] lines | [M] lines |
|---|---|---|
| wave53-open and wave53-final | ref/web/ios OK; android WRONG (x33, x35) | ref OK; web/ios `… no marker ink on text row y214-225`; android `rtl text rows 0/4 in x95-128 (text at x152-174)` |
| wave53-probe | OK on all four rows | ref/web/ios OK; android **`GEOMETRY WRONG (rtl row 1 no marker ink on text row y214-225 (marker ink y244-245 below the line))`** — the reverted unit's defect, named |

Self-test on the replays: `android-P` prints WRONG (no marker); `android-Mp-dev` and `android-Mp-ref` print OK.

**Required verdicts:**

| after | must print |
|---|---|
| unit P | `[P] … android … GEOMETRY OK` ×2. `[M]` stays WRONG on ios/web (no marker / left marker). The Android `[M]` failure changes from "text rows 0/4" to "no marker ink on text row". |
| units P + M′ | **every `[P]` and `[M]` line `GEOMETRY OK`, plus `rows 0-207 identical to <base>` on all three**. `wave53-plan/lists-bakes.geometry.py <run> <base>` must also print counter-suffix ×3 `GEOMETRY OK`. |

## 8. Predictions (from = wave53-final; re-read against `wave54-open` before landing — its opening gate must print 0 movers)

**Unit P** (geometry: the two `[P] android` lines OK):

| cell | from → to | confidence / floor | basis |
|---|---|---|---|
| bidi-lines-001 android | f 0.8934 → **P 0.9629** | HIGH / floor 0.955 | device-measured: these hunk bytes at wave53-probe [cells] |
| bidi-lines-002 android | P 0.9534 → **P 0.9818** | HIGH / floor 0.975 | device-measured [cells]. **Stays DEGENERATE (§10)** |
| counter-suffix android | P 0.9547 → **P ≈ 0.9815** | MED-HIGH / floor 0.970 | [replay] 0.9815. The recipe's bidi-lines replays (0.9818 / 0.9629) equal the device values. RTL text x152 → x104, still no marker. **Stays DEGENERATE** |
| counter-suffix web, ios | byte-identical | MED-HIGH | P's invariance measured on 3 docs × web/ios [ident] |
| bidi-lines-001/-002 web+ios, anchor-center-safe-rtl web+ios | byte-identical | HIGH | [ident]: probe == open |
| anchor-center-safe-rtl android | moves (unscored) | — | wave53-probe capture changed [ident]. The picture is wrong before and after: no green `Anchor`, boxes misplaced |

**Unit M′** (on top of P; geometry: every `[M]` line OK at the device probe AND at the closing gate):

| cell | from → to | confidence / floor | basis |
|---|---|---|---|
| counter-suffix web | P 0.9818 → **P 1** | HIGH / floor 0.999 | wave53-probe measured 1 with the same marker geometry. Root vs li parent places Blink's runs at the same used x (48 + 68.3 = 116.3) |
| counter-suffix ios | P 0.9802 → **P ≈ 0.987** | MED-HIGH / floor 0.985 | wave53-probe measured 0.9873 (overlay mount, same kind under the root). **RTL rows picture-correct; cell stays DEGENERATE on rows 3–6** |
| counter-suffix android | P ≈ 0.9815 (after P) → **P ≈ 0.989** | MED (no floor; gated by geometry) | [replay] 0.9894 (device glyphs lifted 20 px, ref digits) / 0.9900 (ref marker). **RTL rows picture-correct only if the device probe prints OK; cell stays DEGENERATE on rows 5–6** |
| M′ landed WITHOUT P, android | → **f ≈ 0.947** | HIGH, pre-registered LOSS | wave-53 replay 0.9466 (marker and text +48). M′ never lands before P |
| the wave-53 shape (runs under `<li>`) + P, android | P 0.9793, GEOMETRY WRONG | measured | wave53-probe: the regression the device probe guards |

**Must not move** (byte-identical captures; HIGH unless stated):
- **Every platform of the 13 other bidi-baked documents.** That is 35 scored cells, 27 P / 8 f [cells]:
  - arabic-indic css3-counter-styles-101 ×3, -102 web, -103 ×3;
  - bidi-tab-001 ×3, boundary-shaping-009 ×3, hyphenate-character-005 ×3, bidi-plaintext-br-001 ×3;
  - dir-selector-change-003 ×3 and -004 ×3, dir-style-02a ×3, dir-style-03a ×3;
  - attachment-local-positioning-3/-4 (unscored).

  The 35 also count bidi-lines-001/-002 web + ios.
- **counter-suffix rows y0-207 on all three platforms**, under both units: the `rows 0-207 identical` crop, which
  already holds at wave53-probe [geom].
- **The 15 `css-counter-styles/cssom/*` captures under M′**: id shadow only.
- **Every other capture and per-test IR in the corpus.** The bake does not run there.
- **The per-test IR of `dir-style-02a`, `dir-selector-change-003` and `-004`** (zero-padding roots, pin V3b).

**Watchlist lines:**
- `css-counter-styles/counter-suffix.html`
- `css-text/bidi/bidi-lines-001.html android`
- `css-text/bidi/bidi-lines-002.html android`
- the 35 must-not-move cells above, in the wave-53 format.

**`control-check` carrier sets:**

| unit | captures | wire |
|---|---|---|
| P | android {counter-suffix, bidi-lines-001, bidi-lines-002, anchor-center-safe-rtl} | the same 4, no shadow |
| M′ | web/ios/android {counter-suffix} | {counter-suffix} + `wireRenumbering` css-counter-styles +6 × 15 |

## 9. Verification plan (lane) and the device windows the lane must REQUEST in its note

**Pins.** Run `node --test tools/titan/bidi-bake.test.mjs`. Every mutation is executed (red, byte-exact restore, green)
and logged with sha256.
- **Unit P:** V3, V3b, V3c-style guards (content-box clip/origin, non-`visible` overflow, zero padding untouched).
- **Unit M′:**
  - wave 53's V1, V2, V4, V5, VF, VF1, VF2, VF3, ported to root ownership;
  - **V6:** on the counter-suffix walk, every marker run's `ownerPath` is the enclosing root, with root-relative
    left 116.3 / 120.59 (±0.05) and top 2 / 26. Mutation: owner = li → red;
  - **V7:** no `plan.boxes` entry is the owner of more runs than it has text runs (the guard against recreating the
    Compose stack shape). Mutation: push the marker runs to the box → red.

**Windows, in this order.** Each is run by the orchestrator in a device-idle slot; [W1] and [W2] start Chromium / Gradle.
- **[W1] CDP marker probe.** Run the lane's adapted copy of `marker-probe.mjs`. Expect:
  - `MARKER PROBE: ALL PASS`, outcome `baked — 2 roots, 10 runs`;
  - each RTL `<li>` with `list-style-type: none`, ONE child, and no `marker-*` stamp;
  - 6 root-owned runs at frame x ⊂ [131,148] with top = li top + 2 (±1);
  - DOMSnapshot strings `'1. ','2. ','א. ','ב. '`.
- **[W2] Converter hop** for counter-suffix with the gate flags (as wave-53 window 3). Expect:
  - the §4 M′ wire: the 6 runs' `slot.parent` = the two `<ol>` roots;
  - the RTL roots' `PaddingTop..Left {px:0}`;
  - no converter warning.
- **[W3] One-section DEVICE probe of the lane tree (P + M′ committed), BEFORE the closing gate:**
  `tools/titan/gate-driver.sh wave54-rmb-probe --sections css-counter-styles --skip-fixture-net` on a quiet host
  (about 2.5 min per section). Read:
  1. `python3 tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py wave54-rmb-probe wave54-open`. All three
     `[M] counter-suffix` lines must print `GEOMETRY OK` with `rows 0-207 identical to wave54-open`. The `[P]` lines print
     MISSING because css-text is not in the section; they are read at the closing gate. Add `css-text` to `--sections`
     if the orchestrator wants P confirmed early.
  2. `python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py wave54-rmb-probe wave54-open` must print
     counter-suffix ×3 `GEOMETRY OK`.
  3. `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix' wave54-open wave54-rmb-probe` must show web ≥
     0.999 and ios ≥ 0.985.
  4. OPEN the three PNGs. Rows 9-12 must read `foo .1 / bar .2 / foo .א / bar .ב`, with nothing below y300.
  5. The other 47 css-counter-styles captures must be byte-identical (15 of them renumbered).

  **Any `[M]` WRONG, or any leak, means M′ is reverted before the closing gate. P stays, and its own §8 rows stand.**
  Never label a native "picture-correct" from [replay].

## 10. Rows of counter-suffix that stay DEGENERATE even when M′ is right ([look] D, wave53-final; out of family)

**iOS, rows 3–6:**
- Rows 3–4: the Hebrew LTR markers read `.א` / `.ב`. On row 3's baseline (y81) the period ink sits at x48, LEFT of the
  aleph legs (x51-52 / x57). In the ref the legs are at x46 / x53 and the period at x57: the period comes after.
- Rows 5–6: the CJK `一、` / `二、` marker ink is at x29-48 against the ref's x33-51, 4 px left.
- Glyph-level, not counted: row 1 draws a proportional `1` without the tabular foot (marker ink x49-56 vs x47-58;
  row 2's `2` is x46-57 vs x47-58). At wave53-probe the RTL row-1 `.1` was x134-141 vs x134-144: SwiftUI does not
  apply `FontVariantNumeric TABULAR_NUMS` to the run.

**Android, rows 5–6:**
- The CJK marker is at x29/30-48 vs x33-51.
- Rows 3–4 are right: the baseline ink x46/53/57 matches the ref.
- The row-1 tabular-foot glyph residual (x49-56) is shared with iOS. Whether Android honours tabular-nums on the RTL digit
  runs is unknown: they never painted. [W3] sees it.

Both cells are therefore labelled **"RTL rows picture-correct; cell stays DEGENERATE on rows 3–6 (iOS) / 5–6 (Android)"**.
Never call them faithful. Web reaches 1 and is faithful.

**Also out of family:**
- **bidi-lines-002 stays DEGENERATE on all three platforms.** The top and bottom orange `!` are on the LEFT on web, iOS and
  Android (wire `bidi__bidi-lines-002__1__0` and `__1__8`: `Left 10.09`), where the ref has them on the right (x343). That
  is a bake-measurement defect, not P's. P must not be credited with it.
- **bidi-lines-001 android after P.** The `فارسی` runs start at the ref's x (274 vs 273), but the native Arabic face is
  wider: right edge x354 vs x348. That is a font residual.

## 11. Risks

1. **The BACKLOG wording is wrong.** "One row late … run-ordering" should be re-trued to: "one RUN height (+20 px); the
   third run never painted; cause: `CanvasRootHoist.LocalActive` false → `PositionedParentFlowSlot.mount` not applied in
   the abspos-parent Column loop".
2. **The runtime defect stays in the tree.** M′ routes around it.
   - It already costs `dir-style-02a` android its three `.` (f 0.9425). Those boxes are also mirrored on both natives
     (rows 6 and 9 right-aligned), which is a second defect.
   - It will catch any future bake that gives a box ≥ 2 runs.
   - Queue it as its own runtime item, with this census (10 parents / 6 docs) and the anchor-center-safe-rtl control.
   - A candidate bake-side follow-up is "multi-run boxes own their runs from the root", radius 3 boxes in dir-style-02a.
     It was NOT measured here.
3. **M′ adds non-`<li>` children to an `<ol>` on all three runtimes.**
   - Every list-specific path is gated on IN-FLOW `li`: Compose `liKeepsItsMarker` / `ListOrdinal`, the iOS marker
     branch, Blink list-items.
   - These runs are out of flow, as are the `<li>` they sit beside, and those already render right.
   - Unmeasured until [W3]. MED.
4. **Compose may ignore `font-variant-numeric` on the digit runs.** That gives a proportional `1`, 2-3 px narrower
   (iOS already does this). The geometry tolerance (right edge 144 ±3, width ≥ 5) admits it. The score effect is ≤ about 0.001. LOW.
5. **Unit order is load-bearing.** M′ without P is a pre-registered loss (0.9466). Commit P first, and never cherry-pick
   M′ alone.
6. **The CDP facts were last proven on the wave-53 tree.** On a changed Chromium or host, [W1] re-proves them. A
   `marker-not-baked` stamp on any RTL item means M′ does not land.
7. **The [W3] window costs a device slot.** It is cheap (one section), but it must not run under a live gate (memory:
   quiet-host gate). If no slot opens before the closing gate, land P alone and carry M′ to wave 55. That is a GO-SMALL
   fallback, not a reason to skip the probe.

## 12. Recommendation: **GO**

There are two small bake-only units, S–M effort:
- **Unit P is device-proven.** Its exact bytes moved bidi-lines-001 android f → P (0.8934 → 0.9629) and -002 +0.028
  at wave53-probe, with web and iOS byte-identical. It lands first, under its own predictions.
- **Unit M′ is a two-line change of ownership** on top of wave 53's measured marker bake. It moves the marker runs from
  the out-of-flow `<li>` (Compose's host-gated Column loop) to the RELATIVE bake root (the `Box` branch, device-proven
  on bidi-lines-002's root-owned runs). It lands only after a one-section device probe prints `[M] … GEOMETRY OK` on all
  three platforms.

The fallback, if [W3] is WRONG or no device window opens, is P alone.
