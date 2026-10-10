#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/seam-verify.sh — wave 54 lane L3: verify pins with ONE seam patch applied
# under the per-file lock (build-workflow rule 2). Usage:
#   seam-verify.sh <label> <seam-path> <patch>[,<patch2>…] -- <command…>
# mkdir tools/titan/runs/wave54-lock/<basename> is the mutex (retry every 60 s); the seam file must equal HEAD before the
# patch is applied; it is restored byte-exact from HEAD on EVERY exit path (trap), sha256 logged before/applied/after,
# then the lock is removed. Patch-borne NEW files (e.g. a test file the patch creates) are removed on restore too.
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../.." && pwd)"; HERE="$R/tools/titan/results/wave54-hyphenate-character"; LOG="$HERE/seam-verify.log"
label="$1"; seam="$2"; patches="$3"; shift 3; [ "$1" = "--" ] && shift
LOCK="$R/tools/titan/runs/wave54-lock/$(basename "$seam")"; mkdir -p "$(dirname "$LOCK")"
until mkdir "$LOCK" 2>/dev/null; do echo "lock $LOCK held; waiting 60 s"; sleep 60; done
head_sha=$(git -C "$R" show "HEAD:$seam" | shasum -a 256 | cut -d' ' -f1)
created=()
restore() {
  git -C "$R" show "HEAD:$seam" > "$R/$seam"
  for f in "${created[@]:-}"; do [ -n "$f" ] && rm -f "$R/$f"; done
  after=$(shasum -a 256 "$R/$seam" | cut -d' ' -f1)
  echo "sha256 restored: $after $( [ "$after" = "$head_sha" ] && echo BYTE-EXACT-TO-HEAD || echo MISMATCH)" >> "$LOG"
  rmdir "$LOCK"
}
before=$(shasum -a 256 "$R/$seam" | cut -d' ' -f1)
{ echo "=== $label  ($(date '+%Y-%m-%d %H:%M:%S'))"; echo "seam: $seam"; echo "sha256 HEAD:    $head_sha"; echo "sha256 before:  $before"; } >> "$LOG"
if [ "$before" != "$head_sha" ]; then echo "seam file differs from HEAD before apply — refusing" | tee -a "$LOG"; rmdir "$LOCK"; exit 7; fi
trap restore EXIT
IFS=',' read -ra PS <<< "$patches"
for p in "${PS[@]}"; do
  for nf in $(grep -E '^\+\+\+ b/' "$p" | sed 's#^+++ b/##' | cut -f1); do [ -e "$R/$nf" ] || created+=("$nf"); done
  git -C "$R" apply "$p" || { echo "apply failed: $p" | tee -a "$LOG"; exit 6; }
done
echo "sha256 applied: $(shasum -a 256 "$R/$seam" | cut -d' ' -f1)  (patches: $patches)" >> "$LOG"
out=$("$@" 2>&1); rc=$?
echo "command exit=$rc: $*" >> "$LOG"
echo "$out" | grep -E 'BUILD|tests completed|Executed|# (pass|fail|tests)|FAILED|error:' | head -12 | sed 's/^/  > /' >> "$LOG"
echo "$out" | tail -40
exit $rc
