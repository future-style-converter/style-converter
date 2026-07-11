# IR — 06: Dynamic styling (runtime semantics of selector & media buckets)

**Status: normative — for runtime BEHAVIOR.** The wire shape of the
`selectors` / `media` buckets is owned by 01-envelope.md and is
**unchanged** by this spec (the buckets have carried these bytes since
v1; `schema/conformance/fixtures/v2/selectors-media.json` pins the
shape, `v2/dynamic-styling.json` pins multi-bucket ordering). This spec
defines what a conforming runtime DOES with the buckets: which
conditions activate, how active buckets layer over base properties, how
media queries are evaluated, and what happens when state changes.

The capability level defined here is **dynamic-styling runtime v1** —
a *runtime* contract level, independent of the IR wire version (the
wire stays IR v2). Everything a runtime does not implement from this
spec follows the no-silent-fallthrough rule: the bucket stays inert,
and the miss is logged once via the platform's PropertyTracker.

## 1. Vocabulary

- **Selector bucket** — `{condition, properties}`: a conditional style
  layer keyed to an *interaction state* of the component
  ([css-selectors-4] user-action §7 and input §12 pseudo-classes).
- **Media bucket** — `{query, properties}`: a conditional style layer
  keyed to the *rendering environment* ([mediaqueries-5]).
- **Active bucket** — a bucket whose condition/query currently holds.
- **Style resolution** — the step that folds active buckets over the
  base `properties` list to produce the single effective property list
  the extractors/appliers consume. Resolution happens *before*
  extraction: extractors and appliers never see buckets, only the
  resolved list. (Registry files: `PropertyRegistry.{kt,swift,ts}`.)

## 2. Selector conditions at runtime v1

| condition  | activates when | platform notes |
|---|---|---|
| `hover`    | a pointing device designates the component without activating it ([css-selectors-4] §7.1) | **Pointer surfaces only** (web with a mouse/trackpad, iPadOS pointer, Android pointer/stylus hover). On touch-only surfaces `hover` is a **defined no-op**: the bucket never activates and MUST NOT be emulated by latching on first tap — this mirrors `(hover: none)` UA behavior. |
| `active`   | the component is being activated: pointer button held down, or a press gesture is in progress on touch platforms ([css-selectors-4] §7.2). Deactivates on release/cancel. | Compose: `InteractionSource` pressed; SwiftUI: press gesture state; web: real `:active` or forced state. |
| `focus`    | the component holds input focus per the platform focus system ([css-selectors-4] §7.3) | Runtime v1 implements `focus` only; `focus-visible` / `focus-within` are *reserved* (inert + logged). |
| `disabled` | the component is in the platform's disabled/non-interactive state ([css-selectors-4] §12.1.2 `:disabled`) | The host app (or the harness forced-state hook, §6) supplies the flag; the runtime only styles it. |
| `checked`  | a checkable component (toggle, checkbox, radio analogue) is in the checked state ([css-selectors-4] §12.2.1 `:checked`) | Supported where the platform exposes a checkable state cheaply; where no analogue exists the bucket is a defined no-op + PropertyTracker log. |

**Any other condition** (structural pseudo-classes, `focus-within`,
`focus-visible`, `visited`, `nth-child(…)`, …) is *preserved on the
wire* (readers MUST still decode the bucket — tolerance rule, spec 05)
but is **inactive** at runtime v1. Inactivity is logged once per
condition per document via PropertyTracker; it is never a crash and
never drops the component.

## 3. Application model — how active buckets layer

Effective properties are computed at style-resolution time as follows:

1. Start from the base `properties` list.
2. Overlay each **active media bucket**, in `media[]` array order.
3. Overlay each **active selector bucket**, in `selectors[]` array
   order.

Overlaying a bucket means: for each property envelope in the bucket,
**replace** the current entry with the same `type` in place (whole-value
replacement — never a partial/deep merge of `data`), or **append** it
if no entry with that `type` exists yet. Within each step, later
buckets override earlier ones for the same `type` — last writer wins,
mirroring source-order cascade for equal-specificity declarations
([css-cascade-5] §6.4).

Rationale for state-over-media: a pressed/hovered state is the more
specific, user-facing signal; authors expect `:hover` styles to win
over a width-bucket recolor. The order is fixed by this spec — runtimes
MUST NOT reorder by any other heuristic.

Inactive buckets contribute nothing. A bucket becoming active/inactive
re-runs resolution (§5); resolution is pure — given (base, bucket
activation set) the effective list is deterministic.

## 4. Media queries at runtime v1

Supported query grammar: a single `(feature: value)` term, or a
conjunction of terms joined by `and`. Runtime-v1 features:

| feature | evaluation |
|---|---|
| `min-width` / `max-width` | Compared against the **render-surface width** in CSS px — the width of the surface the IR document is rendered into (web: the embedding element/viewport handed to the renderer; native: the hosting view's width in dp≡px). **Never** the device screen width. In the capture harnesses the render surface is the capture canvas (390 px by default; overridable — see `docs/DYNAMIC_CAPTURE.md`). Boundary is inclusive per [mediaqueries-5] §4.2: `(min-width: 390px)` matches a 390 px surface. Only `px` values are evaluated at v1. |
| `prefers-color-scheme` | `light` / `dark`, mapped to the **platform dark-mode signal** ([mediaqueries-5] §11.5): web `matchMedia('(prefers-color-scheme: dark)')`, Android configuration `uiMode` night mask (Compose `isSystemInDarkTheme()`), iOS/SwiftUI `colorScheme` environment. |

**Anything else** — `not` / `only` prefixes, comma-separated query
lists, range syntax (`(200px <= width)`), any other feature
(`orientation`, `hover`, resolution, …) — is **not evaluated** at
runtime v1: the whole bucket is conservatively **inactive** (a query
the runtime cannot evaluate never applies), logged once via
PropertyTracker. Malformed queries follow the same rule.

Related but distinct: `light-dark()` **color values**
(css-color-5) ride *inside* property `data` (dynamic color,
`srgb: null` — spec 02) and are resolved per-property against the same
platform dark-mode signal as `prefers-color-scheme`. The two mechanisms
MUST agree: one surface, one scheme answer.

## 5. Re-evaluation contract

State changes (press began/ended, focus gained/lost, disabled/checked
toggled, forced-state changes) and environment changes (render-surface
resize crossing a width boundary, platform color-scheme flip) MUST:

- **Restyle without recomposition of the tree.** The component's
  identity is stable across re-resolution: same DOM node on web (style
  mutation, not remount), stable composable identity on Compose (state
  read → modifier recomputation, no subtree recreation), stable view
  identity on SwiftUI. Component-local state (text input contents,
  scroll offsets, animation clocks) survives a restyle.
- Apply within one frame of the triggering change (no debounce that
  visibly lags the interaction).
- Re-run only style resolution (§3); slot composition (03-children.md)
  is untouched — dynamic styling never adds, removes, or reparents
  components.

## 6. Forced states (the capture/testing hook)

Real input events are flaky capture instruments (and impossible for
`hover` on device farms). Every runtime MUST therefore accept an
explicit **forced-state set** at style resolution: a set of condition
names treated as active regardless of real input state. Forcing
`{"active"}` resolves exactly the styles a real press would produce —
byte-identical effective property lists.

The harness-level contract (URL parameter on web, launch argument /
intent extra on native, two-width media capture recipe) lives in
`docs/DYNAMIC_CAPTURE.md`; the web harness carries the reference
implementation (`?forceState=` on the capture screen).

## 7. Conformance

- Wire: `schema/conformance/fixtures/v2/selectors-media.json` (single
  buckets) and `schema/conformance/fixtures/v2/dynamic-styling.json`
  (multiple buckets per component — pins that **array order survives
  the wire verbatim**, which §3 layering depends on).
- Visual: `fixtures/fidelity/dynamic/` (generated —
  `tools/visual/gen-fidelity.mjs`): `states.json` (selector buckets,
  base↔state values chosen for maximal pixel contrast),
  `media-width.json` (buckets that match / don't match at the 390 px
  and 250 px capture widths), `dark-mode.json`
  (`prefers-color-scheme: dark` buckets + `light-dark()` values).

[css-selectors-4]: https://www.w3.org/TR/selectors-4/
[mediaqueries-5]: https://www.w3.org/TR/mediaqueries-5/
[css-cascade-5]: https://www.w3.org/TR/css-cascade-5/
