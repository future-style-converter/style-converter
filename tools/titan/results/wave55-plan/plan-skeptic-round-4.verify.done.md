# plan-skeptic round 4 — verify: done

- **Report:** `plan-skeptic-round-4.verify.md`.
- **Evidence:** `plan-skeptic-round-4.verify.d/`.
  - Each script takes a scratch directory and writes only there.
  - `fakes4.py --scratch <dir> --groups Lx --out <file>` re-runs the sweep; `zones4.py <dir> <out>` adjudicates it.
- **Tree:** HEAD `640276a0`, unchanged.
  - The index sha256, `git stash list` and status outside the two plan dirs are identical before and after.
  - The pre-existing files under `wave55-plan/` and `wave55-gate/` are byte-identical before and after.
- **Git:** only throwaway `git worktree add --detach <scratch>/… HEAD` + `remove --force` (toolkit and guard proofs, and
  land-units' own `wt-*`), plus git-init repos / one `git clone --shared` inside the scratchpad. `git worktree list`
  holds no scratch tree.
- **Not run:** no device, gradle, xcodebuild, vite, puppeteer, test-all or smoke. `wave55-open` was never read.

**Verdict: MIXED.** These hold, re-executed:
- `beforeEveryDeviceRun`: `bash -n` 0, and a verbatim run exits 0 from the root and from `/` and prints its kill summary
  (`stopped [] left []`). Its red paths exit 2.
- The three self-test records: they cite the current script bytes, and each reproduces.
- `plan-build.py --out`: byte-identical.
- Watchlist: unmatched 0.
- `geometry-gate.py --self-test`: HOLDS.
- `plan-prose.py --check`: HOLDS.
- The U2 arms: `drop-L4-U2` (`wave55-pre`) and `drop-L6-U2` (`wave55-final`) have distinct run ids and their own
  `--against`.
- The standing teeth set: 215 / 0 / 48 of 48.

The independent added-ink sweep (1160 fake runs) holds on L1, L2 and L3, but finds **4 must-fix findings**:

| | lane | keys | what passes |
|---|---|---|---|
| M1 | L4 face-present | 10 keys | added ink up to +335 px (FACE_INK_TOL 6 %, and the Compose no-wrap rows never read band 2's ink) |
| M2 | L6 `[X]` | 5 keys | a child fill copied 1 px off, +24 … +500 px (`extra` is dilated by 1 px) |
| M3 | L5 `[H]` ios | | no ink read |
| M4 | L5 `[T]` ios | | ink inside its digit window |

The 2-px floor is listed apart, to be named or justified.

STATUS: PARTIAL — the checks of items 1, 3, 4 and 5 hold. Item 2 found must-fix acceptances on 17 gating keys (L4 ×10,
L6 ×5, L5 `[H]` ios, L5 `[T]` ios), which the plan must fix or name before its "added-ink class closed" claim stands.
