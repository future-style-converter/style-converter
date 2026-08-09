#!/usr/bin/env bash
#
# tools/titan/fetch-wpt.sh — Phase 0 WPT acquisition driver.
#
# Implements TITAN_ARCHITECTURE.md Section 3 (Sections 3.1 partial-clone,
# 3.2 pinning, 3.3 sparse-checkout). The corpus lands in tools/wpt/
# which is gitignored (Section 8 Phase 0); this script is the canonical
# way to (re)materialise it on a workstation or CI runner.
#
# Behaviour:
#   - Reads WPT_REF from env; if unset, falls back to the contents of
#     tools/titan/WPT_REF (per Section 3.2 quarterly-repin convention).
#     The first non-comment, non-blank line of WPT_REF is treated as the
#     SHA, so the file can carry an explanatory header.
#   - Partial-clone (--filter=blob:none) + sparse-checkout (cone mode)
#     to keep on-disk size ~1.5 GB instead of WPT's full ~10 GB tree
#     (Section 3.3).
#   - Idempotent: re-running against an existing tools/wpt/ checkout
#     just fetches + checks out the pinned SHA. Useful for cron-driven
#     refresh + for the Section 3.4 quarterly re-pin workflow.
#
# Exit codes:
#   0  — green; tools/wpt/ is at $WPT_REF with sparse paths populated.
#   1  — argv / env malformed (e.g. missing WPT_REF file).
#   2  — git operation failed (network, bad SHA, sparse-checkout error).
#
# Hard rules from the Phase 0 spec:
#   - Do NOT commit tools/wpt/ — the bucket index (tools/titan/wpt-buckets.json)
#     is the durable artifact (Section 10 Q5).
#   - Do NOT touch the existing 327-pair pipeline; this script is purely
#     additive and writes only inside tools/wpt/.

# Strict mode: fail fast on any error, unset var, or pipeline failure.
set -euo pipefail

# ---------------------------------------------------------------------------
# Resolve repo root + relevant paths.
# ---------------------------------------------------------------------------
# Resolve the directory this script lives in (tools/titan/) regardless of
# the caller's cwd. We deliberately don't use $PWD — TITAN scripts are
# routinely invoked from inside test-all.sh subshells that chdir around.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Repo root is two levels up: tools/titan -> testing -> repo root.
REPO_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"
# Target checkout dir; gitignored per .gitignore (Phase 0 PR adds the entry).
WPT_DIR="$REPO_ROOT/tools/wpt"
# Default WPT_REF source (one-line file with optional comment header).
WPT_REF_FILE="$SCRIPT_DIR/WPT_REF"

# ---------------------------------------------------------------------------
# Resolve $WPT_REF: env override wins; else read first non-comment line of
# the WPT_REF file (Section 3.2 — pin lives in-repo, override is per-run).
# ---------------------------------------------------------------------------
if [[ -n "${WPT_REF:-}" ]]; then
    # Explicit env override — trust it verbatim. Used by re-pin PRs and CI.
    PINNED_REF="$WPT_REF"
else
    # No env override: the WPT_REF file is the source of truth.
    if [[ ! -f "$WPT_REF_FILE" ]]; then
        # Without a pin we'd silently follow HEAD — non-reproducible per Q4.
        echo "fetch-wpt: missing $WPT_REF_FILE and no \$WPT_REF set" >&2
        exit 1
    fi
    # Strip comment + blank lines and take the first remaining line. We
    # tolerate either bare-SHA or "<sha>  # comment" formats so the file
    # can self-document how the pin was chosen.
    PINNED_REF="$( grep -Ev '^[[:space:]]*(#|$)' "$WPT_REF_FILE" \
                   | head -n1 \
                   | awk '{print $1}' )"
    if [[ -z "$PINNED_REF" ]]; then
        echo "fetch-wpt: $WPT_REF_FILE contains no SHA line" >&2
        exit 1
    fi
fi

# Echo the resolved pin once for auditability; future TITAN runs grep
# their own log for this line to confirm reproducibility.
echo "fetch-wpt: WPT_REF=$PINNED_REF"
echo "fetch-wpt: target=$WPT_DIR"

# ---------------------------------------------------------------------------
# Initial clone (only if tools/wpt/ doesn't already exist).
# ---------------------------------------------------------------------------
if [[ ! -d "$WPT_DIR/.git" ]]; then
    # Make the parent so `git clone` doesn't ENOENT on a fresh worktree.
    mkdir -p "$( dirname "$WPT_DIR" )"
    # Why these flags (per Section 3.1):
    #   --filter=blob:none — partial clone; defer blob fetch until walked.
    #     Halves disk usage; required because WPT carries large binary
    #     fixtures (fonts, images) we mostly don't touch.
    #   --no-checkout      — sparse-checkout config must land before the
    #     working tree is written, otherwise we materialise the full
    #     ~10 GB tree and only sparse-prune afterward (slow + wasteful).
    #   (no --depth here)  — `git fetch <sha>` later requires the remote
    #     to know the SHA; with --depth 1 we'd need uploadpack.allowReachableSHA1InWant
    #     which github supports, but a shallow clone still has to be deepened
    #     to checkout an arbitrary SHA. Trade-off: skip --depth and pay
    #     ~50 MB extra metadata for simplicity + reliability.
    echo "fetch-wpt: cloning web-platform-tests/wpt (partial, no checkout)..."
    git clone \
        --filter=blob:none \
        --no-checkout \
        https://github.com/web-platform-tests/wpt \
        "$WPT_DIR" \
        || { echo "fetch-wpt: clone failed" >&2; exit 2; }
else
    # Existing checkout — we just need to update refs + sparse config.
    echo "fetch-wpt: re-using existing checkout at $WPT_DIR"
fi

# ---------------------------------------------------------------------------
# Sparse-checkout config (Section 3.3): scope to /css/ + /resources/ +
# /fonts/. WPT's full /html/, /dom/, /svg/, etc. trees are GB of cruft we
# never touch.
# ---------------------------------------------------------------------------
# Cone mode is the recommended modern API; it's much faster than the
# legacy pattern-list mode and what `git sparse-checkout set` defaults to.
echo "fetch-wpt: configuring sparse-checkout (cone mode)..."
git -C "$WPT_DIR" sparse-checkout init --cone \
    || { echo "fetch-wpt: sparse-checkout init failed" >&2; exit 2; }
# These four directories cover every CSS reftest:
#   css/        — the entire reftest corpus (Section 2.2 maps every
#                 subdir to an IR category)
#   resources/  — shared CSS / JS files that tests <link> from
#                 (e.g. /resources/check-layout-th.js, /resources/CSS21/*)
#   fonts/      — @font-face fixtures referenced by css-fonts/ tests.
#                 Bucket-B per Section 4.1 — if we don't have the font
#                 file the extractor can still emit a lossy fixture.
#   images/     — WAVE-38 LANE N4. WPT's server-root image bank: 77 files,
#                 131 KB total at the pinned SHA. css/ tests reference it
#                 SERVER-ROOT-RELATIVE (`url(/images/green.png)`), which
#                 extract-fixture.mjs resolves as join(tools/wpt, payload),
#                 so with the directory absent EVERY such url() failed the
#                 `fs.readFile` in `inlineUrlsInValue` and the owning
#                 component was marked `_lossy` + 'requires-bundled-asset'
#                 instead of carrying its data URI. That silently defeated
#                 the whole css-images/image-set/ family (39 tests, 52
#                 references to /images/{green,red}.png) plus
#                 css-images/image-light-dark: the tests paint a green
#                 image, we painted nothing. The directory is three orders
#                 of magnitude smaller than fonts/, so there is no size
#                 argument for keeping it out — it was simply never needed
#                 until the asset inliner (wave 8) existed.
git -C "$WPT_DIR" sparse-checkout set css resources fonts images \
    || { echo "fetch-wpt: sparse-checkout set failed" >&2; exit 2; }

# ---------------------------------------------------------------------------
# Fetch the pinned SHA + check it out (Section 3.2 reproducibility).
# ---------------------------------------------------------------------------
# Fetch only the pinned commit (not all branches) — keeps `.git/` lean.
# `--filter=blob:none` is inherited from the partial-clone setup so the
# fetch itself stays small.
echo "fetch-wpt: fetching $PINNED_REF..."
git -C "$WPT_DIR" fetch --filter=blob:none origin "$PINNED_REF" \
    || { echo "fetch-wpt: fetch of $PINNED_REF failed" >&2; exit 2; }
# Detached HEAD on the pinned SHA — TITAN runs are read-only, no commits
# happen in tools/wpt/, so detached is the right state.
echo "fetch-wpt: checking out $PINNED_REF..."
git -C "$WPT_DIR" checkout --detach FETCH_HEAD \
    || { echo "fetch-wpt: checkout of $PINNED_REF failed" >&2; exit 2; }

# ---------------------------------------------------------------------------
# Sanity-check: confirm /css/ is populated.  An empty /css/ would mean
# the sparse config silently dropped it (e.g. because of a typo or a
# stale .git/info/sparse-checkout from a previous incompatible setup).
# ---------------------------------------------------------------------------
if [[ ! -d "$WPT_DIR/css" ]]; then
    echo "fetch-wpt: tools/wpt/css/ is missing after checkout" >&2
    exit 2
fi
# Same check for the server-root image bank. A stale sparse config from a
# pre-wave-38 checkout would leave this absent and the failure mode is
# INVISIBLE downstream — the asset inliner just marks components lossy and
# the run scores a green-image test against a blank box. Fail loudly here
# instead.
if [[ ! -d "$WPT_DIR/images" ]]; then
    echo "fetch-wpt: tools/wpt/images/ is missing after checkout" >&2
    exit 2
fi

# Report final size to caller / log so the Section 8 success criterion
# (≤2 GB) is auditable from the script output alone.
echo "fetch-wpt: done. checkout size:"
du -sh "$WPT_DIR" 2>/dev/null | sed 's/^/  /'
echo "fetch-wpt: HEAD = $( git -C "$WPT_DIR" rev-parse HEAD )"
