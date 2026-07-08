# Tier 11 — Accessibility audit (WCAG 2.1 AA)

Per-component: screen reader, focus order, contrast ≥ 4.5:1, keyboard nav.

## Phase 11a status (round 41 closeout)

**Color-contrast slice: WORKING.** `node testing/a11y-audit.mjs` runs end-to-end
against the Phase 5a `?fixture=<Name>` route, injects axe-core 4.8.3, runs the
`color-contrast` rule per fixture, and writes per-failure detail
(selector + observed ratio + expected threshold + fg/bg colors + font size)
to `testing/a11y-report.json`.

Last verified run: **15/15 fixtures scored**, 13 clean, **2 real WCAG AA
contrast failures** in shipped Tier-3 component fixtures:

| component     | selector | contrast | needs | fg / bg            | font          |
|---------------|----------|---------:|------:|--------------------|---------------|
| NavHeader     | span     | **2.28** |   4.5 | `#b8cdf0` / `#3b82f6` | 12pt regular |
| PrimaryButton | span     | **3.67** |   4.5 | `#ffffff` / `#3b82f6` | 12pt regular |

Both are real fixture-source defects (Material/Tailwind blue `#3b82f6` against
small white-ish text). PrimaryButton in particular is a recognised Material
Design pitfall: white-on-#3b82f6 only passes WCAG AA Large (≥18.66px or 14px
bold), not AA at 16px regular.

These are NOT renderer bugs — the converter and three platform engines all
faithfully render what the IR specifies. They're genuine accessibility issues
in the fixture authoring, surfaced by Phase 11a doing its job.

## Critical honesty: most WCAG criteria are blocked, not failing

The Style-Converter IR has **no semantic-HTML / a11y model** today
(no `IRRole`, `IRAriaLabel`, `IRAlt`, `IRTabIndex`). Every component renders
as a `<div>`, with text in a placeholder `<span>`. So most aXe rules are
inapplicable rather than failing — recording them as "passing" or "failing"
would be the same lazy-fabrication pattern that took 40 rounds to fix in Tier 1.

`testing/a11y-report.json.blocked` enumerates each blocked criterion with the
exact reason (no IRRole, no IRAriaLabel, no interactive elements, etc.). The
matrix below mirrors that.

## Per-criterion status

| # | criterion | iOS | Android | web | status | notes |
|---|---|---|---|---|---|---|
| 1 | screen-reader output | stub | stub | aXe-core | **blocked-IR** | IR has no IRRole/IRAriaLabel — every component renders as `<div>`; aXe cannot score what isn't marked up |
| 2 | focus order | stub | stub | aXe-core | **blocked-IR** | No interactive elements emitted (no `<button>`/`<a>`/`<input>` from IR); focus order is meaningless |
| 3 | contrast ratio (4.5:1) | stub | stub | **WORKING** | **Phase 11a DONE** | 15/15 fixtures scored; 2 real WCAG AA failures (NavHeader 2.28, PrimaryButton 3.67); see report.components for per-failure detail |
| 4 | keyboard nav | stub | stub | aXe-core | **blocked-IR** | No interactive elements; nothing to keyboard-navigate |
| 5 | aria-labels | stub | stub | aXe-core | **blocked-IR** | IR has no IRAriaLabel model |
| 6 | semantic landmarks | stub | stub | aXe-core | **blocked-IR** | IR has no IRRole; per-fixture single-component pages have no landmarks by design |
| 7 | skip-to-content | stub | stub | aXe-core | **blocked-IR** | IR has no IRRole=navigation; not applicable to per-fixture pages |
| 8 | form labeling | stub | stub | aXe-core | **blocked-IR** | IR has no IRLabel/IRAriaLabel; FormInput renders as a styled `<div>` with no `<input>` child |
| 9 | iOS XCUITest a11y harness | pending | - | - | **stub** (~8-12h) | wire iOS audit via XCUITest accessibility queries |
| 10 | Android Espresso AccessibilityChecks harness | - | pending | - | **stub** (~6-10h) | wire Android audit via Espresso a11y rules |

## What Phase 11b would unblock (NOT done — pre-req IR work)

To unblock criteria 1, 2, 4, 5, 6, 7, 8, the IR needs a new `accessibility/`
category sibling to the existing 33:

- `IRRole` (button | link | heading | img | nav | main | region | …)
- `IRAriaLabel` / `IRAriaLabelledBy`
- `IRAlt` (for image components)
- `IRTabIndex`

Plus per-platform mapping in each renderer:

- **Compose**: `Modifier.semantics { role = Role.Button; contentDescription = "…" }`
- **SwiftUI**: `.accessibilityLabel("…").accessibilityAddTraits(.isButton)`
- **Web**: emit `<button>`/`<h1>`/`<img>` instead of `<div>`/`<span>`

Estimated 2–3 days for the IR + parser + 3 renderers + fixture coverage.
Tracked as a "Tier 13" candidate in CAMPAIGN_SUMMARY.md (alongside Tier 5
Phase 5d pseudo-state work) since it's a new IR pillar, not a testing fix.

## How to re-run

```bash
cd testing/web && npm run dev    # vite on :3000 (background)
node testing/a11y-audit.mjs       # 15-30s; writes testing/a11y-report.json
```

`testing/a11y-report.json` is git-ignored (regenerated each run). Per-failure
detail is preserved in the JSON; the contrast-failure summary above is
copy-pasted from the last verified run.
