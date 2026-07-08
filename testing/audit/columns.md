# Phase 12 Audit — `columns`

Status: **READ-ONLY code audit**. No fixtures written, no test-all run, no PNGs
pulled. `testing/audit/columns/` snapshot/images dirs left untouched.

## Scope

CSS Multicol IR properties (8):
`ColumnCount`, `ColumnWidth`, `ColumnGap`, `ColumnRuleWidth`,
`ColumnRuleStyle`, `ColumnRuleColor`, `ColumnSpan`, `ColumnFill`
(plus the `column-rule` shorthand which expands into the three rule longhands
at parse time).

IR source of truth: `src/main/kotlin/app/irmodels/properties/columns/` (8 files,
mirrors list above).

## Fixtures

- `examples/properties/columns/longtail.json` — exists, 21 single-component
  variants covering: column-count auto/N, column-width auto/length,
  column-gap normal/length/percent, column-rule-style
  none/dotted/dashed/solid/double/groove, column-rule-width
  thin/medium/thick/2px, column-span none/all, column-fill
  auto/balance/balance-all.
- **Gap**: every fixture block is an *empty* 160×80 coloured box. Multi-column
  layout has no observable effect without child content. So the fixture is
  a parser/extractor smoke test, not a rendering test. No component combines
  `column-count` with text content, with `column-rule-*`, or with a
  `column-span: all` child.
- No `audit-phase12.json` exists; not created (read-only).

## Platform engines

### Web (`testing/web/src/style/engine/columns/`)
Complete triplet for 7 of 8 properties (`ColumnCount`, `ColumnWidth`,
`ColumnRuleStyle`, `ColumnRuleWidth`, `ColumnRuleColor`, `ColumnSpan`,
`ColumnFill`). `ColumnGap` is **delegated** to the spacing family (handled by
the generic `gap` property, intentional per iOS README note). Appliers emit
the direct React `CSSProperties` name — the browser does the work. Edge cases
(column-count: 3 + text, column-span: all child, etc.) would render natively.

### iOS (`testing/iOS/StyleConverterTest/StyleEngine/columns/`)
**Stub only.** A single `ColumnsConfig`/`ColumnsExtractor`/`ColumnsApplier`
triplet that records raw keyword strings into a dict and does nothing with
them (`ColumnsApplier.contribute` is `_ = cfg`). SwiftUI has no multi-column
primitive; this is an expected no-op. Mirror structure violates the
per-property contract (one triplet per property) — README still says
"empty" / "properties migrate in one-at-a-time" but a single combined
triplet landed instead. Flag as structural debt, not a rendering gap.

### Android (`testing/Android/.../style/columns/`)
Four-file module (`MultiColumnConfig`, `MultiColumnExtractor`,
`MultiColumnApplier`, `ColumnsRegistration`). Extractor handles all 8
properties. Applier has a real `MultiColumnLayout` composable with
`BoxWithConstraints` + custom `Layout` distributing children greedy-balanced
into N columns, and a `drawBehind` rule painter supporting
solid/dashed/dotted (double/groove/ridge/inset/outset silently fallback to
solid — see `Notes.COLUMN_RULES`). **However**: the applier is never wired
into `PropertyApplier`/`ComponentRenderer` as a layout mode. The registration
only claims the property names on `PropertyRegistry`; nothing constructs a
`MultiColumnLayout` when `ColumnCount`/`ColumnWidth` is present on an IR
component. Net effect at runtime: Android renders all 8 properties as no-ops
on ordinary Compose components, same as iOS.

## Expected divergence

Web will render real columns; Android and iOS both render single-column with
no rule. Every fixture variant that actually exercised content would SSIM-
fail on every web↔mobile pair. Flag as **expected** — multi-column is not
on mobile's supported surface.

## Per-property contract compliance

| Property | IR | Parser | Web triplet | iOS triplet | Android triplet |
|---|---|---|---|---|---|
| ColumnCount | y | y | y | combined stub | combined (MultiColumn*) |
| ColumnWidth | y | y | y | combined stub | combined |
| ColumnGap | y | y | delegated to spacing | delegated (spacing/Gap) | combined |
| ColumnRuleWidth | y | y | y | combined stub | combined |
| ColumnRuleStyle | y | y | y | combined stub | combined |
| ColumnRuleColor | y | y | y | combined stub | combined |
| ColumnSpan | y | y | y | combined stub | combined |
| ColumnFill | y | y | y | combined stub | combined |

No platform yet meets the canonical `{Property}{Config,Extractor,Applier}`
per-file layout; all mobile code is combined-file. Web is closest (per-
property files) but still uses a shared `_dispatch.ts` not a registry call.

## Recommended follow-ups (out-of-scope here)

1. Split the Android `MultiColumn*` module into 8 per-property triplets under
   `style/columns/` to match iOS/Web contract. Keep the layout composable
   as an internal helper.
2. Wire `MultiColumnApplier.MultiColumnLayout` into `ComponentRenderer` so
   Android at least renders children in N columns when `ColumnCount` or
   `ColumnWidth` is set — today it's dead code.
3. Rewrite iOS stub as 8 per-property no-op triplets (not one combined) so
   the coverage matrix and `PropertyRegistry.allRegistered()` audit read true.
4. Replace `longtail.json` variants with content-bearing paragraphs so
   `test-all.sh` actually exercises column breaking on web; today variants
   are indistinguishable post-render.

## Done-definition status

Per `CLAUDE.md` five-point definition: **0/5 on mobile, ~2/5 on web**.
Fixture exists but doesn't exercise render; triplets exist but are
structurally non-canonical on mobile; no baseline committed for this
category under `testing/baseline/`; `testing/README.md` coverage matrix
not updated.
