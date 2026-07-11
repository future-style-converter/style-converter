# Dynamic-styling capture contract (states + media)

How the visual harnesses capture the dynamic-styling fixtures
(`fixtures/fidelity/dynamic/` — see `schema/spec/06-dynamic-styling.md`
for the runtime semantics being tested). Two harness-level hooks:

1. **`forceState`** — deterministically force one interaction state for
   a whole capture run (selector buckets).
2. **`CAPTURE_WIDTH`** — capture at a non-default render-surface width
   (media `min-width`/`max-width` buckets).

Both are *contracts for all three harnesses*; the **web harness is the
reference implementation** today. Wiring the native harnesses is the
platform lanes' job (tracked per-lane; the contract below is what they
implement against).

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
| Android | launch intent extra `forceState=<state>` on the harness activity; the capture screen passes it into the runtime's resolution entry point | platform lane |
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
| Android | harness sets emulator `wm size <px>x844` + `CaptureCanvas` width from the same intent extra | platform lane |
| iOS | `CaptureCanvas.swift` width from the `-captureWidth <px>` launch argument (or `CAPTURE_WIDTH` env via `SIMCTL_CHILD_CAPTURE_WIDTH`); the canvas publishes the width through `styleViewport`, which IS the surface `MediaQueryEvaluator` compares min-/max-width against | **wired** |

`test-all.sh` needs **no changes**: the env var flows through the shell
to `capture-screenshots.mjs` automatically.

### Two-run recipe (the gate for media-width.json)

```bash
# run 1 — default 390 px: the normal 3-platform gate + committed baselines
./test-all.sh fixtures/fidelity/dynamic/media-width.json

# run 2 — 250 px: web-only until the native lanes wire CAPTURE_WIDTH
SKIP_ANDROID=1 SKIP_IOS=1 CAPTURE_WIDTH=250 \
  ./test-all.sh fixtures/fidelity/dynamic/media-width.json
```

Gating rules:

- The 390 px run gates cross-platform exactly like any other fixture
  (SSIM ≥ 0.95 on every platform pair; baselines at 390 only —
  `UPDATE_BASELINE=1` must never be combined with `CAPTURE_WIDTH`).
- The 250 px run becomes a cross-platform gate once ≥2 platforms
  implement the width override. Until then the 250 px **web** captures
  are the reference PNGs the platform lanes compare their first native
  250 px captures against.
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
