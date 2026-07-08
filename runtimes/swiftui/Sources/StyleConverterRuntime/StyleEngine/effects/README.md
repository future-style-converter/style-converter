<!-- iOS style-engine scaffold README -->
<!-- Umbrella folder for visual-effect categories -->

# effects/ — iOS style-engine scaffold umbrella

Groups visual-effect buckets that map loosely to
`src/main/kotlin/app/irmodels/properties/effects/` plus effect-adjacent
properties that live in sibling IR folders:

- `clip/`   — `clip-path`, `clip`, `clip-rule`
- `mask/`   — `mask-*`, `mask-border-*`
- `shadow/` — `box-shadow` (from `borders/`), `text-shadow` (from `typography/`)
- `filter/` — `filter` (from `color/`), `backdrop-filter` (from `effects/`)
- `blend/`  — `mix-blend-mode`, `background-blend-mode`, `isolation`

## Status

Empty — per-property files migrate in from `../Renderer/StyleBuilder.swift`
as each phase of `testing/ROLLOUT.md` lands.
