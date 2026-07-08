# Fixtures

Test inputs for the converter and the visual pipeline: JSON envelopes
mapping component names to CSS properties (format: repo-root `README.md`
"Input format").

- `properties/<category>/<property>.json` — the canonical per-property
  suites, one directory per IR category (33 categories), one component
  per value variant.
- Suite trees — `perfect/`, `combos/`, `components/`, `keyframes/`,
  `fuzz/`, `perf/`, `viewport/`, `primitives/`, `wpt/`, … — exercise
  multi-property and stress scenarios (see `docs/reports/TIERS.md` for
  what each tier tests).
- Loose `*.json` files at this level are historical batch fixtures kept
  for regression coverage.

Convert one:

```bash
# from the repo root (JDK 21)
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
# → out/tmpOutput.json (the IR)
```

Render + compare it on all three platforms: `./test-all.sh fixtures/visual-test.json`.
