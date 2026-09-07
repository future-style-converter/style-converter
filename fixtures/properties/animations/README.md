# Animations / Transitions / Timelines fixtures (Phase 9)

CSS fixtures exercising every value variant accepted by the animation,
transition, view-timeline, view-transition, timeline-scope, and
scroll-timeline longhand parsers under
`converter/src/main/kotlin/app/parsing/css/properties/longhands/animations/` and
`.../longhands/scrolling/Scroll{Timeline,TimelineName,TimelineAxis}PropertyParser.kt`.

Run each via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/properties/animations/<file>.json -o /tmp/p9"
```

Fixture count: `ls fixtures/properties/animations/*.json | wc -l` (10 at
authoring, Phase 9). Every fixture converted with `(0 generic)` on every
component when the phase shipped; that claim has not been re-run since, so
re-derive it from the command above rather than trusting this line.

## Coverage map

| Fixture | Components | Properties / variants |
|---|---:|---|
| animation-name-duration-delay.json | 14 | `animation-name` (none / ident / dashed-ident / multi), `animation-duration` (s, ms, fractional, 0, multi), `animation-delay` (positive, ms, negative, -s, multi with negatives) |
| animation-iteration-direction.json | 11 | `animation-iteration-count` (1, 3, 0.5, 2.5, infinite, multi), `animation-direction` (normal/reverse/alternate/alternate-reverse + multi) |
| animation-fill-play-composition.json | 12 | `animation-fill-mode` (all 4 + multi), `animation-play-state` (running/paused + multi), `animation-composition` (replace/add/accumulate + multi) |
| animation-timing-function.json | 19 | all 7 keywords, `cubic-bezier(...)` × 2 (incl negative control points), `steps(n)`, `steps(n, end/start/jump-start/jump-end/jump-none/jump-both)`, `linear(0, 0.25 25%, 1)`, `linear(0, 0.5, 1)`, multi |
| animation-timeline-range.json | 23 | `animation-timeline`: auto/none/ident/`scroll()`/`scroll(root)`/`scroll(inline)`/`scroll(root inline)`/`view()`/`view(inline)`/`view(inline 10px 20px)`; `animation-range` normal, pct pair, named pair; `animation-range-start` pct/length/normal/cover+%/entry+%; `animation-range-end` pct/length/normal/contain+%/exit+% |
| transition-property-duration.json | 11 | `transition-property` all/none/ident/multi/mixed-with-all; `transition-duration` s/ms/fractional/0/multi |
| transition-delay-timing-behavior.json | 21 | `transition-delay` (positive, ms, 0, negative, multi with negatives), `transition-timing-function` (same set as animation), `transition-behavior` (normal/allow-discrete/multi) |
| view-timeline.json | 17 | `view-timeline` (name / name+axis ×4), `view-timeline-name` (none/dashed-ident/plain), `view-timeline-axis` (block/inline/x/y), `view-timeline-inset` (auto, single length, single pct, length pair, auto+length, auto+auto) |
| view-transition.json | 16 | `view-transition-name` (none/auto/ident/dashed), `view-transition-class` (none/single/multi), `view-transition-group` (normal/nearest/contain/root/ident), `timeline-scope` (none/all/single/multi) |
| scroll-timeline.json | 12 | `scroll-timeline` (name / name+axis ×4), `scroll-timeline-name` (ident/plain/none), `scroll-timeline-axis` (block/inline/x/y) |

## Totals

- 10 fixture files
- 156 components total
- 0 `generic` rows across the entire suite

## Parser gaps / notes for platform-agent handoff

Everything parses cleanly, but several parsers have quirks that platform
appliers must be aware of:

- **`AnimationNamePropertyParser`** lowercases the entire value before
  splitting, so `AnimationName.Identifier("myAnim")` becomes `"myanim"`. Animation
  name identifiers in the IR are effectively case-insensitive after parse.
- **`AnimationDelayPropertyParser` / `TransitionDelayPropertyParser`** have
  no global-keyword / expression branch — passing `var(--x)` returns `null`
  instead of a Raw variant. Fixtures stay on numeric times only.
- **`AnimationIterationCountPropertyParser`** returns `null` for the whole
  list if any single comma-entry fails to parse (no defensive fallback).
  Fixtures keep every entry valid.
- **`AnimationTimelinePropertyParser`** ignores `nearest`/`root`/`self` /
  axis tokens beyond the first recognized one inside `scroll(...)`/`view(...)`;
  the *last* seen token wins. Also: any non-keyword ident (including
  non-dashed) becomes `Named(ident)` — there is no "plain-ident rejected"
  branch. Fixtures exercise both dashed and plain idents.
- **`AnimationRangePropertyParser`**: only 1, 2, or 4 whitespace-separated
  tokens are handled specifically. A 3-token input (e.g. `entry 25% 75%`)
  falls into the `Raw` branch; fixtures do not exercise that shape.
- **`AnimationRangeStartPropertyParser` / `...End...`**: return a
  `Keyword(lowercased)` catch-all for anything that is not a percent, length,
  or recognized `<name> <pct>` pair. So typos silently become a keyword. This
  is the one place where appliers cannot rely on keyword values being in the
  spec-defined set.
- **`TransitionPropertyPropertyParser`** lowercases + treats every non-
  `none`/`all` token as `PropertyName(...)` — custom-property idents (e.g.
  `--my-var`) survive, but `all` in a list is preserved as `All()`.
- **`TransitionTimingFunctionPropertyParser`** does *not* support the
  `linear(stops)` function (only animation-timing-function does). The fixture
  for transitions sticks to keywords + cubic-bezier + steps.
- **`TimelineScopePropertyParser`** uses the *original* (non-lowercased)
  string when splitting the ident list, so custom-property casing survives.
- **`ViewTimelinePropertyParser`** silently discards `none` (resets name to
  null) and picks the *last* axis token seen. Multi-comma lists are not
  supported — the whole value is treated as one timeline declaration.
- **`ViewTimelineInsetPropertyParser`** requires at least one valid value;
  a bare `%` with no number returns null. `auto auto` produces
  `Auto, Auto`.
- **`ViewTimelineNamePropertyParser`** does *not* produce an explicit
  "None" sentinel — it stores the literal string `"none"` as the name. A
  platform applier checking for sentinel state must compare the string.
- **`ViewTransitionGroupPropertyParser`** stores unknown tokens as
  `Raw(original)` (case-preserved), including `auto` and `match-element`
  which the task spec mentioned — the parser does **not** recognize those
  keywords. Fixture uses the four actually-recognized keywords plus a
  dashed-ident Raw.
- **`ScrollTimelinePropertyParser`** defaults the name to the literal
  `"none"` if no non-axis token is seen. Any non-axis token (including
  plain idents) is accepted as the name.
- **`ScrollTimelineNamePropertyParser`** stores the trimmed raw value
  verbatim — `none` is stored as the literal string `"none"` (no sentinel).

## Properties not directly fixtured (intentional)

- **`animation` shorthand / `transition` shorthand / `scroll-timeline`
  shorthand / `view-timeline` shorthand** — Phase 9 is longhand-only.
  Every longhand is covered.
- **`view-transition-group` with `auto` / `match-element`** — the current
  parser degrades those to `Raw(value)` (same bucket as `--my-group`),
  so they add no new coverage over the existing ident Raw entry.
- **Expression / `var()` / `calc()` inputs** — most parsers in this scope
  return null (dropped) for those, since they pre-date the expression
  branch added elsewhere. Not fixtured to keep zero-generic guarantee.
- **Negative time in `animation-duration`** — semantically invalid per
  spec; TimeParser would accept it, but fixtures keep durations ≥ 0 to
  match spec and avoid misleading platform tests.
