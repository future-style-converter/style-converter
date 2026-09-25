# Wave 52 — gate record

## Opening gate `wave52-open` (2026-09-25, 13:55 → 15:16 UTC, quiet host, `tools/titan/gate-driver.sh wave52-open`)

Tree: `campaign/applier-campaign` == `neworigin/dev` 5d9ed628 (wave 51 PR 3 merged), unmodified.

- 30/30 sections OK, every column 48/48/48 on the first attempt (`summary-open.txt`).
- Fixture net `BASELINE=1 ./test-all.sh --gate-set`: **exit 0 on all 8 fixtures** (`fixture-net-open.txt`) — the 390 baselines refreshed by PR 3 and the 10-line ledger hold on device.
- `node tools/titan/score-gate.mjs wave51-fix wave52-open --watch tools/titan/results/wave50-gate/watchlist.txt`: **0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured** over 4117 scored cells (`score-open.json`, `score-open.txt`). Totals unchanged: web 1215/1379 · iOS 1089/1369 · Android 1080/1369 = corpus-v6.17.

What this proves: PR 2 (9(g)) and PR 3 (label chrome) moved no corpus cell — as predicted (their carriers are off-corpus; TITAN captures suppress the chrome) — and the pipeline is deterministic run-to-run for the third consecutive gate (wave51-open, wave51-fix, wave52-open). Every wave-52 lane's prediction attributes against this run.

## Final gate
(filled at ship time)
