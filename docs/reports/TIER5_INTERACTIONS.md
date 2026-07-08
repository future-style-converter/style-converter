# Tier 5 — Interaction state testing

Drive each state programmatically (Puppeteer/Espresso/XCUITest), capture,
compare.

## Phase 5a status: web slice WORKING (round 40 closeout)

The vite `?fixture=<Name>` route + FixtureCanvas + per-fixture IR
pre-conversion landed in round 40. Running:

```
cd testing/web && npm run build-fixtures      # one-shot, idempotent
cd testing/web && npm run dev                  # background, port 3000
node testing/interaction-states.mjs            # 90 captures
```

produces **90 real PNG snapshots** under `testing/interaction-snapshots/`
(15 components × 6 states). Last verified run: 90/90 ✓ 0 failed 0 skipped.

Run output is also written to `testing/interaction-report.json` with
per-(component × state) status and any error/note.

## What's actually being captured (honest)

The Style-Converter IR has **no pseudo-class support** today — there is no
`IRPseudoState` model, no parser support for `:hover { background: red }`,
and no per-platform `IRStateLayer` applier. So Phase 5a captures
**browser-default state changes only** (focus rings, click flash,
disabled-attribute graying on form elements). It does NOT yet validate that
"my CSS `:hover` rule renders the same on iOS / Android / web" — that's
Phase 5d (multi-week, see TIER5_PLAN.md).

What this gives you today:
- A working harness that reaches each component and fires each state event
- Per-state PNG baselines that can detect cross-version browser regressions
- The wiring for Phases 5b/5c (iOS XCUITest + Android Espresso) to plug into
- A failing-fast pre-flight check (vite-not-running detected up-front)

## Phase status

| phase | scope | status |
|---|---|---|
| 5a | web vite ?fixture route + Puppeteer capture | **DONE** (90/90 captures) |
| 5b | iOS XCUITest harness | stub (TIER5_PLAN.md §Phase 5b) |
| 5c | Android Espresso harness | stub (TIER5_PLAN.md §Phase 5c) |
| 5d | IR pseudo-state model + parser + 3 platform appliers | not started (Tier 13 candidate) |

## Per-component × state snapshot ledger

15 components × 6 states = 90 snapshots, all currently captured against
browser-default state styling. See `testing/interaction-report.json` for
the live machine-readable report.

| component       | hover | focus | active | checked | disabled | visited |
|-----------------|:-----:|:-----:|:------:|:-------:|:--------:|:-------:|
| Alert           |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Avatar          |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| IOSSettingsRow  |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| NavHeader       |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Pill            |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| PrimaryButton   |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| StatusBar       |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Tab             |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Toast           |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| MaterialCard    |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| FormInput       |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Tooltip         |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Code            |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| ProgressBar     |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |
| Badge           |   ✓   |   ✓   |   ✓    |    ✓    |    ✓     |   ✓¹    |

¹ visited has no programmatic API; the snapshot reflects the baseline state.
This is recorded as a `note` field on the result and is not a real
`:visited` capture. Real visited testing requires history manipulation,
which Puppeteer does not expose cleanly.

## Known limitations

- `:checked` and `:disabled` rely on the underlying DOM element exposing
  those properties. The current FixtureCanvas wraps every component in a
  `<div>`, so `node.checked = true` and `node.disabled = true` are no-ops
  for non-form components — the snapshot still captures, but it's the
  baseline. FormInput is the only component that actually exercises these.
- `:active` capture uses `mouse.down()` + screenshot + `mouse.up()` (real
  active state during the screenshot), correcting the previous `node.click()`
  impl that captured the post-release state.
- No cross-platform comparison yet — Phases 5b + 5c needed for that.
