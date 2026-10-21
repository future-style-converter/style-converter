# plan-skeptic round 3 — verify: done

- **Report:** `plan-skeptic-round-3.verify.md`.
- **Evidence:** `plan-skeptic-round-3.verify.d/`. To re-run, copy it to a scratch dir. Each script takes a scratch dir and
  writes only there.
- **Tree:** HEAD `640276a0`, unchanged.
  - Index sha256 and status outside the two plan dirs: identical before and after.
  - All 725 files under `wave55-plan/` and `wave55-gate/`: byte-identical before and after. `landing-manifest.json` was
    re-emitted once, byte-identically; only its mtime changed (report §4).
- **Git:** the only state commands were `git worktree add --detach <scratch>/wave55-r3v-wt HEAD` and its `git worktree
  remove`, plus the `wt-*` throwaways of `land-units.selftest.sh` E / F / G, which removes its own. `git worktree list`
  holds no scratch tree.
- **Not run:** no device, gradle, xcodebuild, vite, puppeteer, test-all or smoke. `wave55-open` was never read.

**Verdict: MIXED.** Re-executed, these hold byte-for-byte or as claimed:
- the generator and the snapshot;
- the watchlist;
- the 13 probes' self-test (230 keys);
- `probes-selfcheck.txt` (§T 130 / 0 / 48 of 48);
- every reader refusal record (A–E, K / J / F);
- toolkit A / B / D on the current bytes;
- `land-units.sh --check` read-only, and its self-test E / F / G (40 / 40);
- `ab-arms55.sh` record-after-restore (static read, plus its two git-free proofs reproduced);
- §9 C1–C15, each encoded;
- `plan-prose.py --check` and its self-check.

Two things fail:
- **M1:** the generated `beforeEveryDeviceRun` is not executable. Its trailing note is a bash syntax error, so nothing
  runs and it exits 2.
- **M2:** a new added-ink fake passes the three L3 dir-style gating keys, with gate exit 0. A row painted twice 3–7 px
  lower is accepted because `rows:` reads only the peak position. Under D31, a new fake joins the standing set.

There is also one should-fix: the committed self-test records cite older bytes. The plan already owes that re-run at
§8 step 6. Six nits are listed in the report.

STATUS: COMPLETE — every claim the task listed was re-executed except the git-init / committing self-tests, which are
outside this pass's one permitted git command (report §3); M1 and M2 are listed as must-fix for the orchestrator.
