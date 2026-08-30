# Campaign backlog — the single source of truth for wave work

This file is what a wave reads before doing anything and updates before
shipping. It replaces session-memory queues: any agent with this file, the
`/wave` skill (`.claude/skills/wave/SKILL.md`), and `docs/STATUS.md` can run
the next wave with zero conversation context.

**Contract: every wave PR updates this file** — completed items removed,
new deferred/discovered items added with evidence pointers, the
next-wave obligations section refreshed. A wave PR that does not touch
this file is incomplete.

Corpus history and per-wave findings live in `docs/STATUS.md` (one dated
paragraph per wave) and `tools/titan/results/corpus-v*.json` (one snapshot
per wave, `_note` carries the full story). Current: **corpus-v6.15**
(wave 49 — web 1205/1379 87.4%, iOS 1081/1366 79.1%, Android 1058/1366
77.5%; 32 cells gained, zero lost, 4111 scored cells with exact column
parity). All deltas attribute against the most recent snapshot, never
across the PR-#126 instrument change.

---

## Standing constraints (never violated, never re-proposed)

- **Never push the branch `claude/jovial-shockley-b4adce`** (the attic —
  contains AiLogs with token-like strings). Recycled worktrees silently
  spawn on it: always `git checkout -f -B campaign/applier-campaign
  neworigin/dev` and verify the tip before any work.
- **Ring-fence**: the WPT test `filter-effects/backdrop-filter-basic-blur`
  belongs to an external session. No test-specific code, ever. Generic
  mechanisms that incidentally move it are reported plainly in the wave
  PR (precedent: waves 46, 47) — never carved out by name.
- **No dev→main promotion** — explicitly declined by the owner; do not
  re-propose. Same for device-visual CI ("Skip #3").
- `gh` default repo resolves to an ARCHIVED legacy repo — every gh command
  needs `-R future-style-converter/style-converter`.
- Squash-merge wave PRs to `dev`; verify `state == MERGED` **before**
  resyncing (violated three times historically).
- Skeptic lanes must produce **executed repros** — a skeptic that only
  reads code has failed.
- A new check must be **proven able to fail** (mutation/negative test)
  before it is trusted.
- **Never commit `node_modules` in any form.** PR #126 accidentally
  committed it as an absolute symlink into a sibling worktree, which
  silently served THAT tree's web runtime to every vite capture (wave-48
  W5's GATE-CRITICAL find). Wave 48 removed the tracked entry and
  tightened `.gitignore` (`node_modules`, no trailing slash — the slash
  form only matches directories, which is how the symlink slipped past).
- **Suite runs on a shared mid-wave tree are single-writer**: concurrent
  gradle daemons produced spurious AGP jar-scan failures during wave-48
  skeptics. Fix/skeptic lanes run focused tests only; the orchestrator
  runs one sequential full sweep afterwards.

## Next-wave obligations (wave 50 opens with these)

1. **Deltas attribute against corpus-v6.15** (the wave-49 gate).
2. The 327-net's fatal exit classes stay in force: exit 3 (non-sRGB
   capture), exit 4 (unledgered divergence), exit 5 (**stale
   ledger/waiver — the action is always DELETE the named line, never
   re-add**), exit 6 (spec oracle). The ledger
   (`tools/visual/cross-platform-expectations.json`) is co-maintained by
   every wave that touches rendering.
3. **The pass column is ~35% degenerate and that is now the campaign's
   biggest known problem.** Wave-49 lane I1 opened 78 randomly-drawn
   PASSING cells by eye: 27 are visibly wrong renders (wrong colour,
   wrong position, wrong line breaking, missing glyphs — "PASS" rendered
   "ASS", "A. B. C." rendered "0. 1. 2."). The 179-cell red-square class
   is one visible slice. **No colour-based check reaches the rest**, so
   the next instrument step is NOT another colour veto. Evidence:
   `scratchpad/wave49-i1/handverdicts.json` (per-cell verdicts) and the
   census in the wave-49 PR.
4. **The novel-ink veto is SHIPPED DISARMED** (`TITAN_NOVEL_INK_VETO=1`
   to arm; stamped on every diff for triage regardless). It missed its
   pre-registered 95% recall bar — 66.7% on the census-selected defect
   set, **16.7% on an unbiased sample**. Do NOT arm it by lowering the
   threshold. The two measured miss mechanisms are (a) wrong-colour-AND-
   displaced renders, whose divergent region fills with the reference's
   own palette, and (b) small marks that clear the fraction bar but not
   the mass bar. The named successor is a **displacement-aware
   denominator** (exclude divergent pixels explained by a rigid
   translation of reference ink before computing the ratio) — a
   redesign needing its own pre-registered decision rule, and a fresh
   hand-verified defect set NOT selected by any rule correlated with the
   check's own bars.
5. ~~Column-presence check~~ DONE (wave 49): `assertPlatformColumns` in
   `inject-wpt-block.mjs` (warns by default, fatal under
   `TITAN_REQUIRE_ALL_COLUMNS=1`) plus a per-section guard in the gate
   script that reprovisions and retries once, then aborts. Both were
   exercised for real at the wave-49 gate.
6. **Mid-run disk is now a gate abort (<8G).** The first wave-49 gate
   attempt lost its Android column when the HOST hit 94% and
   destabilised the emulator into an adb "Broken pipe" during
   `:app:installDebug`. Start-only `df` is not enough. Pre-gate pruning
   recipe that worked: `xcrun simctl delete unavailable` + delete every
   Shutdown sim except the seed (81 sims → 5, CoreSimulator 38G → 11G),
   then `tmutil thinlocalsnapshots / 21474836480 4` — APFS holds freed
   space in local snapshots, so `df` barely moves until you thin them.

## Ranked queue (wave 50+)

0. **Wave-49 discoveries, ranked above the older queue:**
   (a) **`tools/titan/extract-fixture.mjs` collapses competing
   declarations last-wins with no validity oracle** (a bare
   `props[k] = v` around line 830). This is why
   `css-values/angle-units-001` cannot be fixed in the converter: all
   five competing `background-image` declarations are gone before
   conversion, so the four INVALID ones cannot be dropped in favour of
   the valid green one. Lane A1 proved it and refused to claim the flip;
   a prepared patch sits at `scratchpad/seam-A1-1.patch` (+148) with its
   test at `seam-A1-2.patch`, **deliberately deferred** — corpus-wide
   capture-pipeline blast radius for 3 cells, unaudited, and a second
   unowned change to a file lane A3 had already modified. Take it only
   with a differential re-extraction over the full corpus.
   (b) **Compose `LocalContainingBlock` should republish for
   non-block-container ancestors** (CSS 2.1 §10.1). `PercentInsetResolve`
   correctly declines when both axes are null because it cannot tell
   CSS-indefinite from a channel gap; the iOS twin flips because
   SwiftUI's `LayoutAggregate` publishes one. Blocks
   `css-position/position-relative-006` (android) — the wave's only
   prediction miss — and any percentage inset under such an ancestor.
   TODO is in place at `PercentInsetResolve.kt`.
   (c) **`css-transform-3d-transform-style` (android) passes at 0.9577
   against iOS's 0.9574 and is FRAGILE.** `OrthographicFlatten` does not
   read `transform-style`; it converges on iOS's known-wrong render and
   is labelled an approximation. Real preserve-3d compositing on Compose
   is the actual fix; treat the cell as a candidate to lose.
   (d) `clip-path-contentBox-1d/1e` still fail on Android (0.8775,
   0.8875) despite the wave-49 extractor repair — the crash is fixed and
   the centre now decodes correctly, but the rendered shape still
   diverges from the browser ref.
   (e) Scientific-notation angles (`1e2deg`) survive as a Raw
   passthrough because `AngleParser.angleRegex` has no exponent branch.
   Zero corpus carriers today (grep over `tools/wpt/css/` finds none),
   so it is documented as a modelling limitation rather than treated as
   invalidity. Widening AngleParser touches transforms, colour hue
   channels, conic prefixes and gradient stops — wider than one lane.
   (f) **Pre-existing wrong spec citations outside the wave-49 diff**,
   found but deliberately not touched: `PseudoTextFold.swift:21`,
   `PseudoBucketExtractor.kt:115,142`, `GradientValueParsers.kt:21,31`,
   `BackgroundImagePropertyParser.kt:21,273`,
   `MaskImagePropertyParser.kt:63`, `GradientRamp.kt`. A citation sweep
   of the pre-wave-49 tree is a cheap standalone lane.


1. **Vertical-wedge residuals** (the wave-47 wedges are FIXED as
   mechanisms — W1 wave 48 — but the cells still fail): (a)
   `css-break/background-image-006` now captures and scores (gradient
   all-outside crash fixed on both twins, pinned) but passing needs
   **multicol clone re-flow of content-bearing children** — the
   documented `MulticolCloneMeasure` bail; (b)
   `css-writing-modes/direction-upright-002` now captures a real
   390×2316 canvas (explicit MeasurePolicies killed the 32767-sentinel
   → 66404px chain; atomic PNG publication fixed the truncated pulls)
   but Android still draws descendant runs rotated where the ref wants
   upright. (c) **NEW: `css-text-decor/text-decoration-inset-025`** —
   Android composes 9470px vs web 750 / iOS 600 (ssim 0.0), the corpus's
   second true degenerate-canvas positive (wave-48 S6 IHDR scan of all
   4304 captures); undiagnosed Android layout blow-up.
2. **Counter/marker follow-ups from the Rule-43 unexclusion** (W2 wave 48
   unexcluded 15 of 28; the 13-entry refusal list in
   `tools/titan/inject-wpt-block.mjs` carries per-key measured scores):
   (a) **Android blank-paint bug**: `counter-cjk-decimal` renders ZERO
   ink from IR iOS paints perfectly (0.9407 P-veto vs 0.9934) — a real
   generated-content drop, now an honest scored fail dragging the
   column; (b) **both-native zero-width wrap**: `css-lists/counter-004`
   lays the georgian counter string one glyph per line on BOTH natives
   (0.753 each, web fails separately 0.778); (c) adjudicate the three
   self-declared re-label candidates (armenian-008, counter-suffix,
   counter-004 — provably non-font-bound, kept under the conservative
   clause); (d) shared upstream marker gaps: counter-suffix doubled item
   text + missing korean/RTL markers, name-case-sensitivity missing
   marker content on all three.
3. **Multicol float residuals** (W3 wave 48 closed seam 2 — the iOS
   slice-replay renders ref-exact rows; queue slot retired pending the
   gate): (a) the converter/inline bake **drops `<br clear="all">`** —
   per-test IR for floats-clear-multicol-000/001/balancing-000/001 has
   no Clear wire, so both natives put the orange at col-0 vs the ref's
   col-2 and pass only on float-slice dominance; (b) Compose renders the
   003/balancing-003 orange 3px high (y158-160 vs ref 161-163; iOS is
   now ref-exact) — measured-height rounding in the Kotlin strip walk.
4. **Inline-run wall, corrected map** (wave-48 W4 measured the briefed
   rings as no-ops: the br ring shipped in wave 47; the 75 tagless
   members are correctly-stacked BLOCK boxes. The UA-styled ring —
   i/em/cite/var/dfn + sup/sub with the Blink parent/3+1 / parent/5+1
   shift, twin `InlineSpanRing.shiftPx` helper, pinned both platforms —
   shipped instead). Remaining, in value order: (a) the
   **br-stacked-equivalent wall** — lifting it flips 29 hosts across 27
   tests, ~16 currently passing: needs a device A/B, never a blind lift;
   (b) `subelements-003`: needs block-level text-decoration propagation
   (the wrapper's blue underline is missing from EVERY render) AND
   per-range decoration colour diverging from text ink — both outside
   the fold; (c) `block-ellipsis-032`: hanging-whitespace ring
   (WhiteSpace:PRE members through the pre-break); (d) Compose
   line-clamp-006/007: missing cross-block line-box census (the wave-46
   iOS twin has it); (e) Compose baseline-shift is face-ascent-dependent
   (hard-coded Inter 0.96875) while iOS is exact points — watch sup/sub
   cells on device for the ~2px class (named limitation in
   `InlineSpanContent.kt`).
5. **Web tail residuals** (W5 wave 48 measured +14 web: contain-body ×8,
   lch/oklch %, degenerate calc, image(), contain-intrinsic bridge):
   (a) `tools/titan/extract-fixture.mjs` stamps 100×100 on `<hr>`,
   spreading gradient-hue-direction off-canvas (all-three 0.62); (b)
   `none` colour components need an IR wire shape (gradient-none-
   interpolation, all-three ~0.81); (c) gradient-eval-predefined-
   color-spaces: script-templated test extracts zero styled components;
   (d) fallbacks-005 is UNPASSABLE AS SCORED (five tests share one
   solid-green ref whose prose is 001's — WPT authoring artifact;
   exclusion-lane decision, never a code carve-out; web honest loss
   0.9144→0.9038 recorded); (e) native powerless-hue/lch gradients
   (0.65–0.86) need polar interpolation with alpha-carrying stops; (f)
   color-mix in lch needs a static mixer (srgb→lch in ColorConversion);
   (g) contain-content-004/html-overflow-002: all three paint blue-
   FILLED blocks where the ref wants blue-BORDERED hollow cells; (h)
   ~~native image() carries only the FIRST src~~ **FIXED wave 49** (A3,
   `ImageCandidateChain` on both natives + the bare-STRING `<image-src>`
   inlining `extract-fixture.mjs` was missing): fallbacks-and-annotations
   002/003/004 flipped on iOS AND Android, 6 cells, and the 001 triple
   was verified not to regress. Note 004's winning candidate
   `support/1x1-green.gif` has a global colour table of (0,127,0) where
   the ref paints (0,128,0) — one step off, still passing. `005` remains
   unwinnable-as-scored (BACKLOG 5d). (i) **mask-image wire gaps**
   (wave-48 F1/
   S6 probes): `MaskImageValue.ColorStop` drops `positionLength`
   (every px-positioned mask gradient stop), and ImageNotation is absent
   from `mapToMask` so `mask-image: image(...)` drops the whole property.
6. **Open runtime bugs from the harness overhaul** (remaining three): (a)
   ~~Compose transform-list~~ FIXED wave 48 (`TransformListComposer`,
   ordered §11 product; exit-5 deletions done). Residuals: skew-bearing
   lists still accumulate per-kind (route: add a Skew step, replay via
   canvas — deferred to keep Edge_MultiTransform byte-stable);
   `decomposeMatrix2D` mishandles reflections on the matrix() path
   (pinned; no corpus carrier); 3D-bearing lists keep legacy
   approximations; the extractor's largest-axis rotate3d heuristic
   admits `rotate3d(1,1,1,θ)` as planar (inherited, now documented).
   (b) Android clips a transformed child's paint to its layout slot.
   The wave-49 obligation #3 sibling (ancestor clip-path on abspos/
   blended children) is **FIXED wave 49** — clip-path-blending-offset
   Android 0.9566→1.0000, PR #126's BlendModeApplier bounds fix verified
   NOT reverted; the transformed-child clip remains open. (c) Android box-shadow ~2.4× over-blur
   (`MultipleShadowApplier.kt`). (d) iOS overflow clip applied OUTSIDE
   the transform.
7. **css-gaps residuals** (W7 wave 48 built column-wrap on both natives +
   iOS percent flex-basis; gate arbitrates 025/043/045/046): (a) the
   **gap-decoration line-band painter** models lines as the UNION of
   item rects, so §9.4-stretched lines paint rules ~3.5px off — the
   named next step if 045/046 land under 0.95 (thread renderer-computed
   line bands into GapDecorationSegments on both platforms); (b)
   `background-clip-content-box-002` iOS 0.9992 F: a decoded `.percent`
   flex-basis no nowrap path reads — likely free fix when
   CSSFlexLayout honours percent; (c) iOS column-wrap stretch injection
   (no corpus carrier yet); (d) wrap-reverse ordering both natives
   (pre-existing TODO, now load-bearing for the routing gates); (e)
   css-gaps 006 distribution defect (unrelated to wrap).
8. Vertical mechanism completion: multi-child balancing, clone-under-
   vertical, §8.3.1 horizontal collapse (wave-47 Z2 logged bails); the
   upright intrinsic Int-overflow at ~2.1e9px summed advances (wave-48
   S4 probe; coerceAtMost candidate).
9. Smaller: `visibility: collapse|hidden` semantics on both natives;
   `text-decoration` shorthand colour-function drop; the web test that
   cannot fail (`SkepticFontShorthandLh` `font: inherit` case); flex
   cross-axis auto-margin exemption (TODO in `FlexCrossStretch.kt`);
   iOS `InlineRunFlow.swift` decorations guards carry the []-vs-nil
   twin divergence class the runs guards had (F5 flag; zero corpus
   impact); file-size cleanups (`BackgroundImagePropertyParser.kt` 386
   lines, `BackgroundImageExtractor.ts` over threshold,
   `TransformApplier.kt` >1000 — split lane).
10. **Corpus expansion — recommendation DECIDED, execution open** (wave-48
    W8 arithmetic): Option B — add css-shadow (A-pool 106), css-align
    (54), compositing (48) at depth-48 = +144 slots, +10% gate time,
    ~2.0 discriminating web cells per +1% gate vs 0.45 for depth-96;
    each section carries a named open bug (6c over-blur, align-self,
    the PR-#126-disturbed blend/clip area). Budget triage: ~250
    font-parity-tagged docs outside the current sample now arrive
    SCORED (the Rule-43 gate is per-test, decline-on-unknown). Also:
    hanging-punctuation ×2 are impossible passes (Chromium doesn't
    implement it) — exclusion candidates that would remove 2 permanent
    web fails from the honest denominator.

## Parked (decided, do not revisit without new evidence)

- Noto corpus-wide bundling: REJECTED by measurement (wave-45 pilot, net
  zero). Binding constraints named and both since fixed — hence the
  wave-48 Rule-43 re-measure (queue #2 residuals are what remains).
- SVG pre-raster: default ON since wave 44 (earned via A/B).
- WOFF→TTF hop: default ON since wave 42 (validated cache).
- Mono pin: default ON since wave 47 (earned via A/B; escape
  `TITAN_MONO_PIN=0`).
- dev→main promotion + v0.2.0 tagging: declined by owner.
- Cross-machine noise floor: deferred until visual jobs move to CI.
- image() loadable-src-plus-colour precedence: natives resolve
  colour-first (loadability unknowable at extract), web url-stacks so a
  loadable src wins — divergent only for a shape no corpus test
  carries; cross-referenced in all three extractors. Revisit only with
  a fixture (and ledger the divergence the same day).

## Operational recipes (hard-won; read before gating)

- **Fresh-worktree provisioning** (wave-48 additions): beyond
  `local.properties` (root + apps/android-harness) — the WPT mirror
  (`tools/wpt/`, ~600M: move from the old worktree or
  `tools/titan/fetch-wpt.sh`), the iOS xcodeproj
  (`cd apps/ios-harness && xcodegen generate`), and **node_modules**: a
  real DIRECTORY of per-entry symlinks into a sibling store is fine,
  but the three workspace packages (`@style-converter/web`,
  `web-harness`, `style-converter-tools`) MUST link into THIS tree —
  a whole-dir symlink into another worktree serves that tree's web
  runtime to every vite capture (silent, proven wave 48). Clear
  `apps/web-harness/node_modules/.vite*` after repointing. Durable fix:
  a real `npm install` in-tree.
- **Gate script**: df preflight (≥10G or abort), kill all emulators +
  extra sims first, clear `/tmp/titan-device-pool/provisioned-*`,
  provision via `tools/titan/provision-devices.sh` (it `pm clear`s after
  install — API-36.1 reinstalls break the app's external dir without it),
  30 sections × `--max-tests 48 --run-id waveNN-final`, 50-min watchdog
  that kills AND reprovisions, then `BASELINE=1 ./test-all.sh
  fixtures/visual-test.json`. Score with the standard idiom
  (`x.ssim`+`!x.scoreExcluded`, pass = `wptPass===true`); verify CAPTURE
  COUNTS per column against tests.list, never process liveness.
- **Single-fixture recovery** (adb-pull truncation, one missing diff):
  `feed-android.mjs --fixtures <one>.json --composed --device <dev>
  --wpt-dir tools/wpt --out <run>/android-screenshots` then re-run
  `inject-wpt-block.mjs` with the gate env
  (`POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1`) and the section's
  `--combined fixtures/wpt/_section-<sec>.json`. **Refeeds must carry
  `--wpt-dir`** (asset hops silently skip otherwise → artifact scores).
- **Column recovery** (whole platform column dead): same recipe with
  `--fixtures <run>/per-test-ir`; for iOS use `feed-ios.mjs --udid
  0BB986A6-1EAD-4916-9276-079235324DA1` (boot it first).
- zsh does NOT word-split unquoted vars — write refeeds as explicit
  per-fixture commands, never `set -- $pair` loops.
- **Limit-killed workflow lanes**: resume with
  `Workflow({scriptPath, resumeFromRunId})` — completed lanes replay from
  cache; add per-lane `model:'opus'` overrides only to dead lanes.
- Kotlin IC-cache corruption (`Storage already registered`, `Page -N`):
  delete `runtimes/compose/build/kotlin`, retry. Gradle filtered-run
  state: final verification always `--rerun-tasks`. Concurrent daemons
  on one tree → AGP jar-scan races: treat as contention first, re-run
  single-writer before diagnosing.
- Suite counts live in doc tables (README/CLAUDE/STATUS + tree READMEs);
  doc-staleness-check enforces them — stamp AFTER the final sweep, and
  re-derive after skeptic/fix lanes add tests. The android-harness
  `:app` module tests (AtomicPng etc.) are NOT in the documented sweep —
  run `:app:testDebugUnitTest` alongside `:runtime:` when harness code
  changed.
- iOS capture knobs travel as `SIMCTL_CHILD_*`; `FORCE_STATE` has **no**
  `CAPTURE_` prefix.
- Fixture authoring: `_expect` with shown arithmetic; solid colours
  ≥3/channel from white and outside ±8/channel of page bg rgb(26,26,46);
  transforms escaping the box need margin; tie-free animation times;
  control fixtures run `NO_CROSS_PLATFORM_GATE=1`.
- Wire conventions are PINNED in `schema/spec/02-values.md` (wave 48):
  lab/lch lightness 0..100, **oklab/oklch lightness 0..1**, chroma
  absolute (lch 150 = 100%, oklch 0.4 = 100%). The web typed-original
  re-emission and the converter's percentage scaling are a COUPLED pair
  — reverting either half alone regresses gradient-powerless-hue-oklch.
