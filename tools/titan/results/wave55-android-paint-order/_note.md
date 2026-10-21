TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-fix2-L6
TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-reverify-L6
TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-fix-L6
TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L6

# Lane L6 · android-paint-order — builder note (wave 55)

Base tree **611c9f93** (= dev 0d0d3a3d + the committed wave-55 plan + the opening-gate records), worked in the throwaway
worktree on the `TREES:` line, never on the shared tree (the lane dir is the only shared-tree path written). Compose
only, **no seam patch**, no wire byte changed. Three revert units, landing order U1 → U2 → U3 (`landing-manifest.json`
seq 10-12), revert order U2 → U1 (U2's fast path calls U1's painter), U3 alone.

## What changed and why

**The defect (CSS 2.1 Appendix E §E.2).** A box's border is painted at step 2, before its in-flow (steps 4/7) and
positioned (step 8) descendants, so a child that overflows across its parent's band covers it. Compose painted every
band AFTER the content:
- androidx foundation 1.11.4 `BorderModifierNode` (re-read here with `javap -p -c` of
  `androidx/compose/foundation/{BorderModifierNode,BorderKt}` from the Gradle cache's `foundation.aar`): the
  rectangle (`BorderKt.drawRectBorder` lambda), rounded (`drawRoundRectBorder` lambdas $0 / $1) and generic
  (`drawGenericBorder` lambdas $0 / $3) branches each start with `ContentDrawScope.drawContent()`;
- `BorderSideApplier.applyBorders`' slow path was `drawWithContent { drawContent(); paintSide ×4 }`, chained by
  `BordersFacade.apply` OUTER of `ColorApplier` (so also outside the element's opacity group).

**The fix.** The band is drawn by a node that paints the band, THEN `drawContent()`:
- `borders/BehindContentBorder.kt` (NEW, 200 lines): `Modifier.behindContentBorder(width, color, shape)` →
  `BehindContentBorderElement` → `BehindContentBorderNode` (a `DelegatingNode` over a `CacheDrawModifierNode`, i.e. the
  band is built once per size exactly like androidx's node; the brief left "cached or recomputed" to the lane — cached).
  `buildBand()` copies the bytecode arithmetic: `bandGeometry` (hasValidBorderParams; strokeWidthPx =
  min(Hairline ? 1 : ceil(w), ceil(minDimension / 2)); topLeft = half stroke; borderSize = size − stroke; fillArea =
  2·stroke > minDimension), `rectBand` (drawRectBorder), `roundedBand` (isSimple → fill / clipRect(Difference) / shrunk
  stroke; non-simple → outer RoundRect path minus `insetRoundRect` = createInsetRoundedRect with `shrinkCorner` =
  BorderKt.shrink), `genericBand` (drawGenericBorder: fill, or the Alpha8 / Argb8888 mask bitmap with the 2× stroke, the
  over-scaled Clear mask and the opaque tint). Only the position of `drawContent()` moves: `drawInOrder(band, content)`.
- U1: `BorderRadiusApplier.applySelfPaint` installs it in place of androidx `border(…)` (self-paint route).
- U2: StyleApplier's legacy route chains the sides INNER of `ColorApplier` (after the background, inside its opacity
  group — css-color-4 §3.3) and paints them behind the content: fast path = the U1 painter on `RectangleShape`, slow path =
  the same four `paintSide` calls routed through `drawInOrder`.
- U3: a JVM pin that the verbatim clip-path-filter-order wire installs the clip-path layer OUTER of the drop-shadow
  `DrawBehind` node (filter-effects-1 §5: filter, then clipping), closing BACKLOG 0(k)(2)'s ORDER question at the chain
  level. Whether the shadow renders on device stays [W1]'s question.

## Unit U1 — PO-selfpaint-android (`unit-U1.patch`)

Paths (exactly these):
- `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt` (NEW)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/radius/BorderRadiusApplier.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BehindContentBorderTest.kt` (NEW)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BorderRadiusSelfPaintTest.kt`

Change: `BorderRadiusApplier.applySelfPaint` → `result.behindContentBorder(` (was `result.border(`), the androidx import
swapped for the new one, kdoc of `applySelfPaint` and of `selfPaintsWithoutClip` re-trued.
`BorderRadiusSelfPaintTest`: the two "installed chains" tests now name `BehindContentBorderElement` (and assert no
`BorderModifierNodeElement`, background outer of the band); header comment re-trued. Pins P1a–c, P2a–e in
`BehindContentBorderTest`.

## Unit U2 — PO-sides-android (`unit-U2.patch`)

Paths (exactly these):
- `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt`
- `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BordersFacade.kt`
- `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/sides/BorderSidesBehindContentTest.kt` (NEW)

Change:
- `BorderSideApplier.applyBorders(modifier, config, behindContent: Boolean = false)`: fast path one-line condition
  `if (behindContent) return modifier.behindContentBorder(width, color, RectangleShape)`; slow path: the four `paintSide`
  calls (lines unchanged) now sit in a local `val paintSides: DrawScope.() -> Unit`, dispatched by ONE line:
  `drawWithContent { if (behindContent) drawInOrder({ paintSides() }, { drawContent() }) else { drawContent(); paintSides() } }`
  — the default keeps the old content-then-band order for every other caller.
- `BordersFacade.apply(…, sidesBehindContent: Boolean = false)` (one-line condition skips the sides) and NEW
  `BordersFacade.applySidesBehindContent(modifier, config)`.
- `StyleApplier.applyConfig` legacy branch: `BordersFacade.apply(result, paint.borders, sidesBehindContent = true)`;
  `ColorApplier.applyColors(result, paint.colors)` (unchanged); `BordersFacade.applySidesBehindContent(result, paint.borders)`
  (+ 2 comment lines). Both new calls read `paint`, never `config` (BackfaceCullChainTest W4's rule; its regex still counts
  5 decoration calls — `applySidesBehindContent(` is not matched by `BordersFacade\.apply\(`).
Pins P3a–g in `BorderSidesBehindContentTest`.

## Unit U3 — FO-pin (`unit-U3.patch`, test only)

Paths (exactly this): `runtimes/compose/src/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt` (NEW)

P4a: `EffectsFacade.apply(Modifier, EffectsFacade.extractConfig(<the 7 verbatim properties of
clip-path-filter-order `…__1-072`>, wptCaptureMode = true))` holds exactly one `GraphicsLayerElement` with `clip = true`
(read reflectively) and exactly one `DrawBehindElement` (the drop-shadow), the clip FIRST (outer). P4b: the same on the
full `StyleApplier.extractConfig → applyConfig` chain (folded: `GraphicsLayerElement>DrawBehindElement>LayoutElement>…`).

## Deviations from the brief / plan, and why

1. **The brief's P2 example is off by one.** "30 px on 50×50 → fillArea" is false under the bytecode: stroke =
   min(30, ceil(25)) = 25 and fillArea needs 2·25 **>** 50 (strict). P2c pins both: 51×51 → 26, fillArea; 50×50 → 25, a
   Stroke.
2. **The generic outline branch is mirrored too** (`genericBand`), though the corpus has 0 self-paint bands on an
   `EllipticalCornerShape` (census: all 10 are `rounded`) — so it is device-unexercised (like R5's slow path). Not
   mirroring it would have needed a silent fallback to the content-first androidx node for elliptical / %-radius bands.
3. **BorderSideApplier slow path**: more than "two one-line conditions" in the literal sense — the existing side-painting
   lines moved into a local lambda (no line of them changed) so that the dispatch can route through `drawInOrder` as the
   plan's P1 coverage claim requires; the file grew 636 → 647 (+11, of which 6 are comments).
4. **Extra pins beyond the plan's P1–P4** (all mutation-proven below): P1c / P3g source pins (the JVM cannot draw, so the
   node's and the slow path's ORDER are pinned in their source: the band-first `drawInOrder` call and exactly two
   `drawContent()` sites); P3d (under `opacity` the band joins the element's group); P3e (BordersFacade's default chain is
   still androidx `BorderModifierNodeElement`); P3f; P4b (full chain).
5. **Not mirrored:** androidx `BorderModifierNode` also sets the semantics `shape` (`SemanticsPropertiesKt.setShape`);
   no pixel depends on it. `shouldAutoInvalidate` is left at the default (the cache is invalidated only on a real input
   change, as androidx's setters do).
6. **Stale comment outside the lane (read-only file):** `color/ColorApplier.kt` kdoc of `applyColors` ("border draws sit
   OUTSIDE the alpha layer … left as future work") is no longer true on the legacy route after U2 — for the closing docs
   pass (ColorApplier.kt is read-only for L6).

## House rules

- New logic only in the NEW file `BehindContentBorder.kt` (200 lines, every code line commented with the bytecode
  branch / CSS section it mirrors). Final sizes: `BorderRadiusApplier.kt` 281 → 284; `BorderSideApplier.kt` 636 → 647
  (defaulted parameter + one-line fast-path condition + the one-line slow-path dispatch, see deviation 3);
  `BordersFacade.kt` 129 → 149; `StyleApplier.kt` 1188 → 1191 (call-site lines + 2 comment lines);
  `BorderRadiusSelfPaintTest.kt` 249 → 257. "runtime", not "engine". No temporary-directory pointer in any delivered file (the TREES line is exempt).
- No silent fallthrough: every outline kind androidx handles is handled (rect / rounded simple + path / generic), and an
  invalid width or empty box paints content only, as androidx does.

## Census (lane L6's own, behavioural) — `census.sh` + `L6ChainCensusTest.kt.txt` + `census.py` → `census.out.txt`

Method: a JVM census that runs Compose's OWN `StyleApplier.extractConfig → applyConfig` (wptCaptureMode true) over
every component of all 1435 verbatim per-test IR documents of `wave54-final` (10 719 components), on three trees of the
lane worktree — base 611c9f93, U1 (+U3; U2 reverted) and full — and prints the route flags and the folded chain's
element-class signature (`census-{base,u1,full}.tsv.gz`). `census.py` classifies the changed components by what can paint
differently once the band's order changes (children, own text / runs, pseudos, markers, opacity group, an outline with a
negative offset) and cross-checks the document set against `expectations.json` lanes["L6-android-paint-order"].

Results (`census.out.txt`):
- Bordered components under Compose's own side resolution: **1973** (10 self-paint + 1963 legacy = 1494 fast + 469 slow)
  — reproduces the plan's D21 count exactly. 4 components (conic-gradient-line-height-relative-units-003 / -004) throw in
  the JVM chain on all three trees alike.
- U1 changes exactly the **10** self-paint bands (all `RoundedCornerShape` → androidx's rounded branch; **0** generic);
  U2 exactly the **1963** legacy bands (1865 by element signature + 98 slow-path ones whose `DrawWithContentElement` keeps
  its class). **0** changed chains outside a band.
- Exposed documents: U1 3 (clip-rect, clip-rect-zoom, clip-path-contentBox-1e — the last one is clipped to its content
  box by its own clip-path, so its band never shows: identity), U2 267; union 270.
- Cross-check: **269 of 270 inside** carriers (27, all covered) ∪ mayMove (240) ∪ sharedStems (12) ∪ ring-fence (1).
- **GROWTH: 1 capture → a plan regeneration need** (not hidden): `wpt__css-display__display-contents-dynamic-pseudo-insertion-001`
  android (unscored; nobody's carrier). Its `Display: CONTENTS` box keeps its box on Compose because it carries pseudos
  (`ContentsUnboxing.isUnboxable` → false), so its 10-px red legacy band is painted, with the `::after` "SS" text inside.
  Looked at (Android capture): the band ring is 7340 pure-red px and the text ink starts 1 px inside the content box —
  predicted identical (MED; the band was on top, so an overlap is invisible today). Because it is outside `mayMove`, a
  move would read as an R4 LEAK at stage 2 (css-display is a stage-2 section, outside the `drop-L6-U2` arm's sections):
  **regenerate with this stem added to L6 `mayMove` by rule** ("a kept display:contents box with pseudos").
- Plan stems with no exposed changed component: 10 (conservative over-coverage, e.g. the 27 display:contents bands the
  renderer strips — `ContentsUnboxing.resolve` keeps only inherited declarations).
- **Opacity groups (beyond D2):** 49 opacity-bordered components in 2 documents under Compose's resolution, not 1:
  basic-blur (ring-fenced, D2) and **`css-anchor-position/anchor-center-overflow-003`** (48 boxes at `opacity: 20%` with
  style-only 3-dp bands; unscored, `scoreEligible false`; in mayMove via D21). U2 moves those bands INTO the 20 % group:
  a visible fade toward the ref, attributed by the `drop-L6-U2` arm (css-anchor-position is in its sections). The
  mayMoveRule's "a band now under its child" does not describe this picture; the explanation is "the band now inside the
  element's opacity group" (css-color-4 §3.3) — the orchestrator should accept that wording for this stem.
- Outlines on the routes: 8, all with offset ≥ 0 (`OutlineApplier.drawStrokeRing` paints a plain rect OUTSIDE the
  border box), so the outline-after-band order flip touches no band pixel. One negative-`z-index` child of a banded
  parent (anchor-position-multicol-002): the parent has a transform (a stacking context, so the child paints above its
  border anyway) and a transparent band — no effect.
- No fixture-net exposure beyond the brief's scan: since wave 51 the component-name label is harness chrome drawn over
  the capture root (ComponentRenderer `PlaceholderContent` returns before painting any synthesized name), so the 8
  bordered visual-test components paint nothing inside their bands.

## Looks (PNGs opened before and after reasoning)

- clip-rect (ref / web / iOS / Android, rows 150–230 × x140–290 at 3×): ref, web and iOS paint the red `.menu` /
  `.menu2` edges over the navbar's blue band; Android runs the blue band across them. Inversion, rounded ends identical.
- flex safe-001 (2×): ref / web / iOS paint the yellow dotted-purple abspos children over the black 3-px `.flex` band;
  Android draws the black square outline through every child (yellow fill visibly cut).
- display-contents-dynamic-pseudo-insertion-001 (2×): see the census growth item.

## Geometry probe self-test

`/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes L6 --self-test`
→ exit 0, "self-test: HOLDS — 0 violation(s)": 37 keys — gating 7 FAIL, report 1 FAIL, control 29 PASS
(`geometry-selftest.wave54-final.txt`).

## Pins (verbatim wires: tools/titan/runs/wave54-final/sections/*/per-test-ir/)

| pin | asserts | mutation(s) executed (log below) |
|---|---|---|
| P1a | `drawInOrder` runs band, then content (recorders) | M1 swap the two calls → red |
| P1b | `applySelfPaint(navbar 10/20/30/40, 2px solid blue)` installs one `BehindContentBorderElement` (2.dp, SolidColor(Blue), the radius Shape) and no `BorderModifierNodeElement` | M2 restore `result.border(` at :210 of the U1-state file (pre 3ad5a63a…; HEAD's `result.border(` is :207) → red (+ 2 BorderRadiusSelfPaintTest tests) |
| P1c | the node's only banded draw is `drawInOrder(band = { band() }, content = { drawContent() })`; 2 `drawContent()` sites | M5 node draws `drawContent(); band()` → red |
| P2a–e | the androidx band arithmetic: 2px on 304×54 → (1,1)/(302,52)/Stroke; 1.5 → ceil 2; hairline 1; 51×51 fill / 50×50 stroke; invalid → no band; inner corners 8/18/28/38 and `max(0, 1 − 2) = 0` | M3 drop the `ceil` → P2b red; M4 drop the `max(0, …)` clamp → P2e red |
| P3a–d | on the verbatim flex safe-001 `.flex` wire (`…__0-023`) and the DOTTED background-clip-002 wire (`…__2-095`) through `StyleApplier.extractConfig → applyConfig`: the band is ours and sits inner of the size frame / AFTER the Background element / inside the opacity group; the DOTTED slow-path `DrawWithContentElement` AFTER `BackgroundElement` | M6 revert the StyleApplier hunk → P3a, b, c, d red |
| P3e–f | `BordersFacade.apply` default unchanged (androidx node); opt-in installs only the behind-content band on `RectangleShape` | (controls) |
| P3g | the slow path's dispatch line is band-first through `drawInOrder`; 2 `drawContent()` sites | M9 swap the lambdas → red |
| P4a–b | clip-path layer OUTER of the drop-shadow `DrawBehind` (EffectsFacade and the full chain) | M7 move `applyClipPath` below `applyForegroundFilters` → both red; M8 `"drop-shadow"` branch returns null → both red |

Mutation log (bracketed; `l6-mut.sh` with the snippets in `mutations/`; each MUTATING line was written BEFORE the edit,
each RESTORED sha256 equals its pre sha256; M1 was run twice, the first run's reporter did not print test names):

(the bracket lines: each mutation record before the edit, its restore record after the byte-exact restore; each followed by its focused run)

MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M1-P1a-swap-drawInOrder; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M1-P1a-swap-drawInOrder mutated  → FAILURE: Build failed with an exception. gradle rc=0 FAIL TEST-com.styleconverter.runtime.borders.BehindContentBorderTest tests=8 failures=1 errors=0 SUMMARY suites=1 tests=8 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M1-P1a-swap-drawInOrder; post == pre: yes]
  M1-P1a-swap-drawInOrder restored → gradle rc=0 SUMMARY suites=1 tests=8 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M1-P1a-swap-drawInOrder; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M1-P1a-swap-drawInOrder mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P1a - drawInOrder paints the band, then the content SUMMARY suites=1 tests=8 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M1-P1a-swap-drawInOrder; post == pre: yes]
  M1-P1a-swap-drawInOrder restored → gradle rc=0 SUMMARY suites=1 tests=8 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/radius/BorderRadiusApplier.kt sha256 3ad5a63a2f652bd01ae0e2bd0825c1592c4f3a686f475b747e22c03892503236   [M2-P1b-restore-androidx-border; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M2-P1b-restore-androidx-border mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P1b - self-paint installs BehindContentBorderElement and no androidx border node FAIL BorderRadiusSelfPaintTest :: self-paint installs shaped background and border and NO clip layer FAIL BorderRadiusSelfPaintTest :: self-paint without background installs only the border band SUMMARY suites=2 tests=19 failed=3 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/radius/BorderRadiusApplier.kt sha256 3ad5a63a2f652bd01ae0e2bd0825c1592c4f3a686f475b747e22c03892503236   [M2-P1b-restore-androidx-border; post == pre: yes]
  M2-P1b-restore-androidx-border restored → gradle rc=0 SUMMARY suites=2 tests=19 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M3-P2-drop-ceil; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M3-P2-drop-ceil mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P2b - fractional widths round UP, as androidx ceil does SUMMARY suites=1 tests=8 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M3-P2-drop-ceil; post == pre: yes]
  M3-P2-drop-ceil restored → gradle rc=0 SUMMARY suites=1 tests=8 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M4-P2-drop-max0-clamp; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M4-P2-drop-max0-clamp mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P2e - inner corners shrink by the stroke and floor at 0 SUMMARY suites=1 tests=8 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M4-P2-drop-max0-clamp; post == pre: yes]
  M4-P2-drop-max0-clamp restored → gradle rc=0 SUMMARY suites=1 tests=8 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M5-P1c-node-bypasses-drawInOrder; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M5-P1c-node-bypasses-drawInOrder mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P1c - the node draws only through drawInOrder, band first SUMMARY suites=1 tests=8 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [M5-P1c-node-bypasses-drawInOrder; post == pre: yes]
  M5-P1c-node-bypasses-drawInOrder restored → gradle rc=0 SUMMARY suites=1 tests=8 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 5a8c9da1ab86bb94d5b0a936f06ef7050789a6e7b561fe75157894dbaeff28c4   [M6-P3-revert-StyleApplier-hunk; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M6-P3-revert-StyleApplier-hunk mutated  → gradle rc=1 FAIL BorderSidesBehindContentTest :: P3b - with a background the fast-path band sits AFTER (inner of) the Background element FAIL BorderSidesBehindContentTest :: P3a - the verbatim flex wire chains the band INNER of the background-less colors step FAIL BorderSidesBehindContentTest :: P3d - under opacity the band joins the element's group (css-color-4 §3_3) FAIL BorderSidesBehindContentTest :: P3c - the verbatim DOTTED wire's slow-path draw node sits AFTER the Background element SUMMARY suites=1 tests=7 failed=4 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 5a8c9da1ab86bb94d5b0a936f06ef7050789a6e7b561fe75157894dbaeff28c4   [M6-P3-revert-StyleApplier-hunk; post == pre: yes]
  M6-P3-revert-StyleApplier-hunk restored → gradle rc=0 SUMMARY suites=1 tests=7 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt sha256 cda285c3b08270824bc056146f828ae81204ceb3be35d5c30bcba5779f5be04c   [M9-P3g-swap-slow-path-order; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M9-P3g-swap-slow-path-order mutated  → gradle rc=1 FAIL BorderSidesBehindContentTest :: P3g - source - the slow path paints the sides first through drawInOrder SUMMARY suites=1 tests=7 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt sha256 cda285c3b08270824bc056146f828ae81204ceb3be35d5c30bcba5779f5be04c   [M9-P3g-swap-slow-path-order; post == pre: yes]
  M9-P3g-swap-slow-path-order restored → gradle rc=0 SUMMARY suites=1 tests=7 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt sha256 c2d5f2c9330e0f9867940daac7013aea32177b84d27a861445ca4167a477ae4a   [M7-P4-clip-inner-of-filter; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M7-P4-clip-inner-of-filter mutated  → gradle rc=1 FAIL EffectsClipFilterOrderTest :: P4b - the full StyleApplier chain keeps that order FAIL EffectsClipFilterOrderTest :: P4a - EffectsFacade installs the clip outer of the drop-shadow SUMMARY suites=1 tests=2 failed=2 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt sha256 c2d5f2c9330e0f9867940daac7013aea32177b84d27a861445ca4167a477ae4a   [M7-P4-clip-inner-of-filter; post == pre: yes]
  M7-P4-clip-inner-of-filter restored → gradle rc=0 SUMMARY suites=1 tests=2 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/effects/filter/FilterExtractor.kt sha256 21af6aa54ce44d973b21c55c3c7a24ede1580b181461a8f4360b4a0dcb076fda   [M8-P4-drop-shadow-dropped; byte-exact copy in the TREES worktree under .l6-mut-backup/]
  M8-P4-drop-shadow-dropped mutated  → gradle rc=1 FAIL EffectsClipFilterOrderTest :: P4b - the full StyleApplier chain keeps that order FAIL EffectsClipFilterOrderTest :: P4a - EffectsFacade installs the clip outer of the drop-shadow SUMMARY suites=1 tests=2 failed=2 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/effects/filter/FilterExtractor.kt sha256 21af6aa54ce44d973b21c55c3c7a24ede1580b181461a8f4360b4a0dcb076fda   [M8-P4-drop-shadow-dropped; post == pre: yes]
  M8-P4-drop-shadow-dropped restored → gradle rc=0 SUMMARY suites=1 tests=2 failed=0 

All mutated files were restored byte-exact; `EffectsFacade.kt` / `FilterExtractor.kt` (read-only for L6) equal the
shared tree's HEAD bytes (c2d5f2c9… / 21af6aa5…).

## revertStatesCompiled

Each state built from a clean 611c9f93 checkout of the lane's paths in the worktree + `git apply` of the kept unit
patches (`l6-state.sh`), then `:runtime:compileDebugKotlin` + `:runtime:compileDebugUnitTestKotlin` and the focused
`:runtime:testDebugUnitTest` filters `*BehindContentBorder* *BorderRadiusSelfPaint* *BorderSidesBehindContent*
*EffectsClipFilterOrder* *BorderSide* *BordersFacade* *Border* *Backface* *StyleApplier* *Opacity* *Effects* *Filter*
*Backdrop* *Color* *Outline* *Shadow*`:

revertStatesCompiled: U3 reverted (U3 chain [U3]) (kept: U1 U2): compile rc=0; focused :runtime suites → SUMMARY suites=76 tests=608 failed=0
revertStatesCompiled: U2 reverted (U2 chain [U2] = U1 chain step 1) (kept: U1 U3): compile rc=0; focused :runtime suites → SUMMARY suites=76 tests=603 failed=0
revertStatesCompiled: U2 then U1 reverted (U1 chain [U2, U1] end) (kept: U3): compile rc=0; focused :runtime suites → SUMMARY suites=75 tests=595 failed=0
revertStatesCompiled: U2 and U3 reverted (combined) (kept: U1): compile rc=0; focused :runtime suites → SUMMARY suites=75 tests=601 failed=0
revertStatesCompiled: base 611c9f93 (no L6 unit; census base) (kept: none): compile rc=0; focused :runtime suites → SUMMARY suites=74 tests=593 failed=0
revertStatesCompiled: full (U1 U2 U3, the final restore) (kept: U1 U2 U3): compile rc=0; focused :runtime suites → SUMMARY suites=77 tests=610 failed=0

Final state of the worktree: U1 + U2 + U3 applied (the re-cut patches are byte-identical to the delivered ones);
`git apply --check unit-U1.patch unit-U2.patch unit-U3.patch` holds on the shared tree's HEAD (check only).

## Predictions (from wave54-final; replays are SCORE predictions, never picture labels)

| cell | from → to | confidence | key |
|---|---|---|---|
| `filter-effects/backdrop-filter-clip-rect.html android` | P 0.9559 → P ≈0.957 | HIGH stays P; picture MED-HIGH | `[PO] … android` GEOMETRY OK (overlap rows red 416 / blue 0, red px 1552) |
| `filter-effects/backdrop-filter-clip-rect-zoom.html android` (unscored) | overlap red 416 / blue 0 | MED-HIGH | `[PO] … -zoom android` report |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001.html android` | P 0.9619 → P ≈0.994 | MED-HIGH | `[X]` GEOMETRY OK (crossings 0) |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002.html android` | P 0.9635 → P ≥0.991 (replay lower bound) | MED-HIGH | `[X]` GEOMETRY OK |
| `css-grid/abspos/grid-abspos-staticpos-{align-self-001, justify-self-001, align-self-vertWM-001, align-self-rtl-001}.html android` | P 0.9673 / 0.9667 / 0.9612 / 0.9646 → flat (±0.0005), stay P | MED | `[X]` GEOMETRY OK |
| the other 15 grid / flex U2 carriers | wave54-final value ±0.0005, stay P | MED | — |
| `css-grid/abspos/grid-abspos-staticpos-align-self-safe-001.html android` | f 0.9441 → f ≈0.9448 | LOW | — |
| `css-images/gradient/gradient-powerless-hue-{hsl,hwb,lch}.html android` | f, +0.0002–0.0003 | LOW | — |
| `css-masking/clip-path/clip-path-filter-order.html` web / ios / android | byte-identical (no band) | HIGH | `[FO]` controls OK |
| the 10 `[ID]` rows (android) | decoded-pixel identical | MED-HIGH (raster parity, bytecode-mirrored) | `[ID]` controls |
| every web / iOS capture | untouched (Compose-only) | HIGH | control-check |
| `filter-effects/backdrop-filter-basic-blur.html android` (ring-fenced) | undirected mover (band into the 0.6 group), report plainly | — | — |
| `css-anchor-position/anchor-center-overflow-003` android (unscored, mayMove) | visible change: 48 bands into their 20 % groups (toward the ref) | MED | drop-L6-U2 arm |
| `css-display/display-contents-dynamic-pseudo-insertion-001` android (unscored, census growth) | identical | MED | control-check (needs the mayMove regeneration above) |

Flips 0 · lost 0 · newly measured / unmeasured-now 0.

## ORCHESTRATOR WINDOW REQUESTS

Every device run starts with `beforeEveryDeviceRun` (`expectations.json`; PLAN §7) from the shared tree root. L6 needs
no window of its own before stage 1.

1. **Stage 1 `wave55-pre`** (decides U1 and U2): `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net`, then L6's reads:
   - `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/android-paint-order.geometry.py wave55-pre --base wave55-open`
     → expect `[PO] backdrop-filter-clip-rect android … → GEOMETRY OK` and the six `[X] … android` GEOMETRY OK; every
     `[ID]` / `[FO]` / web / iOS control OK; `[PO] … -zoom android` OK (report).
   - `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave55-pre --base wave55-open --lanes L6 --sections filter-effects,css-masking,css-flexbox,css-grid`
     → exit 0 (every L6 gating key PASS).
   - `node tools/titan/results/wave55-gate/control-check.mjs wave55-open wave55-pre --lane L6-android-paint-order --sections filter-effects,css-masking,css-flexbox,css-grid`
     → web and iOS byte-identical; Android changed ⊆ union carriers ∪ L6 mayMove (basic-blur printed RING-FENCED).
   - Decision (pre-registered, `stage1Decision`): an `[X]` gating WRONG, an L6-caused U2 `[ID]` WRONG (clip-radius-zoom,
     grid align-items-center, flex safe-003), a bisected Android mover outside carriers ∪ mayMove, or a mayMove
     non-carrier |Δ| > 0.002 whose picture is not a band now under its child (or, for anchor-center-overflow-003, inside
     its opacity group) → revert U2. `[PO] … android` WRONG or a U1 `[ID]` WRONG (clip-path-*Box-1e, clip-rect-2) → revert
     U2 then U1. Open the clip-rect and flex safe-001 Android PNGs next to the ref before any label.
2. **Stage 2 `wave55-probe`**: reads the `[ID]` rows outside stage 1 (borders-008, background-clip-002, s-11-1-1b-001) and
   U2's 3 css-images carriers; `control-check.mjs wave55-open wave55-probe --sections …` must list
   display-contents-dynamic-pseudo-insertion-001 android as unchanged (or as may-move once the regeneration above adds it).
3. **Closing arm `drop-L6-U2`** (after `wave55-final`): `bash tools/titan/results/wave55-gate/ab-arms55.sh <arms-file> --run --against wave55-final`
   with the arm line `drop-L6-U2 css-anchor-position,css-flexbox,css-gaps,css-grid,css-images,css-position,filter-effects android <U2-S1… shas, then the U2 sha>`;
   read `node tools/titan/results/wave52-gate/ab-diff.mjs wave55-ab-drop-L6-U2 wave55-final --threshold 0.002 --platforms android`.
   Expect: the 25 U2 carriers return to their wave55-open crossings; clip-rect / -zoom identical (U1 stays); basic-blur
   and anchor-center-overflow-003 show the band-into-group move (report only).
4. **[W1] evidence only** (D11: after the closing gate's two launches, before the ship), on the current tree:
   `SKIP_IOS=1 ./test-all.sh fixtures/properties/effects/filter-functions.json`; read `Filter_DropShadow_NoBlur` (160×80
   `#ecf0f1`, `drop-shadow(4px 4px #111)`) on Android: count #111-class pixels (channel sum < 80) in the 4-px strips right of
   and below the box bbox (nominal 944). ≥ 900 → 0(k)(2) closes as "order right by construction (U3), shadow renders on
   device"; 0 → a next-wave unit (radius: 3 components); frame cropped at the box → UNMEASURABLE. Afterwards record
   `shasum -a 1 apps/ios-harness/StyleConverterTest/Resources/tmpOutput.json apps/android-harness/app/src/main/assets/tmpOutput.json`
   in `../wave55-gate/_note.md` (PLAN §8 step 16).

## Not verified (honestly)

- No device run: every pixel claim above is a prediction. Raster parity of the behind-content band with androidx's band is
  argued from the bytecode (same calls, same coordinates, same canvas state) and pinned only arithmetically (P2); the
  `[ID]` keys at stage 1 / stage 2 are its only device proof. The generic (elliptical / %-radius) branch and U2's slow
  path are device-unexercised by any corpus carrier.
- `BehindContentBorderNode`'s attach / invalidation lifecycle (CacheDrawModifierNode delegate) cannot run in the plain
  JVM suite (android.graphics is stubbed); it mirrors androidx `BorderModifierNode`'s own structure.
- The census reads each component's own declarations through Compose's real extractors, not the renderer's full
  cascade (UA rules, inheritance merges); the display:contents rule was mirrored from `ContentsUnboxing.isUnboxable`.
- Not run: `:app:testDebugUnitTest`, any full suite, any SwiftUI / web suite (Compose-only lane). Run read-only on the
  shared tree: `land-units.sh --check --only L6` → "0 error(s), 1 warning(s) — landable" (the warning: the note was
  written < 120 s before the check), the cumulative apply and the three revert dry-runs ok.

builder cut (superseded by fix-r1, see the end of this note): unit-U1.patch sha256 27dd5bc2925068908fa1896081beb1cd1440dbebd4d2c4bf4ff4bc39d03b2ef2
builder cut (superseded by fix-r1, see the end of this note): unit-U2.patch sha256 b10eae9755d349f91c47532c4a77094704efa3113688ce0b7ccb68518871cd58
builder cut (superseded by fix-r1, see the end of this note): unit-U3.patch sha256 7bf15926c58a49438c67401fd9e69eee335e8073ed34bb975fbed01b70df383f

STATUS: COMPLETE

STATUS: PARTIAL — fix-r1 in progress

## Fix pass fix-r1 — execution log (the prose summary follows below this log)

Fix worktree: the `TREES:` line naming `wave55-fix-L6` (the builder's `wave55-build-L6` line stays as history). Builder patches applied there first
(U1 → U2 → U3, all clean on 611c9f93; sha256 27dd5bc2… / b10eae97… / 7bf15926…), focused run 4 classes 28 tests 0 failed.

### Repro of skeptic D2 on the builder's patches (pre-fix; expected GREEN = the defect)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X1; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 pre-X1 mutated  → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X1; post == pre: yes]
  fix-r1 pre-X1 restored → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X2; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 pre-X2 mutated  → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X2; post == pre: yes]
  fix-r1 pre-X2 restored → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X3; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 pre-X3 mutated  → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X3; post == pre: yes]
  fix-r1 pre-X3 restored → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X4; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 pre-X4 mutated  → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X4; post == pre: yes]
  fix-r1 pre-X4 restored → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X7; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 pre-X7 mutated  → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 a0f6cf5b20833d6606568ceb1ffee5d81ef1cbe9069ea636aa2ec38609eed967   [fix-r1 pre-X7; post == pre: yes]
  fix-r1 pre-X7 restored → gradle rc=0 SUMMARY suites=4 tests=28 failed=0 

### Post-fix pin mutations (expected RED, then GREEN after the byte-exact restore)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X1; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-X1 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P5b - rectBand fills the whole box from the origin when the bands meet SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X1; post == pre: yes]
  fix-r1 post-X1 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X2; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-X2 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P5a - rectBand strokes the half-inset rect with the ceil'd width SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X2; post == pre: yes]
  fix-r1 post-X2 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X3; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-X3 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P5c - a simple radius at exactly half the stroke takes the shrunk-stroke branch SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X3; post == pre: yes]
  fix-r1 post-X3 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X4; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-X4 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P5e - the node drops its cached band on an input change, and only then SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X4; post == pre: yes]
  fix-r1 post-X4 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X7; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-X7 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P5f - source - the generic branch tints its alpha mask with the OPAQUE colour SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-X7; post == pre: yes]
  fix-r1 post-X7 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M1; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M1 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P1a - drawInOrder paints the band, then the content SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M1; post == pre: yes]
  fix-r1 post-M1 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M3; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M3 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P2b - fractional widths round UP, as androidx ceil does SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M3; post == pre: yes]
  fix-r1 post-M3 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M4; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M4 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P2e - inner corners shrink by the stroke and floor at 0 SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M4; post == pre: yes]
  fix-r1 post-M4 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M5; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M5 mutated  → gradle rc=1 FAIL BehindContentBorderTest :: P1c - the node draws only through drawInOrder, band first SUMMARY suites=4 tests=36 failed=1 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt sha256 c67accaba51aa850a280589905c5f46960a0379ce1253747c071bef760dbe0cd   [fix-r1 post-M5; post == pre: yes]
  fix-r1 post-M5 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r1 post-M6; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M6 mutated  → gradle rc=1 FAIL BorderSidesBehindContentTest :: P3b - with a background the fast-path band sits AFTER (inner of) the Background element FAIL BorderSidesBehindContentTest :: P3a - the verbatim flex wire chains the band INNER of the background-less colors step FAIL BorderSidesBehindContentTest :: P3d - under opacity the band joins the element's group (css-color-4 §3_3) FAIL BorderSidesBehindContentTest :: P3c - the verbatim DOTTED wire's slow-path draw node sits AFTER the Background element SUMMARY suites=4 tests=36 failed=4 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r1 post-M6; post == pre: yes]
  fix-r1 post-M6 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r1 post-MD1; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-MD1 mutated  → gradle rc=1 FAIL BorderSidesBehindContentTest :: P3i - a clipping SOLID box keeps androidx's band (drawn after its clipped content) FAIL BorderSidesBehindContentTest :: P3h - a clipping INSET input keeps its slow-path band outer of the background SUMMARY suites=4 tests=36 failed=2 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r1 post-MD1; post == pre: yes]
  fix-r1 post-MD1 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt sha256 c2d5f2c9330e0f9867940daac7013aea32177b84d27a861445ca4167a477ae4a   [fix-r1 post-M7; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M7 mutated  → gradle rc=1 FAIL EffectsClipFilterOrderTest :: P4b - the full StyleApplier chain keeps that order FAIL EffectsClipFilterOrderTest :: P4a - EffectsFacade installs the clip outer of the drop-shadow SUMMARY suites=1 tests=2 failed=2 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/effects/EffectsFacade.kt sha256 c2d5f2c9330e0f9867940daac7013aea32177b84d27a861445ca4167a477ae4a   [fix-r1 post-M7; post == pre: yes]
  fix-r1 post-M7 restored → gradle rc=0 SUMMARY suites=1 tests=2 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/effects/filter/FilterExtractor.kt sha256 21af6aa54ce44d973b21c55c3c7a24ede1580b181461a8f4360b4a0dcb076fda   [fix-r1 post-M8; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]
  fix-r1 post-M8 mutated  → gradle rc=1 FAIL EffectsClipFilterOrderTest :: P4b - the full StyleApplier chain keeps that order FAIL EffectsClipFilterOrderTest :: P4a - EffectsFacade installs the clip outer of the drop-shadow SUMMARY suites=1 tests=2 failed=2 
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/effects/filter/FilterExtractor.kt sha256 21af6aa54ce44d973b21c55c3c7a24ede1580b181461a8f4360b4a0dcb076fda   [fix-r1 post-M8; post == pre: yes]
  fix-r1 post-M8 restored → gradle rc=0 SUMMARY suites=1 tests=2 failed=0 
fix-r1 WIP: the fix patches were saved under fix-r1/wip/ before every state switch of the census; removed after the final re-cut matched them byte for byte.

## Fix pass fix-r1 — defect → action, outputs

Skeptic report: `skeptic.md` (D1 must-fix, D2 should-fix, D3–D6 nits). Each check was re-run first in the fix worktree on
the builder's patches: D2's five mutations X1 / X2 / X3 / X4 / X7 stay GREEN (log above, "pre-"), and the census
reproduces D1's GROWTH 4 exactly (below, builder tree). Everything below is executed; nothing ran on a device.

### D1 (must-fix, census under-count) → fixed IN CODE (unit U2), so the plan does not have to grow for it

- **Look first** (ref / iOS / Android today / the skeptic's U2 replay, rows 0–60 at 4×): the ref draws a thin frame on
  all four sides; Android today draws the dark top / left and grey right / bottom INSET band with the replica's hairline
  inside; the U2 replay loses the right and bottom band under the replica's white fill. The input carries
  `OverflowX/OverflowY: CLIP` on the wire (`…__0-077`, `first-line-and-placeholder__0__0-199`).
- **Root cause, wider than the widget.** css-overflow-3 §3 clips the content of a non-`visible` box at its PADDING edge,
  so in CSS clipped content never reaches the band. Compose clips at step 7 (`OverflowApplier.applyOverflow` →
  `clip(RectangleShape)`), which sits OUTER of the step-8 padding, i.e. at the BORDER box. On a clipping box the
  pre-wave-55 order (band drawn after the clipped content) is therefore the faithful one; U2's behind-content band let
  any clipped content that reaches the band area paint over it — the UA-widget replica (a 153×21 canvas from the content
  origin) is one case, the scrolled / clipped children of the background-attachment-local scrollers another.
- **Change** (`StyleApplier.applyConfig`, legacy branch — the same three call-site lines):
  `BordersFacade.apply(result, paint.borders, sidesBehindContent = !config.overflow.shouldClip)`; `ColorApplier` unchanged;
  `if (!config.overflow.shouldClip) result = BordersFacade.applySidesBehindContent(result, paint.borders)` (+3 comment
  lines). `config.overflow.shouldClip` is exactly the predicate the self-paint gate already passes as `overflowClips`, so
  U1 and U2 now agree: a band paints behind content only on a box that does not clip; a clipping box keeps the base chain.
  `BordersFacade.applySidesBehindContent` kdoc says so. BackfaceCullChainTest W4's regex still counts 5 `paint.` decoration
  calls and 0 `config.` ones (the predicate is an argument, not the call's second parameter; the focused run includes it).
- **Census** (`fix-r1/census.sh` + `fix-r1/L6FixCensusTest.kt.txt` + `fix-r1/census.py` → `fix-r1/census.out.txt`; TSVs
  `fix-r1/census-{base,builder,fix}.tsv.gz`): Compose's own extractConfig → applyConfig over all 10 719 components of the
  1435 verbatim wave54-final per-test IR documents, on three trees of the fix worktree (base 611c9f93, the builder's
  patches, the fix's patches). Movers are BEHAVIOURAL (every band on a route whose order changed — a slow-path band can
  keep its class signature), exposure is the skeptic's predicate with nothing subtracted.
  - Builder tree: 283 exposed documents, 279 inside carriers ∪ mayMove ∪ sharedStems ∪ ring, **GROWTH 4** —
    revert-layer-015, first-line-and-placeholder, gradient-hue-direction (`<hr>`), display-contents-dynamic-pseudo-insertion-001:
    the skeptic's numbers exactly.
  - Fix tree: 261 exposed, 260 inside, **GROWTH 1** — display-contents-dynamic-pseudo-insertion-001 only (the builder's own
    pre-existing item: an unclipped `display: contents` box kept for its pseudos; predicted identical, see below).
  - 29 legacy bands sit on a clipping box (26 documents); all 29 keep the base chain byte-for-byte (class signature), 18 of
    them had moved under the builder. Builder ≠ fix on exactly those 18 rows; every other mover's chain equals the builder's.
  - 22 documents leave the exposed set: the 3 OUTSIDE stems above (revert-layer-015, first-line-and-placeholder,
    gradient-hue-direction — its `<hr>` carries the UA `overflow: hidden`) and 19 mayMove stems (anchor-scope-scroll, the
    12 attachment-local-clipping-color/image-1…6, attachment-local-positioning-2/3/4, all-prop-001,
    display-contents-suppression-dynamic-001, backdrop-filter-edge-behavior): may-move over-coverage, harmless.
  - Carriers: every revert unit's captures keep an exposed mover on their unit's route (U1 2/2, U2 25/25). None of the 29
    withdrawn bands is in a carrier document or in any `[ID]` / `[FO]` / `[PO]` / `[X]` key document.
  - UA-widget tags with a legacy band: 2 `<input>` + 1 `<input type=text>` + 1 `<textarea>`, all clipping → no longer move;
    2 `<button>` move (display-contents-button — unboxed by `display: contents`; flex-gap-decorations-023 — see below).
  - Opacity-group movers unchanged: 49 components in 2 documents (basic-blur, ring-fenced; anchor-center-overflow-003).
- **The one widget host that still moves**: css-gaps/flex/flex-gap-decorations-023 `<button>` `…__2-182` (OUTSET 2px black,
  padding 1/6, `overflow: visible`, in L6 mayMove). Its BUTTON replica (108.6×21, the whole native border box) is drawn
  from the content origin and overflows right / down past the band (a UAWidgets placement defect, not L6's). Looked at
  (ref / iOS / Android today / replay, 5×): under U2 the replica's own frame covers the band's right and bottom portion.
  Replay (`fix-r1/widget-replay.py` + `widget-replay-score.mjs` → `fix-r1/widget-replay.out.txt`, the gate's diffWebVsRef):
  **0.9541 → 0.9548 (Δ +0.0007)**, 234 band px — stays P, inside R5's 0.002, never claimed (picture: "the widget replica
  now over the band", DEGENERATE-labelled).
- **Pins** (verbatim `properties` arrays): **P3h** revert-layer-015 `…__0-077` → no `BehindContentBorderElement`, one
  `DrawWithContentElement` OUTER of (before) `BackgroundElement`; **P3i** anchor-scope-scroll `…__0-781` (1px SOLID,
  `overflow-y: hidden`) → one androidx `BorderModifierNodeElement`, no behind-content band. Mutation **MD1** (the builder's
  unconditional lines back) → P3h + P3i red; restored → green (log above). M6 (the whole hunk reverted) → P3a–d red.

### D2 (should-fix, painters / node unpinned) → fixed (test-side recorder; production change is visibility + kdoc only)

- `BehindContentBorder.kt`: `rectBand` / `roundedBand` `private` → `internal` (bodies byte-identical), and one-line kdocs on
  `bandGeometry` / `rectBand` / `roundedBand` / `genericBand` written into former blank lines (file stays at 200 lines; D4).
- `BehindContentBorderTest.kt` P5: a `java.lang.reflect.Proxy` DrawScope / DrawContext / DrawTransform / Canvas records
  each draw / clip call with its unpacked arguments (packed Offset / Size / CornerRadius longs, DrawStyle, alpha, filter,
  blend mode); an unexpected scope call throws. **P5a** rect stroke (`drawRect (1,1) (302,52) Stroke(2) … b=3`), **P5b**
  rect fill from the origin, **P5c** simple radius exactly half the stroke → shrunk stroke (androidx's strict `<`), **P5d**
  radius under half → `clipRect 2 2 98 48 op=0` + full rounded fill, and the meeting-bands rounded fill, **P5e** the node
  drops its cache (`CacheDrawModifierNodeImpl.isCacheValid`, androidx 1.11.4) on a width / brush / shape change and keeps
  it on equal inputs, **P5f** (source pin) the generic branch's opaque tint and Alpha8 / Argb8888 mask config.
- Mutations X1 → P5b red, X2 → P5a red, X3 → P5c red, X4 → P5e red, X7 → P5f red; each restored byte-exact → green (log
  above). Still JVM-unpinnable: the rounded PATH branch and `genericBand`'s raster (android.graphics.Path / Bitmap are stubs);
  the stage-1 / stage-2 `[ID]` keys stay their only raster proof (no corpus carrier exercises them).

### Nits

- D3 → fixed: P3's flex safe-001 `…__0-023` and DOTTED background-clip-002 `…__2-095` wires are now the verbatim
  `properties` arrays (wire order), parsed by a `wire()` helper.
- D4 → fixed (above). D5 → fixed: P4b calls `applyConfig(…, wptCaptureMode = true)`; M7 / M8 re-executed, both red → green.
- D6 → not touched (`ColorApplier.kt` is read-only for L6; the closing docs pass owns its kdoc).

### Builder mutations re-executed on the new bytes (log above)

M1 → P1a, M3 → P2b, M4 → P2e, M5 → P1c (BehindContentBorder.kt pre c67accab…); M6 → P3a–d, MD1 → P3h–i (StyleApplier.kt
pre e0b379be…); M7 / M8 → P4a–b (EffectsFacade.kt c2d5f2c9… / FilterExtractor.kt 21af6aa5…, = the shared HEAD bytes). M2
(BorderRadiusApplier.kt) and M9 (BorderSideApplier.kt) were not re-run: neither file nor its pins changed in fix-r1.
Every MUTATING line has its RESTORED pair with post == pre.

### Units after fix-r1 (paths unchanged from the builder's sections)

- **U1 PO-selfpaint-android** (`unit-U1.patch`): `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt` (NEW),
  `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/radius/BorderRadiusApplier.kt`,
  `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BehindContentBorderTest.kt` (NEW),
  `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BorderRadiusSelfPaintTest.kt`.
  fix-r1: painters internal + kdocs (no body change), P5a–f.
- **U2 PO-sides-android** (`unit-U2.patch`): `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt`,
  `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BordersFacade.kt`,
  `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt`,
  `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/sides/BorderSidesBehindContentTest.kt` (NEW).
  fix-r1: the clip condition on the three call-site lines, the facade kdoc, verbatim P3 wires, P3h / P3i.
- **U3 FO-pin** (`unit-U3.patch`): `runtimes/compose/src/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt` (NEW).
  fix-r1: P4b in capture mode.

Sizes: BehindContentBorder.kt 200 (=); StyleApplier.kt 1194 (base 1188; builder 1191: one new call-site line, one changed,
five comment lines); BordersFacade.kt 152 (builder 149); BorderSidesBehindContentTest.kt 175 (157); EffectsClipFilterOrderTest.kt
79 (=); **BehindContentBorderTest.kt 266 (149)** — a new test file past the 200-line target: kept in the lane's owned file
rather than a new unowned path (ownership is a hard rule); under the ~300 split line. Every new line is commented.
No scratchpad pointer in any patch (grep).

### Geometry self-test (re-run)

`/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes L6 --self-test`
→ exit 0, "self-test: HOLDS — 0 violation(s)": gating 7 FAIL, control 29 PASS, report 1 FAIL — identical to the builder's
(`fix-r1/geometry-selftest.wave54-final.txt`). Run on the shared tree as it stood (its `wave55-plan/` files carry the
orchestrator's in-progress round-4 edits: `expectations.json` L6 differs from HEAD in `geometryProbes` only — carriers,
mayMove, sharedStems and revertUnits, which the census reads, are unchanged).

### revertStatesCompiled (fix-r1, on the re-cut patches; `l6-state.sh`: clean 611c9f93 checkout of the lane's paths in the
fix worktree + `git apply` of the kept patches, then compileDebugKotlin + compileDebugUnitTestKotlin + the 16 focused filters)

revertStatesCompiled: U3 reverted (U3 chain [U3]) (kept: U1 U2): rc=0; focused :runtime suites → SUMMARY suites=76 tests=616 failed=0
revertStatesCompiled: U2 reverted (U2 chain [U2] = U1 chain step 1) (kept: U1 U3): rc=0; focused :runtime suites → SUMMARY suites=76 tests=609 failed=0
revertStatesCompiled: U2 then U1 reverted (U1 chain [U2, U1] end) (kept: U3): rc=0; focused :runtime suites → SUMMARY suites=75 tests=595 failed=0
revertStatesCompiled: U2 and U3 reverted (combined) (kept: U1): rc=0; focused :runtime suites → SUMMARY suites=75 tests=607 failed=0
revertStatesCompiled: base 611c9f93 (no L6 unit) (kept: none): rc=0; focused :runtime suites → SUMMARY suites=74 tests=593 failed=0
revertStatesCompiled: full (U1 U2 U3, the final restore) (kept: U1 U2 U3): rc=0; focused :runtime suites → SUMMARY suites=77 tests=618 failed=0

(raw lines: `fix-r1/revert-states.txt`; +8 tests over the builder's counts = P3h, P3i, P5a–f.) Final state: U1 + U2 + U3;
a re-cut there is byte-identical to the delivered patches; `git apply --check` of the three holds on the shared HEAD.

### Predictions changed by fix-r1 (everything else in the builder's table stands)

| cell | wave54-final → predicted | confidence | why |
|---|---|---|---|
| `css-cascade/revert-layer-015.html android` | f 0.9663 → identical | HIGH | clipping input: chain = base (P3h; census) |
| `css-pseudo/first-line-and-placeholder.html android` | P 0.9743 → identical | HIGH | same |
| `css-images/gradient/gradient-hue-direction.html android` | → identical | HIGH | its 3 `<hr>` bands clip (UA overflow hidden) |
| the 19 withdrawn mayMove stems (list above) | → identical | HIGH (chain), MED (no other mover in the doc where one remains) | clipping bands keep the base chain |
| `css-gaps/flex/flex-gap-decorations-023.html android` (mayMove) | P 0.9541 → ≈0.9548 (replay), stays P | LOW-MED | widget replica over the band; |Δ| < 0.002; DEGENERATE, never claimed |
| `css-display/display-contents-dynamic-pseudo-insertion-001` android (unscored) | identical | MED | the builder's item; the ::after ink starts 1 px inside the content box |

Flips 0 · lost 0 · newly measured / unmeasured-now 0 (unchanged).

### Plan regeneration need (for the orchestrator; plan files are not this lane's to edit)

1. **Still needed (pre-existing, the builder's item):** add `wpt__css-display__display-contents-dynamic-pseudo-insertion-001`
   to L6 `mayMove` BY RULE ("a kept `display: contents` box with pseudos"), predicted identical.
2. **No longer needed:** the skeptic's proposed rule row for revert-layer-015 / first-line-and-placeholder — both are now
   predicted byte-identical (a clipping box keeps the base chain), so css-pseudo need not join `probeSections`. If the
   orchestrator wants them read anyway, they can be named as identity controls; control-check at stage 2 (css-cascade) and at
   the closing gate (css-pseudo) should list them unchanged.
3. **Prose only:** U2's description gains "except on a box whose overflow clips (css-overflow-3 §3; the base chain is kept
   there)"; the commit text "three call-site lines" stays true. No carrier, unit or prediction row of `expectations.json`
   changes; `mayMove` now over-covers 19 stems (harmless).
4. **mayMoveRule wording** the readers should accept besides "a band now under its child": "the band now inside the
   element's opacity group" (anchor-center-overflow-003, the builder's) and "a UA-widget replica now over the band"
   (flex-gap-decorations-023, |Δ| predicted 0.0007).

## ORCHESTRATOR WINDOW REQUESTS (fix-r1 delta; the builder's section above stands otherwise)

- Stage 1 `wave55-pre`: unchanged commands, expected outputs and decisions (no stage-1 section holds a withdrawn band
  except filter-effects' backdrop-filter-edge-behavior, mayMove, now predicted identical).
- Stage 2 `wave55-probe`: `node tools/titan/results/wave55-gate/control-check.mjs wave55-open wave55-probe --lane L6-android-paint-order --sections css-cascade,css-display,css-backgrounds,css-anchor-position,css-gaps,css-images,css-writing-modes`
  → expect `wpt__css-cascade__revert-layer-015` android UNCHANGED (byte-identical); the 12 attachment-local-clipping-* /
  positioning-2/3/4, anchor-scope-scroll, all-prop-001 and gradient-hue-direction unchanged (in direction-upright-001/002
  only the `<hr>` band is withdrawn; both documents stay may-move through their other bands); flex-gap-decorations-023
  android may-move with |Δ| ≤ 0.002. Decision: a change on revert-layer-015 android is an L6 leak → bisect (rule 5) → U2.
- Closing gate: `first-line-and-placeholder` android read for the first time there (css-pseudo is in no probe stage) —
  expected identical; a move is an unpredicted R5 mover → its picture and the U2 clip condition are the first suspects.

Not verified (fix-r1): no device run — every "identical" above is a chain-level (class signature) proof plus the order
argument, not a pixel proof; the widget replay is a SCORE prediction; P5 pins the call lists, not the raster; the rounded
path branch and genericBand remain device-only; the census reads each component's own declarations (no UA / inheritance
cascade); `:app:testDebugUnitTest` and every full suite were not run (single-writer sweep owns them).
patch: unit-U1.patch sha256 8044c1c26d8b402c5e30ac1eb1b68f5753de27b968e16308c40627faac8fe375
patch: unit-U2.patch sha256 4fb42fff3951beaa88e43bd11cbd7a66227676102d97b85cbeadd5f97c48da52
patch: unit-U3.patch sha256 1c21d8499b357a7f741a0bcd2aa18d9ddaa0df90fae8e90162d54425fc7fb578

STATUS: COMPLETE

STATUS: PARTIAL — fix-r2 in progress

## Fix pass fix-r2 — execution log (the prose section follows below this log)

Fix-r2 worktree: the `TREES:` line naming `wave55-fix2-L6` (created at HEAD 4e789976, whose runtimes / apps / converter /
`Package.swift` equal 611c9f93's; provisioned per PLAN §0; `unit-U1.patch` → `unit-U2.patch` → `unit-U3.patch` applied
clean, sha256 8044c1c2… / 4fb42fff… / 1c21d849…). Mutation XR3 re-executed with `fix-r2/mut.sh` on the edit
`fix-r2/mutations/XR3.edit` (byte-identical to `reverify-r1/mutations/XR3.edit`), with the census run on the mutated tree.

MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r2 XR3; byte-exact copy in the TREES worktree under .l6-fix2-mut-backup/]
  fix-r2 XR3 mutated  → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
  fix-r2 XR3 mutated  → census on the mutated tree: rows: 10719 (TSV: fix-r2/fixr2-census-xr3.tsv.gz)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r2 XR3; post == pre: yes]
  fix-r2 XR3 restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r2 MD1-census-control; byte-exact copy in the TREES worktree under .l6-fix2-mut-backup/]
  fix-r2 MD1-census-control mutated  → gradle rc=1 FAIL BorderSidesBehindContentTest :: P3i - a clipping SOLID box keeps androidx's band (drawn after its clipped content) FAIL BorderSidesBehindContentTest :: P3h - a clipping INSET input keeps its slow-path band outer of the background SUMMARY suites=4 tests=36 failed=2 
  fix-r2 MD1-census-control mutated  → census on the mutated tree: rows: 10719 (TSV: fix-r2/fixr2-census-md1.tsv.gz)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt sha256 e0b379be0297725b5e865f9fb42c5b0290427a40589f4f49597e3f30f569f8ec   [fix-r2 MD1-census-control; post == pre: yes]
  fix-r2 MD1-census-control restored → gradle rc=0 SUMMARY suites=4 tests=36 failed=0 

## Fix pass fix-r2 — the re-verify pass `reverify-r1/`, mutation XR3's verdict, the prelanding items

Source of the items: `../wave55-gate/prelanding/citations.md` "Mismatches" 2, 3, 4 (the L6 part) and 7. Item 1 (the L6
`mayMove` / `mayMoveRule` regeneration) is plan-build.py's and the orchestrator's; nothing here touches `wave55-plan/`.

- **Item 2 (stale TREES pointer) → fixed.** `_note.md:335` (the first line of "Fix pass fix-r1 — execution log"; it was
  :334 before this pass prepended its own `TREES:` line at :1) now reads "the `TREES:` line naming `wave55-fix-L6`"
  instead of "the FIRST `TREES:` line". Every pass's tree is found by its name: builder `wave55-build-L6`, fix-r1
  `wave55-fix-L6`, re-verify `wave55-reverify-L6`, fix-r2 `wave55-fix2-L6`.
- **Item 7 (nit) → fixed.** The P1b row (:181) names :210 of the U1-state file M2 mutated (pre 3ad5a63a…), not HEAD's :207.
- **Item 4 (scratchpad paths) → fixed for L6.** `skeptic.md:1` (its `TREES:` line) reads `<scratch>/wave55-skeptic-L6`.
  The note's non-`TREES:` lines, `reverify-r1/` (every file, the `.tsv.gz` contents included) and `fix-r2/` carry no
  scratchpad path (grep: 0); only this note's four `TREES:` lines (:1-:4) name the worktrees, by design (PLAN §0).

### The re-verify pass `reverify-r1/` (an independent re-verifier; records only, no patch byte changed)

On the fix-r1 patches (sha256 8044c1c2… / 4fb42fff… / 1c21d849…) in its own worktree (the `TREES:` line naming
`wave55-reverify-L6`):
- **Census** — `reverify-r1/census.sh` + `ReverifyL6CensusTest.kt.txt` + `census.py` → `census.out.txt` (TSVs
  `census-{base,fix}.tsv.gz`; the skeptic's census plus a `clip` = `config.overflow.shouldClip` column): 10 719
  components of 1435 documents, ERR 4 / 4; banded 1973 (U1 self-paint 10, U2 legacy 1963, of which 29 clipping in 26
  documents); invariants I1–I5 hold (the 29 clipping legacy chains == base; 0 of them with `BehindContentBorderElement`;
  all 1485 non-clipping fast rows carry it; 0 unbanded chain changes; builder → fix changes only on the clipping rows, 18);
  fix-r1 movers: 261 exposed documents, 260 in the plan set, **GROWTH 1 = display-contents-dynamic-pseudo-insertion-001**
  (the builder's and fix-r1's stem: the plan regeneration of item 1); U1 2/2 and U2 25/25 carrier captures keep an
  exposed mover on their unit's route.
- **Revert states** — `reverify-r1/revert-states.txt`: the six states compile (rc=0) and run the 16 focused filters with
  0 failures: U3 reverted 616, U2 reverted 609, U2 then U1 595, U2 + U3 607, base 593, full 618 — fix-r1's counts.
- **Mutations** — `reverify-r1/mutations.log.txt`: 9 bracketed mutations, MD1, XR1, XR2, XR3 (StyleApplier.kt, pre
  e0b379be…) and skeptic-X1 / X2 / X3 / X4 / X7 (BehindContentBorder.kt, pre c67accab…); every MUTATING line has its
  RESTORED pair with post == pre. `reverify-r1/mut.sh` wrote the brackets to that log rather than to this note on purpose
  (its header: to keep `STATUS: COMPLETE` last); PLAN §0 puts brackets in the note, so this section is their pointer.
  Eight went red → green (MD1 / XR1 / XR2 → P3h + P3i; X1 → P5b, X2 → P5a, X3 → P5c, X4 → P5e, X7 → P5f).
  **XR3 survived** (4 suites, 36 tests, 0 failed) with no verdict recorded — adjudicated below.

### XR3 — `config.overflow.shouldClip` → `config.overflow.clipsX` on both StyleApplier legacy-branch lines (:758, :760)

- **Where the predicates differ (the code; `OverflowConfig.kt`, unchanged by L6).** `shouldClip = clipsX || clipsY`
  (:81-82); `clipsX = axisClips(usedOverflowX)` (:67-68), `clipsY` its twin (:71-72), `axisClips(used) = used != VISIBLE`
  (:131); `usedOverflow` (:112-124) uses `visible` as `auto` beside a non-{visible, clip} axis. So clipsX ≠ shouldClip
  ⟺ clipsX false ∧ clipsY true ⟺ specified overflow-x `visible` ∧ overflow-y `clip` (beside `hidden` / `scroll` /
  `auto`, x is used as `auto` and clips). `OverflowExtractor.kt:30-37` maps `Overflow` to both axes, `OverflowX` /
  `OverflowInline` to x and `OverflowY` / `OverflowBlock` to y. And `BorderSideApplier.applyBorders` returns the modifier
  untouched when `!config.hasBorders` (:78 with U2; :72 at HEAD), so both `BordersFacade` calls are identities on a
  band-less box. XR3 can change a chain only on a box with overflow-x `visible` + overflow-y `clip` AND a border band on
  the legacy route.
- **The reachable payloads, executed** (`fix-r2/xr3.py` → `fix-r2/xr3.out.txt`, exit 0):
  1. IR scan of all 1435 wave54-final per-test IR documents at every depth: Overflow* occurs only in component
     `properties` (OverflowX 1997, OverflowY 2005, OverflowClipMargin 2, OverflowWrap 1). There is no `Overflow`,
     `OverflowBlock` or `OverflowInline`, and none in pseudos or any other bucket.
  2. Compose's own predicates per component (`fix-r2/FixR2L6CensusTest.kt.txt` = reverify-r1's census plus ovX / ovY /
     clipsX / clipsY / legacyBand columns, run by `fix-r2/census.sh` on the fix tree → `fix-r2/fixr2-census-fix.tsv.gz`;
     its reverify-r1 columns equal `reverify-r1/census-fix.tsv.gz` on all 10 719 rows). **clipsX ≠ shouldClip on exactly 7
     components**, all overflow-x `visible` + overflow-y `clip`:
     - abspos-multicol-in-second-outer-clipped `…__1__0-039`;
     - css-overflow clip-002 `…__2-008`, clip-003 `…__3__0-020` and `…__4__0-023`, clip-004 `…__2-029`,
       clip-005 `…__2-035`, clip-007 `…__1__0-043`.

     **None has a band**: 0 Border* declarations, and no `meta` / sourceTag or pseudos, so no tag-keyed UA rule adds one.
     So 0 are on the legacy band route. None of the 7 stems is in an L6 plan set. The 4 ERR rows
     (conic-gradient-line-height-relative-units-003 / -004) are not among them: the IR scan reads them too and finds the
     same 7.
  3. **The census of the XR3-mutated tree is byte-identical to the fix tree's** (bracketed run above,
     `fix-r2/fixr2-census-xr3.tsv.gz` vs `fix-r2/fixr2-census-fix.tsv.gz`: sha256 b956343a… both; 0 differing cells over
     10 719 rows, the chain signature included).
  4. **The census does see a predicate edit (positive control, same pipeline, same worktree):** MD1 (the builder's
     unconditional lines) → 18 differing rows, all of them clipping legacy-band rows — fix-r1's "builder ≠ fix on exactly
     those 18 rows" (`fix-r2/fixr2-census-md1.tsv.gz`, sha256 4b7b5167…). The focused suite goes red on P3h + P3i.
  5. **Pins.** Every legacy-branch pin's verbatim payload has clipsX = shouldClip:
     - P3a flex safe-001 `…__0-023` and P3b–d background-clip-002 `…__2-095`: visible / visible → false / false;
     - P3h revert-layer-015 `…__0-077`: clip / clip → true / true;
     - P3i anchor-scope-scroll `…__0-781`: visible / hidden, x used as `auto` → true / true;
     - P4b clip-path-filter-order has no band.

     That is why XR3 survives the focused suite (re-run above: 4 suites, 36 tests, 0 failed).
- **Verdict: equivalent mutant on the reachable payloads.** The 10 719 components of the 1435 verbatim wave54-final
  documents include every L6 carrier, every L6 geometry-key document and every pin payload. On them, clipsX and
  shouldClip differ on 7 components, none of which has a border band, and the census of the XR3-mutated tree is
  byte-identical to the fix tree's while the MD1 control moves 18 rows. No pin was added and no patch was re-cut.
- **Not equivalent in general (recorded, unreachable, not pinned).** On a BORDERED box with overflow-x `visible` +
  overflow-y `clip`, the fix keeps wave 54's chain (the band over the content on all four sides) and XR3 would paint the
  band behind the content. Neither is the CSS picture: CSS paints the x-overflow over the left / right band, and the
  y-content stops at the padding edge. The fix's choice moves no pixel against base on such a box. A future carrier with
  that payload needs a per-axis band split, not either predicate.

### Focused suites and revertStatesCompiled (fix-r2)

fix-r2 changed no byte of any patch (the delivered patches are fix-r1's cut; `patch:` lines re-stated below after the
final restore). The six revert states were re-run anyway on the landing base, in the fix-r2 worktree at HEAD 4e789976
(`l6-state.sh` from the lane dir; a clean checkout of the lane's paths + `git apply` of the kept patches, then
compileDebugKotlin + compileDebugUnitTestKotlin + the 16 focused filters; raw lines `fix-r2/revert-states.txt`; the
JUnit XML `timestamp`s of the runs are fresh, so they executed and were not restored from a cache). The counts equal
fix-r1's and reverify-r1's. Final state: U1 + U2 + U3 (StyleApplier.kt e0b379be… after every restore). `git apply -R
--check` of U3, U2 and U1 holds there.

revertStatesCompiled: [fix-r2, HEAD 4e789976] U3 reverted (U3 chain [U3]) (kept: U1 U2): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=76 tests=616 failed=0
revertStatesCompiled: [fix-r2, HEAD 4e789976] U2 reverted (U2 chain [U2] = U1 chain step 1) (kept: U1 U3): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=76 tests=609 failed=0
revertStatesCompiled: [fix-r2, HEAD 4e789976] U2 then U1 reverted (U1 chain [U2, U1] end) (kept: U3): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=75 tests=595 failed=0
revertStatesCompiled: [fix-r2, HEAD 4e789976] U2 and U3 reverted (combined) (kept: U1): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=75 tests=607 failed=0
revertStatesCompiled: [fix-r2, HEAD 4e789976] base 4e789976 (no L6 unit) (kept: none): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=74 tests=593 failed=0
revertStatesCompiled: [fix-r2, HEAD 4e789976] full (U1 U2 U3, the final restore) (kept: U1 U2 U3): compileDebugKotlin+compileDebugUnitTestKotlin rc=0; focused :runtime suites → SUMMARY suites=77 tests=618 failed=0

No device run (fix-r2): every "identical" above is a chain-level proof (the JVM census), not a pixel proof.
patch: unit-U1.patch sha256 8044c1c26d8b402c5e30ac1eb1b68f5753de27b968e16308c40627faac8fe375
patch: unit-U2.patch sha256 4fb42fff3951beaa88e43bd11cbd7a66227676102d97b85cbeadd5f97c48da52
patch: unit-U3.patch sha256 1c21d8499b357a7f741a0bcd2aa18d9ddaa0df90fae8e90162d54425fc7fb578

STATUS: COMPLETE
