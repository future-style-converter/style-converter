<!-- iOS style-engine scaffold README -->
<!-- Umbrella folder for border-related categories -->

# borders/ — iOS style-engine scaffold umbrella

Mirrors `src/main/kotlin/app/irmodels/properties/borders/`, split into
logical sub-buckets on the iOS side:

- `sides/`   — per-side width / color / style (top/right/bottom/left + logical)
- `radius/`  — per-corner border-radius properties
- `image/`   — `border-image-*` properties
- `outline/` — `outline-*` (outline is grouped here for locality)

## Status

Empty — per-property files migrate in from `../Renderer/StyleBuilder.swift`
as each phase of `testing/ROLLOUT.md` lands.
