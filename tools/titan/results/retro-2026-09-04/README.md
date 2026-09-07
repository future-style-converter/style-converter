# tools/titan/results/retro-2026-09-04 — the 2026-09-04 retrospective's committed evidence

Why this directory exists: docs/BACKLOG.md's standing constraint (added in the
same retrospective) says every evidence pointer in the backlog is a tracked
path or a gate cell, and every lane artifact a later wave must act on is
COMMITTED under tools/titan/results/<wave>-<lane>/. The wave-49 refill
pointed at a session scratchpad and both artifacts (the 78-cell hand
verdicts and the extract-fixture seam patch) were lost with the session
(findings A10#0 / A2#1). These files are the retrospective audit lanes'
artifacts that BACKLOG queue items cite; nothing here is consumed by code.

All measurements are against tools/titan/runs/wave49-final (gitignored;
corpus-v6.15 — whose `artifact` field wrongly says wave48-final, obligation
#1) and the frozen refs
tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/.

| file | lane | what it is | cited by |
|---|---|---|---|
| `wave49-lookups.txt` | A10 | per-cell verdicts (`web/ios/android P|f|EXCL <ssim> [cF pF cvF]`, capture/ref frames) for every cell a BACKLOG item quotes; produced from the wave49-final manifests with the standard scorer idiom (`typeof x.ssim==="number" && !x.scoreExcluded`, pass = `wptPass===true`) | Ranked queue 0–12 |
| `excl-detail.mjs` + `excl-detail-wave49-final.out` | A9 | the extraction-wall census: 52 wall-excluded tests, the 13 whose raw metrics pass on all three with no veto, and the css-anchor-position exclusion detail; re-run from the repo root with `node tools/titan/results/retro-2026-09-04/excl-detail.mjs` against any run dir (edit `root`) | obligation #4, Instrument decisions "Extraction-wall re-measure", queue 9(l) |
| `fidelity-classified.csv` | A11 | one row per failing fidelity/combination component across the 88 `./test-all.sh` runs of the fidelity net (columns: fixture, component, odd platform, class — IOS-ODD / ANDROID-ODD / WEB-ODD / WEB-LABEL-SPILL / SIZE-MISMATCH …, per-pair ssim+pixelPct, canvas sizes, label ink, ink bbox, parent, text, props) | Decided (A) label chrome, (B) fit-content, queue 9(g–k) |
| `fidelity-per-fixture.csv` | A11 | the same runs summarised per fixture (exit code, elapsed, capture counts per platform, gate pairs / known / unexpected / stale, oracle checked / violations / waived / stale, failing components with class) | Decided (A) — "51 exit-4 fidelity fixtures" |
| `ledger-remeasure.json` | A12 | every line of tools/visual/cross-platform-expectations.json re-measured against the committed baselines under the current (0.02 + AA) pixelmatch: recorded vs measured ssim / pixelPct / dE95, status (STILL-FAILING / NO-BASELINE / …), expiry | obligation #7, queue 9(f) |
| `red-passing-cells-wave48-final.json` | A1 | the red-square degenerate-pass cells at wave48-final under the strict-red predicate, `[section, test, platform, wptPass, capRedPct, refRedPct]` — the interim fix-lane target set until `node tools/titan/red-square-census.mjs <run-id>` (retro R8b, the predicate of record) is re-run at the wave-50 gate | obligation #3 |
| `p2a-compose-dead-remaining.json` | P2a (fix-phase sweep) | the measured tail the Compose dead-code sweep did NOT delete: 187 zero-reference declarations (~1319 estimated body lines, `counts`) still living INSIDE otherwise-live Compose files under `runtimes/compose/src/main`, one `declarations[]` entry per declaration (`name`, `kind`, `file`, `line`, `bodyLines`, `private`), with `method` and `caveat` stating how "zero-reference" was derived — the work list for a later dead-code lane | Ranked queue registry / dead-code items (A6#8, A6#11) |

Regenerating: the `.out`/`.txt`/`.json` files are outputs of the audit
lanes' scripts over a run dir; only `excl-detail.mjs` is included as a
script because BACKLOG names its output as a work list. The others' scripts
lived in the audit session and are NOT needed to act on the items — the
numbers are the evidence, and the wave-50 gate re-derives them.
