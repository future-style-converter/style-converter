# Archived loop scripts

Scripts in this folder were used during the round 1-39 audit-driven Tier 1
campaign but became dead code once Tier 1 converged at round 40 (FREEZE
notice in `testing/TIER1_VARIANT_DEPTH.md`). They're kept here for git
history continuity rather than deleted, in case future contributors want
to re-run them on a new fixture corpus.

## What's archived (round 64)

| script                      | original purpose | why archived |
|-----------------------------|------------------|--------------|
| `master_autopilot.sh`       | Sequential Tier-2-through-12 auto-runner during Tier 1 work | Replaced by per-tier explicit phases in rounds 40-50 |
| `overnight_worker.sh`       | Continuous Tier 1 + Tier 3 processor during overnight runs | Tier 1 frozen, no more bulk processing needed |
| `tier1_eternal.sh`          | CAP=25 keyword-expansion worker for Tier 1 fixtures | Tier 1 frozen at 91/550 honest |
| `tier1_verify.sh`           | Per-fixture verifier for Tier 1 tracker | Replaced by `BASELINE=1 ./test-all.sh` regression test (round 49 + smoke.sh) |
| `tier3_diagnose.sh`         | Tier 3 component fail diagnostic | Tier 3 not actively worked post-round-3 |
| `expand_v2.py`              | Careful incremental fixture expander | Tier 1 frozen |
| `synth_form_variants.py`    | Form-aware fixture synthesizer (NEEDS_BG, NEEDS_TEXT, etc.) | Tier 1 frozen |
| `synth_function_fixtures.py` | Hand-templated CSS-function-syntax fixture generator | Tier 1 frozen |
| `degen_audit_output.txt`    | Stale captured output from round-22 degenerate audit | Reference only; current state in DEGENERATE_FIXTURES.md |

## What's still live (in `testing/loops/`)

- `audit_rewrite_claims.sh` — wired into `testing/smoke.sh` (round 59) and
  CI (`audit.yml`) as a guardrail against tracker fabrication.
- `degenerate_audit.py` — referenced from `testing/DEGENERATE_FIXTURES.md`
  for re-running the systematic degenerate-fixture detection if the
  fixture corpus changes.

See `testing/CAMPAIGN_SUMMARY.md` for the full chronological story of
how the campaign migrated from these loop scripts to the unified
`smoke.sh` audit infrastructure.
