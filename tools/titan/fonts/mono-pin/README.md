# mono-pin — the default monospace metric pin faces

DejaVu Sans Mono (Regular + Bold), the free faces with **byte-identical
metrics** to the Menlo the frozen browser refs were rasterised with
(unitsPerEm 2048, '0' advance 1233, hhea 1901/-483 — measured in
`tools/titan/mono-pin.mjs`'s banner). The feeders deliver them as a document
`@font-face` named `monospace` to every native fixture that names the
monospace generic, so `ch` widths, wrap points and baselines land where the
refs have them; only outline ink differs.

Committed (not host-staged) since wave 47: the two-section A/B on both
natives (css-text + css-overflow, lane Z4) measured the pin at **+12 passes /
0 regressions** with the wave-47 measurement seams closed, which flipped
`TITAN_MONO_PIN` default-ON — a default cannot depend on an uncommitted
host directory. `TITAN_MONO_PIN=0` restores the platform cascade;
`TITAN_MONO_PIN_FONTS` still overrides the staged directory (both in
`mono-pin.mjs`).

License: Bitstream Vera (redistribution permitted with the notice) + DejaVu
changes in the public domain — the `LICENSE` file here is the text embedded
in the faces themselves (name table id 13), extracted verbatim.
