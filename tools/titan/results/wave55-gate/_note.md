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
