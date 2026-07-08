# Tier 8 — SSIM regression history

Per-fixture min SSIM tracked in JSON over time. CI alerts on regression > 0.05.

Run `node testing/ssim-history.mjs` to update `testing/ssim-history.json`.

| # | task | status | notes |
|---|---|---|---|
| 1 | Build ssim-history.mjs aggregator | **complete** | reads /tmp/tier*_results.txt + git commit, appends snapshot |
| 2 | Wire into CI (.github/workflows) | **complete** | .github/workflows/ssim-regression.yml runs on PR, ./test-all.sh + node testing/ssim-history.mjs; non-zero exit on any > 0.05 drop |
| 3 | Generate baseline history.json (initial run) | **complete** | 2 snapshots in testing/ssim-history.json: 1119 fixtures (2026-05-08, original) + 1154 fixtures (2026-05-10, round 68 — added Tier 9 real-page IRs + Tier 5 fixture-canvas captures since the original). `node testing/ssim-history.mjs` reports ✓ no regressions across the diff. |
