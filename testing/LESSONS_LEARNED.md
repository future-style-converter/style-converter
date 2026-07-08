# Lessons learned — testing-infrastructure patterns

Captured at round 62, extended at round 76 with 2 more patterns the
campaign caught after closeout. The 75+ round audit-driven testing
campaign uncovered a recurring set of failure patterns that future
contributors should recognise on sight, not re-discover.

This doc is for the next person debugging "why did the SSIM drop?" or
"why does Tier X show different numbers than yesterday?" Read
[CAMPAIGN_SUMMARY.md](CAMPAIGN_SUMMARY.md) for the chronological story
and [README.md](README.md) for the audit infrastructure. This doc is
purely "what tricks to look for".

---

## Pattern 1: degenerate fixtures as silent passes

**Symptom**: SSIM ≥ 0.95 across all platforms (test "passes") but the
component renders as an empty box on every platform. The platforms
agree because there's nothing to disagree about.

**How it slipped in**: A fixture for property X was authored with only
that property declared, expecting the property's effect to dominate the
render. But the property requires CONTEXT (sibling element, scrollable
parent, animation timeline, SVG element, …) and absent that context,
nothing renders. The capture is a 390×N rectangle of the canvas
background on every platform → 1.000 SSIM.

**Where it bit us**: Tier 1 round 0 reported "548/548 100% passing". 40
rounds of audit demoted ~445 of those to `blocked-platform` with honest
"Need svg-context / Need scrollable parent / Need animation timeline /
…" notes. The honest headline is 91/550 (16.5%) — within the auditor's
"strict 7-21%" estimate.

**How to spot**: when a passing-100% fixture suite has SSIM ≥0.99 on
EVERY pair, sample a few captures visually. If the captured PNG is
mostly canvas-background, the fixture is degenerate.

**How to fix**: either (a) rewrite the fixture with the required
context (`testing/TIER1_VARIANT_DEPTH.md` rows marked REWRITTEN show
the gold-standard examples), or (b) demote the row to `blocked-platform`
with a root-cause note.

---

## Pattern 2: position:fixed bleed across gallery captures

**Symptom**: Web captures of unrelated components show the same
overlay text contaminating them. iOS+Android render cleanly.

**Root cause**: web's `?mode=capture` renders ALL components on ONE
long page (CaptureGallery is a flat vertical list). When any single
component declares `position: fixed; inset: 0; width: 100%; height: 100%;
background: #foo;`, that overlay spans the entire viewport — covering
every per-element screenshot. iOS+Android render each component in its
own isolated SwiftUI/Compose view tree, so `position: fixed` only
spans within that one component.

**Where it bit us**: CNN's `modal__overlay` IR component (`position:
fixed; bg: #0c0c0cf2`) was contaminating every CNN capture. CNN was
stuck at 12% pass / median SSIM 0.702 / 99 deep failures.

**Fix landed in round 47** (`testing/web/src/ui/CaptureGallery.tsx`):
add `transform: translateZ(0)` + `position: relative` to the canvas
style. Per CSS Transforms 1 spec, any non-`none` transform makes the
element a containing block for fixed-position descendants. CNN went
9% → 30%, deep failures 99 → 12.

**How to spot**: read the actual divergent images (`testing/report/
images/{platform}/<name>.png`) — visually identify whether the bleed
is coming from a different component's name/text/bg. Then grep the IR
for `position: fixed` or `position: absolute; inset: 0`.

---

## Pattern 3: opacity:1 (and other no-op modifiers) false-positive
visibility

**Symptom**: A component with no actual visible properties (`border:
none; height: 2px; opacity: 1; transition: opacity .25s`) passes the
isVisible filter and gets rendered. iOS + web show PlaceholderLabel
text (component name); Android shows nothing → SSIM 0.61.

**Root cause**: `opacity: 1` is the default. Authors declare it as part
of transition setup (animate from 1 → 0 on hide). But isVisible() was
treating `opacity` as a visibility marker.

**Fix landed in round 51** (`testing/css-to-ir.mjs`): remove `opacity`
from the `VISIBLE_PROPS` Set. transform/rotate/scale/translate are
similar modifiers (kept for now — edge cases where 3D transforms reveal
hidden faces).

**How to spot**: a fixture is in the IR despite having no
background/color/border/content. Run `bash testing/loops/audit_rewrite_claims.sh`
or just `node testing/css-to-ir.mjs` and inspect the generated IR for
no-render components.

---

## Pattern 4: ARG_MAX in `ls $glob` for large directories

**Symptom**: `count_glob` (or any `ls dir/*.ext | wc -l`) silently
returns 0 even when the dir has thousands of files. Subsequent
"captured 0" log lines mask the real success.

**Root cause**: `ls $glob` lets bash expand the glob into argv at the
shell-command level. For 10000 files × ~80 chars = ~800 KB of argv,
which exceeds macOS ARG_MAX (~256 KB). `ls` aborts; `2>/dev/null`
swallows the error; `wc -l` counts nothing.

**Fix landed in round 47** (`test-all.sh count_glob`): switch to `find
"$dir" -maxdepth 1 -name "$pattern" -type f`. find handles enumeration
internally without building a giant argv.

**How to spot**: any "captured 0" message that contradicts the actual
file count in the dir. Same pattern can hit `rm dir/*.ext` and similar.

---

## Pattern 5: stale baseline after a renderer fix lands

**Symptom**: `BASELINE=1 ./test-all.sh` reports a regression on a
component that LOOKS unchanged. iOS / Android / web cross-platform
SSIM is high (0.94+); only the per-platform-vs-baseline SSIM dropped.

**Root cause**: the baseline was committed BEFORE a renderer fix
landed. After the fix, the platform output legitimately changed — the
"regression" is the renderer being CORRECT against an outdated baseline.

**Where it bit us**: round 49 caught iOS Glass_Effect baseline
(committed 2026-05-05) was stuck at the pre-fix render with `.thinMaterial`
overlay; the iOS FilterApplier `.thinMaterial`-removal fix landed
2026-05-09. Surgical refresh of just the one baseline + re-verify
BASELINE=1 returns to 327/327.

**How to spot**: the regression is single-platform (other 2 platforms
pass baseline at SSIM=1). Cross-reference `git log` for renderer
commits between the baseline-commit date and now. Visually diff the
baseline PNG vs the current capture — if the visual change is "good"
(matches what the renderer fix intended), refresh the single file.

**How NOT to fix**: don't `UPDATE_BASELINE=1 ./test-all.sh` wholesale
after a regression hit — it accepts every change including any
unintended ones. Single-file refresh + re-verify is safer.

---

## Pattern 6: sample-composition shifts looking like regressions

**Symptom**: A site's pass rate drops dramatically (e.g. 92% → 41%).
Looks like a regression but renderer behavior is unchanged.

**Root cause**: the IR sample changed. css-to-ir.mjs's filter logic
landed (round 45 isVisible, round 51 opacity refinement) — same site,
same CSS, but different components in the IR now. The new sample
includes more components clustered around the 0.95 threshold.

**Where it bit us**: round 53 Tailwind appeared to "regress" 92% → 41%
but median SSIM was 0.952, **0** deep failures, 88/150 in the 0.90-0.95
band (just under threshold). It was just a wider sample with more
close-misses.

**How to spot**: when a pass-rate drop is large (>10%), check the
denominator. If the count changed, the sample changed. Compare median
SSIM and deep-failure count instead of just headline percentage.

**Fix**: never compare pass rates across rounds where the IR adapter
changed without normalizing for sample size. Document the change
honestly: "denominator shifted; not regressed".

---

## Pattern 7: iOS Simulator non-determinism on backdrop-filter / blur

**Symptom**: A specific iOS fixture's SSIM-vs-baseline drifts ~0.05
between runs even with no code change. Other 326 baselines stay at 1.000.

**Root cause**: iOS UIVisualEffectView / backdrop-filter compositing is
not bit-exact across Simulator runtime versions or even cold/warm boots
of the same runtime. Components using `backdrop-filter: blur(…)`,
`-webkit-backdrop-filter`, or large translucent overlays are most
affected.

**How to spot**: per-component SSIM-vs-baseline drift while
cross-platform pair SSIMs stay similar.

**How to handle**: accept as inherent platform behavior; don't refresh
baseline reflexively. Only refresh when there's a real renderer fix
explaining the drift (Pattern 5).

---

## Pattern 8: PlaceholderLabel divergence on bg-only components

**Symptom**: A component with bg-color but no children renders
differently across iOS / Android / web. iOS PlaceholderLabel renders
text-only (ignores bg+shadow); Android Text+wrapContentSize renders
bg+text+shadow at native font metrics; web `<span>{name}</span>`
renders bg+text at CSS metrics. Each picks slightly different sizes
(60 vs 62 px line-height is common).

**Where it bit us**: 6 of CNN's 10 remaining deep failures (post-Phase
9e) trace to this. Phase 9i is the deeper fix but risky for Tier 1
fixtures that deliberately test invisibility.

**How to spot**: cross-platform divergence on components that have
ONLY bg-color + no children, with dimensions matching within 2 px.

**How NOT to fix**: don't reflexively try to "fix" the placeholder —
it's the testing-affordance behavior that lets you SEE empty fixtures
in a gallery debug view. Architectural sign-off needed before changing.

---

## Pattern 9: requestAnimationFrame hangs in headless puppeteer

**Symptom**: A page renders correctly when you visit it in a regular
browser, but puppeteer's `waitForSelector` for an effect-set attribute
times out at 5s on every fixture (e.g. all 90 Tier 5 captures fail
with `fixture-ready-timeout`).

**Root cause**: Headless Chrome throttles or outright suppresses
`requestAnimationFrame` callbacks when the page isn't being painted
to a display. A useEffect that uses `requestAnimationFrame(() => …)`
to defer DOM mutations until "after paint" never fires; the deferred
mutation (e.g. `setAttribute('data-fixture-ready', '1')`) never happens.

**Where it bit us**: Round 75 caught FixtureCanvas's double-RAF
(originally added round 40 for visible-display correctness) hanging
in headless mode. Tier 5 went 90/90 → 0/90 silently. Round 75 also
documents that CaptureGallery had hit the same root cause earlier
and worked around it with `setTimeout(50)` — see
`testing/web/src/ui/CaptureGallery.tsx` ~L117.

**How to spot**: anywhere your test code uses
`element.querySelector('[data-something-ready]')` to wait on a
React useEffect side-effect.

**How to fix**: replace `requestAnimationFrame` with `setTimeout(50)`
inside the useEffect. setTimeout stays on the JS task queue and
isn't paint-throttled. 50ms is enough for layout + paint to settle.

## Pattern 10: puppeteer protocolTimeout default dropped

**Symptom**: A puppeteer-driven harness starts producing
`Runtime.callFunctionOn timed out` or `Page.captureScreenshot timed out`
errors after a recent puppeteer minor version upgrade. Same code that
worked yesterday now fails partway through.

**Root cause**: puppeteer's default `protocolTimeout` (the deadline for
each CDP-protocol round-trip) was lowered in a recent version. Combined
with headless Chrome's RAF/paint throttling, per-state CDP round-trips
take longer than the new floor, so the harness errors out on
specific element interactions.

**Where it bit us**: Round 75. Even after fixing FixtureCanvas's RAF
hang (Pattern 9), Tier 5 hit `Runtime.callFunctionOn timed out` partway
through the 90-state harness. The fix: bump `protocolTimeout` to 5min
(generous enough for any single fixture even on cold machines) in
both `interaction-states.mjs` and `a11y-audit.mjs`'s
`puppeteer.launch()` calls.

**How to spot**: error messages that explicitly mention
"`Increase the 'protocolTimeout' setting`".

**How to fix**: pass `{ protocolTimeout: 5 * 60 * 1000 }` (or higher)
to `puppeteer.launch({ headless: true, … })`. Check ALL
puppeteer.launch calls in the codebase, not just the one currently
failing — the next one to time out tomorrow will be the silent neighbour.

---

## Audit infrastructure quick reference

When something looks wrong, run in this order:

1. `bash testing/loops/audit_rewrite_claims.sh` — fabrication?
2. `node --test testing/*.test.mjs` — adapter logic broken?
3. `./testing/smoke.sh --quick` — Tier 5/Tier 11 web regression?
4. `BASELINE=1 ./test-all.sh examples/visual-test.json` — renderer drift?
5. Visually inspect `testing/report/images/{iOS,Android,web}/<name>.png`
   when SSIM is low — the eye catches what numbers can't.

Each of these has saved a real bug across rounds 39-61. They're cheap
to run (~50ms to ~5min) and the false-positive rate is near zero.
