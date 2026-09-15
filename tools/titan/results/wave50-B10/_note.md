# wave50-B10 — gap-decoration line bands, and the erased references

Lane B10 of wave 50. Queue items `docs/BACKLOG.md` 7(a) (the
gap-decoration line-band painter) and 7(e) (the css-gaps distribution
defects). **No device gate this wave** — everything below is a JVM /
Catalyst pin, a PNG measurement against the frozen refs, or a corpus
simulation over `tools/titan/runs/wave49-final`.

---

## 1. SHIPPED — the line band is the flex LINE BOX, not the item union

`GapDecorationLines.toLines` gave each flex line the UNION of its item
rectangles as its cross extent. That is the line box only while the items
fill their line. `align-content: stretch` — the flex initial `normal`,
css-align-3 §5.1 — grows every line into a definite container cross size
(css-flexbox-1 §9.4 step 8), and the union then under-reports the line box
at both ends.

Measured, ref vs the wave49-final native captures:

| test | quantity | ref | iOS | Android |
|---|---|---|---|---|
| 045 | red column rule, image x | `[76,81]` | `[72,77]` | `[72,77]` |
| 045 | gold rule runs, image x | `[18,76]` `[81,138]` | `[18,68]` `[81,131]` | `[18,68]` `[81,131]` |
| 046 | gold rule bands, image y | `[73,78]` `[134,139]` | `[70,74]` `[131,136]` | `[70,74]` `[132,136]` |

So the red rule is **4 px left** of Chromium's and the gold runs are 7–8 px
short on 045; the 046 bands are the **~3.5 px** the backlog item quotes.
The item rectangles are NOT in dispute — ref, iOS and Android place them
identically to the pixel on both fixtures (connected-component scan of the
`#007bff` fill).

**The fix.** A new pure module on each native rebuilds the §9.4-step-8 line
boxes from the SAME arithmetic the wrapping layout runs
(`FlexWrapLines.stretchLines` on Compose, `FlexWrapPlan.stretchLines` on
iOS), and `GapDecorationSegments.build` paints against those:

- `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/GapDecorationBands.kt`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/GapDecorationBands.swift`

The container's `row-gap` / `column-gap` / `align-content` now ride on the
gap-decoration config (read, never CLAIMED — the spacing and flexbox
extractors keep the registry rows).

**No renderer seam was needed.** The backlog item proposed threading the
renderer-computed bands into `GapDecorationSegments.build`; re-running the
one shared pure function inside the painter reaches the same numbers with
no new channel through `ComponentRenderer` on either platform, so nothing
in this lane is deferred behind a patch.

**What is taken on trust, stated plainly:** the used cross gap. It cannot
be cross-checked against the item frames — the identity
`container = Σ lineSize + (n−1)·gap` holds for *every* gap once the sizes
are derived from it — so it is read from the same IR the layout reads, and
it is `null` (refusing the whole rebuild) whenever the wire left it
unresolved. Everything else IS checked: the rebuilt bands must contain the
items they claim to describe, or the union model is kept and a
PropertyTracker breadcrumb is left.

### Blast radius — corpus-simulated, every css-gaps cell

`css-gaps-band-blast-radius.json` (regenerate: `node
tools/titan/results/wave50-B10/census.mjs`). Of the **48 scored css-gaps
tests in wave49-final, exactly 2 change**: `flex-gap-decorations-045` and
`-046`. Every other test is inert *by construction* — nowrap, a
positioning `align-content`, an auto cross size, or zero §9.4 leftover —
not by inspection. In particular the four currently-failing css-gaps cells
(027, 033-web, 034, 035) and the near-threshold 031/032/036/037 are all
`auto cross size` or `align-content: CENTER` and are **untouched**.

### Prediction for the gate (numbers, not adjectives)

- 045 natives: currently `P 0.9549` / `P 0.9549`. The red rule moves 4 px
  onto the ref band and the seven gold runs gain 7–8 px each. Expect the
  natives to land in the same band as web (`1.0000`), i.e. ≥ 0.99. No cell
  flip (already passing) — a margin gain.
- 046 natives: currently `P 0.9951` / `P 0.9908`. The first gold band
  becomes exact; the second keeps a 1 px offset on Android only, inherited
  from `FlexWrapLines.stretchLines`' integral split (Android already places
  that item row at image y 140 where Chromium places it at 139). Expect
  ≥ 0.995 on both.
- Web: unchanged on both, and structurally immune — the web engine has no
  segment model at all (`runtimes/web/src/engine/columns/ColumnRule*Applier
  .ts` emit the CSS longhands and the BROWSER paints the rules), which is
  also why web is the platform that renders 033 correctly.
- **Any movement on a css-gaps cell other than 045/046 is a regression**,
  not an expected side effect.

### The pins, and the proof they can fail

New: `runtimes/compose/src/test/java/com/styleconverter/runtime/columns/GapDecorationBandsTest.kt`
(11 tests) and `runtimes/swiftui/Tests/StyleConverterRuntimeTests/GapDecorationBandsTests.swift`
(11 tests). Both pin the corrected bands AND the pre-wave-50 union output,
so the defect is pinned by the pixels that exhibit it.

Mutation-proved (both runtimes, executed):

| mutation | Compose | iOS |
|---|---|---|
| `GapDecorationSegments.build` skips the band rebuild | 4 of 10 fail | 4 of 11 fail (17 assertions) |
| the containment gate always accepts | 1 fails (`bands that disagree…`) | 1 fails (`…AreRejected`) |

Two existing test files had to state the container's real gaps, because
they modelled the live wire incompletely and the rebuild needs the cross
gap: `SkepticGapDecorationProbeTest.kt` (the 003-family wire carries
`ColumnGap`/`RowGap` 10 — verified in the wave49-final per-test IR; the
skeptic's own authored 2×2 layout has 20) and
`GapDecorationGrammarTests.swift` (`testOmittedWidthPaintsAtMedium`). Both
keep their original expected rectangles.

Focused suites, green: Compose `columns.*` + `layout.flexbox.*` = 398 tests,
0 failures. Catalyst `GapDecoration*Tests` + `FlexWrapPlanTests` = 73 tests,
0 failures. `node tools/visual/spec-cite-validate.mjs` exit 0.

---

## 2. GATE-CRITICAL FINDING — the ref recipe erases `z-index: -1` ink

**The css-gaps cells 033/034/035 cannot be fixed by any runtime change:
their reference renders no rules at all.**

`capture-browser-ref.mjs`'s `canvasFrameCss()` injects
`:where(html, body) { …; background: #FFFFFF; }`. Canvas propagation
already paints the page white from the ROOT's background; the BODY half
additionally paints an opaque box in the body's own layer, and CSS 2.1
Appendix E orders the root stacking context (1) root background,
(2) NEGATIVE-z-index descendants, (3) in-flow block backgrounds. A
reference that draws its decoration boxes as `position: absolute;
z-index: -1` body children — which is exactly how the css-gaps
`-ref.html` files after 032 are authored — is painted at step 2 and buried
at step 3.

**Executed A/B** (`zneg-ab-probe.mjs`, puppeteer with the repo's own
`BROWSER_LAUNCH_ARGS`, 390×600), chromatic pixels live vs the same sheet
with the body half dropped:

| reference | live | control |
|---|---|---|
| `flex-gap-decorations-033-ref` | **0** | 5200 |
| `flex-gap-decorations-034-ref` | **0** | 2500 |
| `flex-gap-decorations-035-ref` | **0** | 4050 |
| `flex-gap-decorations-036-ref` | **0** | 2050 |
| `flex-gap-decorations-037-ref` | **0** | 2050 |
| `css-text/…/hanging-punctuation-block-bound-001-ref` | **0** | 58184 |
| `css-view-transitions/css-tags-paint-order(-with-entry)-ref` | 234000 | 234000 (unaffected — its negative z-index is inside a `::view-transition` stacking context) |

**Scope** (`erased-refs.json`): 1435 wave49-final tests examined (scored denominators 1379 web / 1366 iOS / 1366 Android, as corpus-v6.15 counts them — skeptic S6-18),
42 corpus refs use a negative z-index, 28 of them rasterise differently
under the fix, and **6 are the match target of a scored test → 18 cells,
10 of them currently `f`**:

```
css-gaps  flex/flex-gap-decorations-033   web f 0.9400  iOS P 1.0000  and P 1.0000
css-gaps  flex/flex-gap-decorations-034   web f 0.9458  iOS f 0.9466  and f 0.9466
css-gaps  flex/flex-gap-decorations-035   web f 0.9206  iOS f 0.9220  and f 0.9220
css-gaps  flex/flex-gap-decorations-036   web P 0.9557  iOS P 0.9565  and P 0.9565
css-gaps  flex/flex-gap-decorations-037   web P 0.9568  iOS P 0.9576  and P 0.9576
css-text  hanging-punctuation/hanging-punctuation-block-bound-001
                                          web f 0.7567  iOS f 0.7440  and f 0.6796
```

Two consequences the campaign should record:

1. **033's native `P 1.0000` is a degenerate pass.** The natives paint
   nothing in a 0 px gap and the ref shows nothing, so they agree on an
   empty picture; web, which paints the rules Chromium really paints,
   scores `f 0.9400`. This is another instance of the standing "a score is
   not a look" rule — the montage is unambiguous: ref blank, web red+blue
   rules, both natives blank.
2. **034/035 are not render defects.** All three platforms agree with each
   other at ≥ 0.995 (`nativeParity` in `erased-refs.json`) and all three
   fail the blank ref together. Whoever picks up queue 7(e) should not
   spend a lane on them until the ref is re-frozen.

### The fix, deferred as a verified patch

`capture-browser-ref-body-background.patch` — applies cleanly to
`747b28e4` (`git apply --check` verified; applied, exercised and reverted
in-lane). It moves `background` off the body half, keeps it on
`:where(html)` where canvas propagation reads it, and replaces the
now-wrong "background legitimately stays on both" note with the measured
account.

**Why it is not applied here:** it is an INSTRUMENT change. `CANVAS_REV`
must be bumped and the affected refs re-frozen, and the resulting cell
moves are a CALIBRATION to be adjudicated per the standing rule (capture
hashes compared, every flip attributed), not a render delta. Expected
moves after a re-freeze, stated up front:

- 034, 035: all three platforms should flip `f → P` (+6 cells) — they
  already agree with each other at ≥ 0.995.
- 036, 037: pass today at ~0.956 against a blank ref; expect the score to
  rise, not the verdict.
- 033: web should flip `f → P` (+1) and **both natives should flip
  `P → f` (−2)** — they paint nothing where Chromium paints 10 px rules in
  0 px gaps. That is the honest direction; suppressing it would mean
  keeping a degenerate pass.
- `hanging-punctuation-block-bound-001`: the three platforms disagree with
  each other at 0.70–0.84, so a corrected ref exposes a real render defect
  rather than flipping the cells. Expect it to stay `f` and to become
  actionable for the first time.
- Net on the 18 cells: **+5 verdicts, −2 verdicts**, and one previously
  unreachable defect made visible.

Regenerate the scope with `node tools/titan/results/wave50-B10/census.mjs`;
re-measure the erasure with `zneg-ab-probe.mjs` and the patch's byte-level
blast radius with `canvas-css-ab.mjs` (both take ref paths or a
newline-delimited list file).

---

## 3. NOT FIXED — css-gaps 006, and the price of fixing it

`flex-gap-decorations-006` (natives `f 0.8266` / `f 0.8223`, web `P`) is
**not** a gap-decoration defect. Its container is
`writing-mode: vertical-lr`, so css-flexbox-1 §4 makes the flex main axis
the writing mode's INLINE axis — vertical — and the whole layout
transposes. The refs and web do that; both natives derive
`mainHorizontal` from `flex-direction` alone (the `.gapDecorations(…,
mainHorizontal:)` call in `runtimes/swiftui/Sources/StyleConverterRuntime/
Renderer/ComponentRenderer.swift`, and `axes.mainHorizontal` in Compose's
`runtimes/compose/src/main/java/com/styleconverter/runtime/layout/flexbox/
FlexAxes.kt`), so the items
lay out left-to-right, the red `column-rule`s stand vertically instead of
lying horizontally, and the blue `row-rule` lies horizontally instead of
standing. The montage shows a clean transpose of the ref, not a
mis-measured band. Android additionally rotates the item TEXT (so the
writing mode reaches the text pipeline) while leaving the boxes in the
horizontal arrangement, which is the same split from the other side.

The fix is a writing-mode-aware flex axis, which is a flexbox-wide change,
not a `columns/` one. **Priced before proposing**
(`vertical-flex-census.mjs`, over every wave49-final section: a component declaring `display: flex` and a
vertical `writing-mode` on the same element):

```
tests=11  scoredCells=33  currently failing=4
css-flexbox  abspos/abspos-autopos-{vlr,vrl}-{ltr,rtl}            P P P  (×4)
css-flexbox  abspos/flex-abspos-staticpos-align-self-safe-001/2/3 P P P  (×3)
css-flexbox  alignment/flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse  P P P (×2)
css-gaps     flex/flex-gap-decorations-006      web P · iOS f 0.8266 · and f 0.8223
css-writing-modes forms/input-range-zero-inline-size  web P · iOS f 0.9747 · and f 0.9743
```

So the upside is **at most 4 cells** and the exposure is **27 cells that
pass today**, with no device gate available this wave to check them. That
trade is not worth taking blind; it belongs to a wave that opens with a
gate. Recorded here rather than attempted.
