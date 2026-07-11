# IR — 07: Animations (keyframes wire + runtime motion semantics)

**Status: normative.** Two contracts live here: the **wire shape** of the
document-level `keyframes` envelope key (an ADDITIVE IR v2 minor
revision per the 05-versioning.md change process — machine contract in
`schema/ir-v2.schema.json`, golden pin in
`schema/conformance/fixtures/v2/keyframes.json`), and the **runtime
behavior** contract for playing them — which properties interpolate,
with what timing semantics, and how the capture harnesses freeze motion
deterministically. The runtime capability level defined here is
**animation runtime v1**, independent of the wire version (the wire
stays IR v2). Everything a runtime does not implement from this spec
follows the no-silent-fallthrough rule: the animation stays inert and
the miss is logged once via the platform's PropertyTracker.

## 1. Wire shape

### 1.1 Authoring input (CSS-side envelope)

The JSON authoring envelope gains a document-level `keyframes` block —
the authoring twin of CSS `@keyframes` at-rules, which are
document-scoped in CSS as well:

```json
{
  "keyframes": {
    "fade": [
      { "offset": "from", "declarations": { "opacity": "0" } },
      { "offset": "50%",  "declarations": { "opacity": "0.25" } },
      { "offset": "to",   "declarations": { "opacity": "1" } }
    ]
  },
  "components": { "…": { "properties": { "animation-name": "fade", "animation-duration": "1s" } } }
}
```

- `offset` is one keyframe selector per stop, per [css-animations-1]
  §4.2: `from` (= 0%), `to` (= 100%), or a percentage. Comma-grouped
  selectors are authored as separate stops. A percentage outside
  0–100% or any other token makes the stop **invalid** — the converter
  drops it with a log (no clamping: clamping would invent a stop the
  author never wrote). A set with zero valid stops is dropped whole.
- `declarations` is a plain property→value map — the **same shape and
  the same parser pipeline** as a component's base `properties` map.
  Shorthands expand, colors normalize to sRGB floats, lengths to px
  (02-values.md). There is no separate "keyframe value" grammar.

### 1.2 Wire output (IR v2 envelope key)

```json
{
  "irVersion": 2,
  "minReaderVersion": 2,
  "components": [ … ],
  "keyframes": {
    "fade": [
      { "offset": 0.0, "properties": [ { "type": "Opacity", "data": { … } } ] },
      { "offset": 0.5, "properties": [ … ] },
      { "offset": 1.0, "properties": [ … ] }
    ]
  }
}
```

- **Additive, omit-when-empty** (05-versioning.md minor-revision rule):
  a document without keyframes is byte-identical to pre-motion output.
- `offset` is the **resolved fraction in [0, 1]** — readers never
  re-parse `from`/`to`/percent strings.
- Each set is **sorted ascending by offset**, stable for equal offsets
  (equal-offset stops keep authoring order; the runtime applies the
  [css-animations-1] §4.2 last-wins cascade per property). Readers MAY
  rely on sortedness and MUST NOT reorder.
- Stop `properties` are standard `{type, data}` property envelopes —
  byte-identical semantics to component properties, including the
  unknown-`type` tolerance rule (05 §tolerance: skip + log).

### 1.3 Referencing and dangling names

A component opts into an animation with an ordinary `AnimationName`
property whose identifier matches a `keyframes` key (names are
case-sensitive custom-idents; the parser lowercases identifiers on both
sides, so matching is effectively on the lowercased form today).

A **dangling reference** — `animation-name` naming a set the document
does not define — is a **defined no-op**: the component renders in its
base styles, nothing animates, and the runtime logs the miss once per
name per document via PropertyTracker. It is never an error and never
drops the component. (CSS behaves identically: an unknown
`animation-name` simply doesn't animate.) The golden
`v2/keyframes.json` pins that the wire carries dangling references
verbatim.

## 2. Animatable tier v1

Runtimes implement interpolation for exactly this property set at
animation runtime v1:

| property (IR type) | interpolation space |
|---|---|
| `Opacity` | number, linear ([css-values-4] §interpolation; result clamped to [0,1] at paint) |
| `Transform` (function list), `Translate` / `Scale` / `Rotate` (individual) | per [css-transforms-1] §interpolation: matching function lists interpolate per-function (lengths in px, angles in degrees); non-matching lists fall back to the discrete step rule below |
| `BackgroundColor`, `Color` | in **sRGB component space** on the 0–1 floats the wire already carries (02-values.md) — all three platforms interpolate the same bytes, so cross-platform captures stay comparable |
| `Width`, `Height` | lengths in **px space** (the wire's normalized unit); a stop whose length is runtime-dependent (`null` px — %, em, calc) interpolates only if the runtime can resolve it first, else the step rule applies |

**Numbers linear, lengths in px, colors in sRGB** — one rule set,
three platforms, zero per-platform color-space drift.

**Non-tier properties in keyframes are carried, not dropped.** The
wire forwards every typed declaration; a platform that cannot
interpolate a property MAY **step-apply** it: the value switches
discretely at each stop boundary (at the stop's offset, mirroring the
50%-progress-point discrete rule of [web-animations-1] §4.4.3 is NOT
required — boundary switching is the honest v1 contract). Step-applied
properties MUST still be logged once via PropertyTracker so fidelity
reports can distinguish "interpolated" from "stepped". This is the
documented honesty valve, not a license to skip the tier.

## 3. Timing model

Animation runtime v1 honors the timing subset the runtimes already
extract (`runtimes/*/…/animations/` triplets):

- `animation-duration` — per [css-animations-1] §5.5; 0s means the
  animation completes instantly (fill-mode still applies).
- `animation-delay` — negative delays advance the start point per spec.
- `animation-iteration-count` — including `infinite` and fractional counts.
- `animation-direction` — `normal` / `reverse` / `alternate` /
  `alternate-reverse` ([css-animations-1] §5.6).
- `animation-fill-mode` — `none` / `forwards` / `backwards` / `both`:
  which stop's values apply outside the active interval.
- `animation-timing-function` — the easing keywords +
  `cubic-bezier()` / `steps()` forms the parser recognizes; applied
  **per iteration**, and (per [css-animations-1] §4.4) a
  timing-function declared *inside a keyframe stop* applies from that
  stop — runtimes MAY implement only the property-level function at v1
  (in-stop functions then step to the property-level one, logged).
- `animation-play-state` — `running` / `paused`
  ([css-animations-1] §5.8): `paused` freezes the animation at its
  current progress; flipping back to `running` resumes from that
  progress (never restarts). Play-state is the property-level cousin of
  the capture hook in §5 — the two MUST compose (a captured-at-time-t
  animation that is also `paused` at t stays wherever the capture put
  it).

Multi-animation lists (`animation-name: a, b`) pair with the timing
lists index-by-index per [css-animations-1] §5.2 list-matching (excess
timing entries are truncated, short lists repeat).

## 4. Transitions (state-change motion)

Transitions reuse this spec's interpolation tier and timing semantics,
triggered not by keyframes but by **selector/media bucket flips**
(06-dynamic-styling.md §5): when re-resolution changes the effective
value of a property named in `transition-property` (or `all`), the
runtime interpolates from the old effective value to the new one over
`transition-duration` + `transition-delay` +
`transition-timing-function`. Non-tier properties change discretely at
the transition's midpoint or endpoint (platform's choice, logged —
same honesty valve as §2). The forced-state hook (06 §6) is the
deterministic trigger: forcing a state mid-capture starts the
transition exactly like real input would, which is what
`fixtures/fidelity/motion/transitions.json` exercises together with
the §5 time hook.

## 5. Deterministic capture — `CAPTURE_ANIMATION_TIME`

Live motion is uncapturable: two screenshots of a running animation
never match. The harness contract (full recipes in
`docs/DYNAMIC_CAPTURE.md` §4) is:

**`CAPTURE_ANIMATION_TIME=<seconds>`** forces **every animation on the
captured surface to its state at absolute timeline time t, paused.**

- *Absolute*: t is measured from each animation's timeline zero —
  delays, direction, iteration and fill arithmetic all apply exactly
  as if wall-clock time t had elapsed. `t=0` is the animation's
  initial frame (respecting `animation-fill-mode: backwards/both` and
  negative delays).
- *Every animation*: keyframe animations AND running transitions —
  one clock for the whole surface, so multi-component fixtures freeze
  coherently.
- *Paused*: the state is frozen — two captures at the same t are
  byte-comparable, and capture latency cannot smear the frame.
- Unset ⇒ no seizing at all: the historical capture path stays
  byte-identical (committed baselines carry no animations).

**Web is the reference implementation**, via the Web Animations API
([web-animations-1]): the capture page seizes
`document.getAnimations()` and sets
`animation.currentTime = t × 1000; animation.pause()` — CSS
animations, CSS transitions and WAAPI animations all expose the same
`Animation` interface, so one loop freezes everything. Native
transports (platform lanes implement against this contract):
Android — intent extra `animationTime=<seconds>`, applied by seeking
the Compose `Transition`/`Animatable` clocks (test frame clock) to t;
iOS — launch argument `-animationTime <seconds>` (or
`SIMCTL_CHILD_CAPTURE_ANIMATION_TIME` env), applied by evaluating the
animation state at t instead of driving `withAnimation`. Like
`forceState`, the surface must carry a verifiable marker
(`data-animation-time` on web canvases) so a seized run can never
silently degrade to a live capture.

## 6. Conformance

- **Wire**: `schema/conformance/fixtures/v2/keyframes.json` — verbatim
  converter output pinning offset sorting, typed stop declarations,
  the multi-stop / multi-property shapes, and a dangling
  `animation-name` riding the wire untouched.
- **Visual**: `fixtures/fidelity/motion/` (generated —
  `tools/visual/gen-fidelity.mjs`): `keyframes-basic.json` (fade,
  slide, pulse, color shift, multi-property, 3-stop, alternate
  direction, fill-mode both — geometries chosen so t=0 / t=mid / t=end
  captures differ maximally) and `transitions.json` (transition-*
  against wave-7 selector buckets — forced state + mid-transition
  time capture).

[css-animations-1]: https://www.w3.org/TR/css-animations-1/
[css-transforms-1]: https://www.w3.org/TR/css-transforms-1/
[css-values-4]: https://www.w3.org/TR/css-values-4/
[web-animations-1]: https://www.w3.org/TR/web-animations-1/
