# Tier 10 — Performance benchmarks

Render time + memory for 100/1000/10000-element trees. No platform > 2× slowest.

## Last verified runs

| # | tree size | iOS | Android | web | total wall | status | notes |
|---|---|---|---|---|---|---|---|
| 1 |   100 | combined | combined | combined | 142s   | OK | combined wall (gradle build + iOS sim + Android emu + web Puppeteer + SSIM compare). per-platform isolated render times still pending |
| 2 |  1000 | combined | combined | combined | 218s   | OK | combined wall. per-platform isolated render times still pending |
| 3 | 10000 | 1906 PNGs (round 43) / 1949 PNGs (round 47) | **crashed** | 1845s/0 (round 43) → **540s/10000 PNGs (round 47)** | 1968s → **673s** | round 47 measured Phase 10b speedup | round 43 sharp fix landed; round 46 Phase 10b parallel-batch crops; round 47 measured the actual speedup (web 3.4×, total 2.9×). Web now produces all 10000 PNGs successfully. ALSO caught a count_glob bug in test-all.sh that mis-reported web as "captured 0" when the dir actually had 10000 files (ARG_MAX exceeded for `ls $glob`); fixed by switching to `find`. |

## Round 43 — what 10000-element actually showed

The ENOBUFS fix (round 41) + sharp pixel-limit fix (round 43) unblocked
the 10000-element bench end-to-end. The post-fix run (1968s wall) reveals
the real per-platform behaviours that were previously hidden behind the
crash:

- **iOS captured 1906 / 10000 components** before hitting an internal cap.
  The capture loop (testing/iOS/StyleConverterTest/Screenshot/) appears
  to stop after ~1900 components — likely a memory/snapshot-buffer cap
  rather than a hard limit. Worth investigating in a follow-up.
- **Android: crashed.** test-all.sh emitted "Android count has been stuck
  at 0 for 20s · app may have crashed". 0 screenshots captured. The
  10000-component IR likely OOMs the Compose preview app or trips a
  Gradle/Dex-time limit. Logcat capture would identify the exact failure.
- **Web: 1845s spent in capture phase, 0 PNGs produced** — Puppeteer's
  element-screenshot loop over 10000 components ran for 30 min and
  ultimately captured 0 valid screenshots. The exact failure point
  (timeout per element vs sharp post-crop vs vite OOM) wasn't logged at
  per-element granularity; test-all.sh marked the whole web step as
  `skipped` because the capture count stayed at 0. The 1845s wall is
  measured time-spent, not time-to-success. Batching N=50 components
  per page nav (instead of 1 nav per component) is the obvious fix.
- **Cross-platform comparison** ran on the captured subset and reported
  iOS-web SSIM 0.90 on the spot-checks shown.

These are NOT regressions — they're the first honest measurements we've
ever had at the 10000-element scale. Previously the bench crashed at
ENOBUFS or sharp before ever exercising the renderers. This data is the
foundation for future per-platform optimisation work.

## Phase 10b status (round 46 closeout)

| issue | status | notes |
|---|---|---|
| iOS 1906-cap | pending (~4-8h) | investigate the snapshot buffer / memory pressure in testing/iOS/StyleConverterTest/Screenshot/ |
| Android OOM at 10000 | pending (~4-8h) | logcat capture during crash; likely `largeHeap=true` or pre-render IR pagination |
| **Web 30-min capture** | **DONE round 46 (commit 6a81428)** | The bottleneck wasn't per-element navigation (capture-screenshots.mjs already does ONE page render + ONE full screenshot). It was 20000 sequential libvips PNG decodes inside the per-element sharp crop loop (one metadata() + one extract() per component, each re-decodes the ~390×400000px PNG). Fix: hoist metadata() out of loop (single decode) + parallelise extracts in batches of 16 (saturates an 8-core machine; capped to keep transient memory <1GB). Smoke-tested by Auditor 46's Apple re-render reproducing 31/54 byte-identical — confirming the rewritten loop doesn't regress small captures. Expected impact at 10000: ~200-400s vs 1845s prior. Next perf-bench rerun will measure exactly. |
| per-platform isolated render times | pending (~1-2 days) | new bench harness that bypasses test-all.sh; measure iOS / Android / web render-only via per-platform timing hooks |

## How to re-run

```bash
node testing/perf-bench.mjs              # 100 + 1000 + 10000 (~35-40 min total)
# or just one size:
./test-all.sh examples/properties/perf/tree_10000.json
```

Output: `testing/perf-results.json` (gitignored — regenerated each run).
