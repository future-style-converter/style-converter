# Dynamic-styling capture contract (states + media + motion)

How the visual harnesses capture the dynamic-styling fixtures
(`fixtures/fidelity/dynamic/` — see `schema/spec/06-dynamic-styling.md`
for the runtime semantics being tested) and the motion fixtures
(`fixtures/fidelity/motion/` — `schema/spec/07-animations.md`). Three
harness-level hooks:

1. **`forceState`** — deterministically force one interaction state for
   a whole capture run (selector buckets).
2. **`CAPTURE_WIDTH`** — capture at a non-default render-surface width
   (media `min-width`/`max-width` buckets).
3. **`CAPTURE_ANIMATION_TIME`** — freeze every animation at an absolute
   time t, paused (keyframe animations + transitions, §4).

All three are *contracts for all three harnesses* and **all three
harnesses implement them**: web is the reference implementation, iOS
takes launch arguments / `SIMCTL_CHILD_*` env, and Android takes launch
**intent extras** on `MainActivity` (`--es forceState`, `--ei
captureWidth`, `--es animationTime`, wired in wave 7 / `e4d87c2c`; see
`apps/android-harness/README.md` for the `adb` recipes and the logcat
markers). Verification markers are part of the contract on every
platform, and `test-all.sh` hard-fails a hooked Android run whose logcat
marker is missing.

## 1. The `forceState` hook

Driving real input events per component is flaky (and impossible for
`hover` on touch devices), so spec 06 §6 requires every runtime's style
resolution to accept a forced-state set. The harness contract exposes
exactly **one forced state per capture run**:

- **Values**: `hover` | `active` | `focus` | `disabled` | `checked`
  (the runtime-v1 condition set, spec 06 §2).
- **Semantics**: while set, style resolution on **every** captured
  component treats that condition as active — layered per spec 06 §3 —
  regardless of real input state. Everything else about the capture
  (canvas size, background, padding, naming) is unchanged.
- **Run naming**: captures from a forced run are compared against
  captures from other platforms' runs with the **same** forced state.
  Keep runs in separate output directories (the per-run recipe below);
  the PNG filenames themselves stay identical to the base run so the
  SSIM pairing logic needs no changes.

### Per-platform transport

| platform | transport | status |
|---|---|---|
| web | `?forceState=<state>` query param on the capture URL (`?mode=capture&forceState=active`); `CAPTURE_FORCE_STATE=<state>` env on `apps/web-harness/capture-screenshots.mjs` sets it | **reference implementation, wired end-to-end** — the capture screen (`CaptureGallery.tsx`) validates the value and stamps `data-force-state="<state>"` on every capture canvas; the harness renderer adds a `force-<state>` class to every component element, which twins the real pseudo-class on the SAME RuleBuilder rule (`runtimes/web/src/core/renderer/RuleBuilder.ts`) — identical declarations, identical specificity, so forced and real input resolve byte-identically |
| Android | launch intent extra `--es forceState <state>` on `MainActivity` (`MainActivity.kt` `readForceStateExtra()` validates against the runtime-v1 condition set and passes it into the runtime's resolution entry point) | **wired** — logcat marker `Capture run config: forceState=…` (tag `ScreenshotCapture`) and the canvas test tag `capture-canvas-force-state-<state>`; `test-all.sh` HARD-FAILS a `CAPTURE_FORCE_STATE` run whose marker is missing |
| iOS | launch argument `-forceState <state>` (or `FORCE_STATE` env — `SIMCTL_CHILD_FORCE_STATE=<state>` on the host shell flows through `xcrun simctl launch`, so `SIMCTL_CHILD_FORCE_STATE=active SKIP_ANDROID=1 SKIP_WEB=1 ./test-all.sh …` is the full recipe); `CaptureOverrides.swift` validates the value and the capture canvas publishes the runtime's `forcedStyleStates` environment, which `StateResolver` layers per spec 06 §3. Marker: the canvas accessibility identifier reads `force-state-<state>` on a forced run (`capture-canvas` otherwise) and the capture screen logs `[Capture] forceState=…` | **wired** |

The DOM/view marker (`data-force-state` on web, an equivalent test tag
on native capture screens) is part of the contract: it lets a capture
script *verify* the forced run actually ran forced, instead of silently
diffing two base-state captures.

### State capture recipe (web today)

```bash
# base state (unchanged flow)
SKIP_ANDROID=1 SKIP_IOS=1 ./test-all.sh fixtures/fidelity/dynamic/states.json

# one run per forced state; move captures aside between runs
for s in hover active focus disabled checked; do
  ( cd apps/web-harness && CAPTURE_FORCE_STATE=$s node capture-screenshots.mjs \
      --url http://localhost:3000 --out screenshots-$s )
done
```

(`tools/visual/interaction-states.mjs` remains the event-driven web
prober — real hover/mousedown/focus events per component. It verifies
that *real* input agrees with *forced* resolution on web; forceState is
what natives can implement.)

## 2. Two-width media capture (`CAPTURE_WIDTH`)

`fixtures/fidelity/dynamic/media-width.json` is designed around two
render-surface widths (spec 06 §4 — the surface is the capture canvas,
not the device):

- **390 px** (the default canvas) — `(min-width: 200px)`,
  `(max-width: 500px)`, `(min-width: 300px)` buckets match;
  `(min-width: 500px)`, `(max-width: 300px)` must NOT.
- **250 px** (the second width) — `(max-width: 300px)` flips ON and
  `(min-width: 300px)` flips OFF; the rest keep their 390 px answers.

### Contract

`CAPTURE_WIDTH=<px>` overrides the capture-canvas width for the whole
run. Default (unset) is **390** and MUST remain byte-identical to the
historical capture path — committed baselines were all captured at 390.

| platform | wiring | status |
|---|---|---|
| web | `CAPTURE_WIDTH` env read by `capture-screenshots.mjs` (viewport width + `&width=<px>` on the capture URL; `CaptureGallery.tsx` sizes the canvas from it) | **wired** |
| Android | launch intent extra `--ei captureWidth <px>` on `MainActivity` (`MainActivity.kt` reads it with a 390 default and rejects non-positive values); the capture canvas sizes from it, so media min-/max-width evaluate against the render surface, not the device screen | **wired** — logcat marker `Capture run config: … captureWidth=…` |
| iOS | `CaptureCanvas.swift` width from the `-captureWidth <px>` launch argument (or `CAPTURE_WIDTH` env via `SIMCTL_CHILD_CAPTURE_WIDTH`); the canvas publishes the width through `styleViewport`, which IS the surface `MediaQueryEvaluator` compares min-/max-width against | **wired** |

`test-all.sh` needs **no changes**: the env var flows through the shell
to `capture-screenshots.mjs` automatically.

### Two-run recipe (the gate for media-width.json)

```bash
# run 1 — default 390 px: the normal 3-platform gate + committed baselines
./test-all.sh fixtures/fidelity/dynamic/media-width.json

# run 2 — 250 px: all three platforms (every harness implements the override)
CAPTURE_WIDTH=250 ./test-all.sh fixtures/fidelity/dynamic/media-width.json
```

(On Android the 250 px run is a manual relaunch against the emulator
`test-all.sh` leaves running with `EMULATOR_KEEP=1`:
`adb shell am start -n com.styleconverter.test/.MainActivity --ei captureWidth 250`.)

Gating rules:

- The 390 px run gates cross-platform exactly like any other fixture
  (SSIM ≥ 0.95 on every platform pair; baselines at 390 only —
  `UPDATE_BASELINE=1` must never be combined with `CAPTURE_WIDTH`).
- The 250 px run **is** a cross-platform gate: this doc's own rule was
  "once ≥2 platforms implement the width override", and all three do.
- Do not mix widths inside one report: `compare-screenshots.mjs` pairs
  by filename, and a 390-vs-250 pairing is a guaranteed size-mismatch
  row. One width per run, one report per run.

## 3. Dark mode (`dark-mode.json`)

The default capture environment is **light** scheme on all three
platforms, so the `(prefers-color-scheme: dark)` buckets and the dark
arm of `light-dark()` values must NOT apply in the standard run — that
non-application is itself gated (a runtime that applies dark buckets in
a light environment fails the SSIM pair against one that doesn't).
A forced-dark capture run (web: **wired** — `CAPTURE_DARK=1` env on
`capture-screenshots.mjs`; the scheme is ALWAYS pinned via
`page.emulateMediaFeatures` — light unless forced — because headless
Chrome otherwise inherits the HOST OS appearance, which silently
activated every dark bucket on a dark-mode dev machine; Android:
`-night yes` config; iOS: **wired** — `-captureDark 1` launch argument
or `CAPTURE_DARK=1` env via `SIMCTL_CHILD_CAPTURE_DARK`; the capture
canvas pins `.environment(\.colorScheme, …)` explicitly, light unless
forced, so ImageRenderer output never depends on app/system appearance)
follows the same one-variable-per-run rule as §1/§2.

## 4. Deterministic motion capture (`CAPTURE_ANIMATION_TIME`)

Live motion is uncapturable — two screenshots of a running animation
never match, across platforms or across runs. Spec 07 §5 therefore
defines one hook:

**`CAPTURE_ANIMATION_TIME=<seconds>`** forces **every animation on the
capture surface to its state at absolute timeline time t, paused.**

- *Absolute*: t counts from each animation's timeline zero — delay,
  direction, iteration and fill-mode arithmetic all apply exactly as if
  wall-clock time t had elapsed. `t=0` is the initial frame (which,
  with `animation-fill-mode: backwards|both` + a delay, is the
  from-state, NOT the base style — `MK_FillBoth` in
  `fixtures/fidelity/motion/keyframes-basic.json` pins exactly this).
- *Every animation*: keyframe animations AND running transitions, one
  clock for the whole surface — `keyframes-basic.json` keeps every
  duration at 1s so `t=0.5` is mid-run for all components in one shot.
- *Paused*: the frame is frozen; capture latency cannot smear it, and
  two runs at the same t are byte-comparable.
- Unset ⇒ **no seizing at all**: the historical capture path stays
  byte-identical (committed baselines carry no animations).

### Per-platform transport

| platform | transport | status |
|---|---|---|
| web | `?animationTime=<s>` query param on the capture URL; `CAPTURE_ANIMATION_TIME=<s>` env on `apps/web-harness/capture-screenshots.mjs` sets it | **reference implementation, wired end-to-end** — the capture screen (`CaptureGallery.tsx`) seizes via the Web Animations API: `document.getAnimations()` returns CSS animations, CSS transitions and WAAPI animations as uniform `Animation` objects, and the page sets `anim.pause(); anim.currentTime = t*1000` on each. The hook re-runs after every React commit AND is exposed as `window.__seizeAnimations(t)`, which capture-screenshots.mjs re-invokes right before screenshotting so late-created animations (font-swap reflows, late transitions) are caught. The `@keyframes` rules themselves are built by the web ENGINE (`RuleBuilder.buildKeyframeRules` serializes each decoded `IRDocument.keyframes` stop through the same `buildStyles` appliers as base properties) and mounted by the harness in the managed dynamic-rules stylesheet (`useDynamicRules`); the engine's registered animation-* appliers emit the per-component declarations that bind by ident |
| Android | launch intent extra `animationTime=<s>` on the harness activity (`adb shell am start … --es animationTime 0.5`); `test-all.sh` forwards `CAPTURE_ANIMATION_TIME=<s>` into that extra automatically. The capture canvas provides `LocalForcedAnimationTime`, and the runtime's `KeyframeAnimationDriver` EVALUATES state-at-t (pure `KeyframeTimeline` math — delay/direction/iteration/fill arithmetic) instead of running any frame clock, so the frame is frozen by construction — the native equivalent of the WAAPI seize (`play-state: paused` is likewise overridden at t, matching what `currentTime = t` does to paused web animations). Markers: the canvas testTag gains an `-anim-time-<s>` suffix and the harness logs `Capture run config: … animationTime=<s>`; `test-all.sh` HARD-FAILS the run if the env was set but that logcat marker is missing | **wired end-to-end** |
| iOS | launch argument `-animationTime <s>` (or `CAPTURE_ANIMATION_TIME` env via `SIMCTL_CHILD_CAPTURE_ANIMATION_TIME=<s>` on the host shell — `SIMCTL_CHILD_CAPTURE_ANIMATION_TIME=0.5 CAPTURE_ANIMATION_TIME=0.5 SKIP_ANDROID=1 SKIP_WEB=1 ./test-all.sh …` is the full recipe); `CaptureOverrides.swift` validates the value and the capture canvas publishes the runtime's `animationCaptureTime` environment. The renderer EVALUATES state-at-t in property space (pure `AnimationDriver` phase math — delay/direction/iteration/fill arithmetic — with per-segment easing in `KeyframeInterpolator`, css-animations-1 §4.4) and its TimelineView frame clock is paused outright, so the frame is frozen by construction — the native equivalent of the WAAPI seize (`play-state: paused` is likewise overridden at pinned t, spec 07 §5 composition). Markers: the canvas accessibility identifier gains an `+animation-time-<s>` suffix, the capture screen logs `[Capture] … animationTime=<s>`, AND the app writes `capture-config.json` beside the captures; `test-all.sh` HARD-FAILS the run if the env was set but the pulled config doesn't carry that t | **wired end-to-end** |

The verification marker is part of the contract (same rationale as
`data-force-state`): on web every capture canvas carries
`data-animation-time="<s>"` on a seized run, and
`capture-screenshots.mjs` HARD-FAILS if the env var was set but the
marker (or the `__seizeAnimations` hook) is missing — a seized run can
never silently degrade to a live capture. Native capture screens must
expose an equivalent test tag.

### Recipes

```bash
# keyframes: three deterministic time points (start / mid / end-ish)
for t in 0 0.5 0.9; do
  ( cd apps/web-harness && CAPTURE_ANIMATION_TIME=$t node capture-screenshots.mjs \
      --url http://localhost:3000 --out screenshots-t$t )
done

# transitions (fixtures/fidelity/motion/transitions.json): force the state
# that triggers the transition AND freeze mid-flight — one variable pair
# per run. The post-paint flip LANDED 2026-08-28 on all three platforms,
# so this now captures the genuine mid-flight value, not the endpoint.
#
# The flip engages only when BOTH knobs are set. A forced run with no
# pinned clock still applies the state at mount, so settled-appearance
# captures (tools/visual/interaction-states.mjs) keep capturing the
# resting state rather than a mid-flight frame at a racy wall-clock
# instant — and the whole static corpus stays byte-identical.
#
# How each platform gets there differs, and only the VALUE is contractual:
#   web     — real DOM flip. ComponentRenderer defers the force class when
#             a clock is pinned; capture-screenshots.mjs adds it post-paint
#             between two forced reflows (a style CHANGE event, which is
#             the only thing that creates a CSSTransition). __seizeAnimations
#             then seeks it like any other animation.
#   Android — real recomposition flip. CaptureCanvas mounts in base state
#             and writes the forced set after `withFrameNanos {}`;
#             TransitionDriver presents the blend at the pinned t.
#   iOS     — DECLARED flip. ImageRenderer is one synchronous pass over a
#             fresh graph per component, so no flip can be observed; the
#             renderer instead computes the pre-flip list as `effective(for:)`
#             minus the forced set (StateResolver is pure) and blends at t.
#             HONEST LIMIT: this exercises the blend, not the live flip
#             DETECTION path (.onChange / transitionSnapshot).
#
# Verified on all three at t=0.4s: MT_BgFade = rgb(153,107,102), the spec
# lerp of #7f8c8d→#c0392b. Geometry pin at t=0.5s: MT_WidthGrow is exactly
# 120px wide (base 80, target 160) — a width no static state produces.
# Delay pin: MT_Delayed (delay 0.5s) is still base at t=0.25s.
#
# Pick TIE-FREE sample times. t=0.5 on MT_BgFade lands on (159.5, 98.5,
# 92.0) — two exact .5 ties, exactly where three independent float→byte
# roundings are entitled to disagree by 1 and manufacture a fake
# cross-platform divergence.
CAPTURE_FORCE_STATE=hover CAPTURE_ANIMATION_TIME=0.5 \
  node capture-screenshots.mjs --url http://localhost:3000 --out screenshots-hover-mid
```

Gating rules mirror §1/§2: one variable combination per run, one
report per run, and `UPDATE_BASELINE=1` must never be combined with
`CAPTURE_ANIMATION_TIME` (baselines are motion-free by contract).
