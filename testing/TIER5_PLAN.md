# Tier 5 (Interaction states) — implementation scope

Captured by the round-39 closeout scoper agent. Tier 1 is converged;
Tier 5 is the most actionable next blocker. This doc preserves the scope
so implementation can start cold.

## What `testing/interaction-states.mjs` expects

- URL: `http://localhost:3000/?fixture=<ComponentName>` (e.g. `?fixture=Alert`)
- DOM contract: a single element with `[data-testid="<ComponentName>"]`
  that Puppeteer can `.hover()`, `.focus()`, `.click()`, `.boundingBox()`,
  and `.screenshot()` on.
- 15 component names, all matching existing fixture filenames in
  `examples/properties/components/`: Alert, Avatar, IOSSettingsRow,
  NavHeader, Pill, PrimaryButton, StatusBar, Tab, Toast, MaterialCard,
  FormInput, Tooltip, Code, ProgressBar, Badge.

## Mismatch: fixtures are CSS shorthand, not IR

Tier-3 fixtures (`examples/properties/components/Alert.json` etc.) are
**kebab-case CSS shorthand** keyed by component name:
```json
{ "components": { "Alert": { "properties": { "background-color": "#fef3c7", ... } } } }
```
The web renderer consumes **IR** (`{components: [{type: "BackgroundColor",
data: {srgb: ...}}]}`). So the route handler must either (a) shell out to
the JVM converter to produce IR per-fixture, or (b) the fixtures must be
pre-converted into per-component IR JSONs served from `public/`.

## Files that must change

1. **`testing/web/src/ui/App.tsx`** — add `?fixture=<name>` mode parser
   alongside `isCaptureMode()`. When set, fetch `/fixtures/<name>.json`
   (an IR doc) and render a new `<FixtureCanvas>` that:
   - finds the single component matching `<name>`
   - renders it inside a wrapper carrying `data-testid="<name>"` and
     `tabIndex={0}` (so `.focus()` works on a div) plus
     `data-fixture-ready="1"` after mount.
2. **New file `testing/web/src/ui/FixtureCanvas.tsx`** (~30 lines) —
   single-component chromeless render that adds the `data-testid` attribute.
   Mirrors `CaptureCanvas` shape.
3. **Route/asset wiring** — ship 15 pre-converted IR JSONs at
   `testing/web/public/fixtures/<Name>.json`. Either:
   - a one-shot npm script (`npm run build-fixtures`) that loops the 15
     fixtures through `./gradlew run --args="convert ..."`, or
   - extend `package.json`'s `copy-ir` to do the same.
4. **`testing/interaction-states.mjs`** — add (a) launch-the-vite-server-or-fail
   check, (b) `await page.waitForSelector('[data-fixture-ready]')` before the
   state action, (c) wrap puppeteer in a `try/finally` so the browser closes
   on failure (currently leaks).
5. **`testing/TIERS.md` + `testing/TIER5_INTERACTIONS.md`** — flip status.

## Puppeteer driver expects on the page

- `[data-testid="<ComponentName>"]` selector resolves to a single element
- That element has a non-zero `boundingBox()` (so it must be sized — wrap
  in a 390px canvas like `CaptureCanvas` does)
- Page is fully painted before the state event (sentinel
  `[data-fixture-ready]` recommended; `interaction-states.mjs` doesn't
  currently wait but should)

## Blockers / Risks

- **Visited state**: script comment already admits "no programmatic API;
  relies on browser history" — that variant will produce identical
  baseline+state shots regardless. Document as known-stub.
- **`:active`**: `node.click()` fires after :active is released. Real
  `:active` capture needs `page.mouse.down()` + screenshot + `mouse.up()`
  — current code is wrong even when wired. Note this in the implementation PR.
- **`:checked` / `:disabled`**: only fire on form elements, but the
  renderer wraps everything in `<div>`. Either skip these states or
  change the wrapper element type for FormInput.
- **iOS XCUITest + Android Espresso harnesses are stubs** — Tier 5 won't
  be cross-platform "complete" until those land. The vite work only
  unblocks the web slice.
- **Style engine has no pseudo-class support** — IR has no `:hover` data;
  we're capturing browser-default state changes (focus rings, click flash)
  only. There's nothing in the IR/parser/style-engine triplet today that
  maps `:hover { background-color: red }`. If the goal is to compare
  *styled* states cross-platform, this is a much bigger missing pillar
  (would need `IRPseudoState` IR type, parser support, and per-platform
  appliers).

## Complexity estimate

- **If "make `interaction-states.mjs` reach a rendered component and
  snapshot default state" is the goal**: **4-hour job** (route handler +
  FixtureCanvas + fixture-conversion script + wait-for-ready + browser
  cleanup).
- **If "actually test :hover/:focus/:active styling cross-platform" is
  the goal**: **full-week job, possibly multi-week** — needs IR
  pseudo-state model, parser, three platform engines, plus the iOS/Android
  driver harnesses that are still stubs.

## Recommended phasing

- **Phase 5a** (4 hours): web slice — vite route + FixtureCanvas + IR
  pre-conversion + fix puppeteer waits/cleanup. Result: `:focus` and
  `:active` (with mouse.down fix) capture against browser-default styling.
- **Phase 5b** (1 day): iOS XCUITest harness (separate target, drives
  Simulator via accessibility actions).
- **Phase 5c** (1 day): Android Espresso harness.
- **Phase 5d** (multi-week): IR pseudo-state model + parser + per-platform
  `IRStateLayer` appliers. Out of scope for Tier 5 v1; track as Tier 13.
