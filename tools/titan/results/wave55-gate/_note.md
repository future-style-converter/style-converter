# Wave 55 — gate record

## Opening gate `wave55-open` (2026-10-11 10:37 UTC → ; tree 0d0d3a3d = the wave-54 squash; `tools/titan/gate-driver.sh wave55-open --skip-fixture-net`, then `rm -f /tmp/titan-device-pool/provisioned-*` + `--skip-corpus`)

Preflight (wave skill Phase 0): branch `campaign/applier-campaign` resynced to `neworigin/dev` = 0d0d3a3d (PR #153's squash,
merged 2026-10-10 14:52 UTC), tree clean, stash empty; provisioning intact (both `local.properties`, `tools/wpt/refs`, the
xcodeproj, `node_modules` a directory whose three workspace links point into this tree); disk 43 GB free; no emulator attached,
only the seed iPhone booted; tooling 2340 (2336 pass, 4 skipped); `doc-staleness-check.sh` exit 0; pilots default-ON; the 9 gate
fixtures present, 408 committed baselines, the ledger's 10 rows all with a real owner and a parseable `expires`;
`corpus-v6-20.json` names `wave54-final` in `artifact` and `reproduce`. Host at launch: load1 2.7, free+inactive 2820 MB,
**swap 9.4 / 10 GB used** (the wave-53 lesson: css-view-transitions may need `--resume` under swap); the driver's own read after
stopping our processes: load1 3.14, free+inactive 3487 MB. Provision rc=0 at 10:40:59 (emulator-5554 + the seed iPhone).

**Installed-build hashes** read at 10:41:27, right after `attempt 1 starting` (`build-hashes.txt`): **android MATCH**
(`62384c7e…`), **ios MATCH** (`38ea8fdc…` — the same digest as every wave-54 launch: no Swift source changed).

**The apk sha1 question settled (a correction to wave-54 lesson L19).** The apk on disk was NOT rebuilt by this provisioning —
`provision.103722.log` ran `:app:assembleDebug` but the file's mtime stays 2026-10-09 12:43:48 (the first wave-54 arm's build):
Gradle found the task up to date. The build embeds exactly one asset, `assets/tmpOutput.json` — the fixture IR that
`test-all.sh` copies into `apps/android-harness/app/src/main/assets/` (`test-all.sh:460`) — and that file was last written at
12:42:15 during the wave-54 fixture net (all-then-color, the gate set's last fixture, 6 components). So the wave-54 corpus
launch's `3e6173df…` and the arms' / today's `62384c7e…` differ by that ASSET (the net rewrote it between the two builds), not by
an irreproducible build: with unchanged sources and the same asset Gradle reuses the apk byte-for-byte, and a sha1 change does
signal an input change. For the WPT corpus the asset is irrelevant (titan feeds the per-test IR at run time), so across launches
the apk sha1 is comparable only when the last-net fixture is the same; built == installed MATCH remains the record's check.

**Corpus half: 30 / 30 sections OK on attempt 1** (10:41 → 11:52 UTC; css-cascade 43/43/43, every other section 48/48/48;
css-view-transitions on the first attempt despite the swap). **Read-out** (`open55-readout.sh` → `score-open.*`,
`control-open.*`, `watchlist-check.wave55-open.txt`, `sections-open.status.txt`): `score-gate.mjs wave54-final wave55-open` =
**0 gained / 0 lost / 0 movers (at ≥ 0.005 AND at any Δ) / 0 newly measured / 0 unmeasured-now over 4096 cells** — the totals are
corpus-v6.20's exactly (web 1243/1372 · iOS 1131/1362 · Android 1128/1362); the byte-identity control (`wave54-gate/control-check.mjs
wave54-final wave55-open`, no carriers): **web 1435 / 1435 identical · iOS 1435 / 1435 · Android 1435 / 1435 · wire 1435 / 1435**
(0 content-changed, 0 renumbered); watchlist unmatched 0. So the shipped tree — the wave-54 gate tree plus the post-gate regex
rewrite in `InertOutOfFlowWordJoin.ts` — reproduces the gate of record byte-for-byte on this host: the rewrite moved no pixel,
as its language-equivalence proof said it could not. The wave-55 opening obligation is DISCHARGED on the corpus; the fixture net
follows (its installed-build read is annotated as a net launch).

**Fixture-net half** (12:00 → 12:23 UTC; `--skip-corpus`; installed-build read at 12:04:38 annotated as a net-launch read — android
MATCH on the corpus launch's apk, ios "built .app digest=MISSING" = the race): **exit 0 on all 9 gate fixtures**
(`tools/titan/runs/wave55-open/gate-driver/fixture-net.log`): visual-test "no regressions vs baseline (327 platform-comparisons
ran)", filter-sepia-amounts 24 oracle checks · 2 waived · no regressions (24 comparisons), the five gate-only combinations 0 oracle
violations (12 / 12 / 12 / 9 / 15), composition-test gate-only, and all-then-color against its committed baselines "no regressions vs
baseline (18 platform-comparisons ran)", 12 oracle checks, 0 violations. **The wave-55 opening obligation is DISCHARGED** on the
corpus (0/0/0/0/0, 1435/1435 ×3 + wire) and on the net (exit 0 ×9). The net's only side effect on the tree is the regenerated
`tools/visual/baseline-stats.json` (same classification as committed; the timestamp-only churn was discarded).

## [W-L7a] — L7's Chromium + converter window (2026-10-12, before the landing, no gate on the host; records in `wl7a/`)

Run as the lane's note prescribes (`wave55-instruments/_note.md` "ORCHESTRATOR WINDOW REQUESTS"; `wl7a/window-run.txt`):
the two patches' sha256 equal the note's `patch:` lines (`wl7a/step1-patch-sha256.txt`); three throwaways at 611c9f93 with
U5 on `post` and U2 on `diff`; **step 3** `wl7a-marker-paint-probe.mjs` → `WL7A MARKER PAINT: every value none / normal`
and the wave-54 marker probe from the post tree → `PASS baked — 2 roots, 10 runs` / `MARKER PROBE: ALL PASS`; **step 4**
`wire-differential.sh` (U2) pre vs post over css-anchor-position, css-backgrounds, css-counter-styles, css-text,
css-writing-modes, selectors → 12 status rows `0 0 0 0 extracted=48 fail=0`, **identical 288 · content-changed 0 ·
renumbered 0 · one-sided 0 · renumbered-anomalous 0, verdict exit 0**; **step 5** the instrument fails when it must:
`--classify-only` against an expect file naming counter-suffix as CONTENT-CHANGED → exit 5, `MISSING CONTENT-CHANGED …
(expected, not observed)`. **Decision (PLAN §8 step 5): U5 lands at seq 9; no `--hold L7:U5`.** Throwaways removed.

## Landing (2026-10-12; `land-units.sh`, PLAN §4 / §8 step 6)

Prerequisites on the tree at b4f64963: the seven lanes COMPLETE (lane notes' last STATUS line; the pre-landing audit
`prelanding/` — 108 cell citations, 41 patch hashes, the manifest series applies with every revert chain, seams anchored on
611c9f93), the regeneration of the plan from the audit's items (55dd4cd9: expectations b773904f, 26 intended paths), the
landing tool's self-test green on the current bytes (144 / 0, b4f64963), [W-L7a] held (U5 lands at seq 9).
`land-units.sh --check --root <throwaway>` first: a clean checkout holds none of the UNTRACKED lane deliveries nor the
gitignored force-add files, so the first attempt reported 50 "MISSING" errors and touched nothing — the throwaway check is run
after rsyncing the seven lane dirs and the six wave-54 `out/` files into it (lesson for the recipe), then it printed
**0 errors, 0 warnings — landable**; `--check` on the shared tree: 0 / 0, `git status` byte-identical before and after.
**The landing: records commit fb94a502 (the seven lane dirs + the six force-added files) + 27 unit commits in manifest order
(L7 U1 optional, no patch, its files tracked), b4f64963..5bbc2a41, working tree clean.** Order as §4: L7 U2 → U3 → U4 →
U6-compose → U6-swift → U6-web → U6-tooling → U5; L6 U1 → U2 → U3; L4 U1-android → U1-ios → U2-android → U2-ios; L2 U1 → U2;
L3 U1 → U2; L5 U1-ios → U2o-ios → U2i-ios → U2o-android → U2i-android; L1 NB-ios; L5 U3-ios → D. Contingencies NB-ios-b and
U1-ios-LRM not landed (stage 1 decides). Next: the single-writer sweep on 5bbc2a41, S1, the worktree cleanup (§8 step 9½),
then stage 1 `wave55-pre` after `beforeEveryDeviceRun`.
