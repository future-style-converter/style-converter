# RECORD of the wave-54 A/B arm runner (copied from the session scratchpad at ship time; ROOT below is the wave-54 worktree path).
# KNOWN DEFECT, recorded in _note.md "A/B arms" and build-hashes.txt: it appends the installed-build block on the throwaway arm
# branch and then restores the tree with `git checkout -f`, which discarded the three blocks — a later runner writes records only
# after the restore (wave-54 lesson L18). The read-outs (final/ab-*.txt) are written after the restore and survived.
#!/usr/bin/env bash
# Wave 54 A/B arms (PLAN §8 step 12; expectations.abArms / abRead / beforeEveryDeviceRun). Runs AFTER both halves of
# wave54-final, devices idle. Each arm: the integrated tree ef20c977-line HEAD with ONE unit reverted on a throwaway branch
# (the revert commits were dry-run clean in a scratch worktree), devices re-provisioned (the markers removed, so the apk/.app
# are rebuilt from the arm's tree), the section(s) run as wave54-ab-<arm>, the installed-build hash read right after
# `attempt 1 starting`, the tree restored, then the read-out ARM FIRST: Δ = wave54-final − arm = what the dropped unit did.
set -uo pipefail
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf; cd "$ROOT"
G=tools/titan/results/wave54-gate; OUT=$G/final; mkdir -p "$OUT"
BRANCH=campaign/applier-campaign; BASE=$(git rev-parse --short HEAD)
export PROJECT_ROOT="$ROOT"; source tools/titan/own-processes.sh
ARMS=(
  # arm          sections              platforms(read)   commits to revert (child first)
  "drop-W1      css-text              web               6e1c2d5e 8846e304"
  "drop-Mprime  css-counter-styles    web,ios,android   d0b1f3e5 b312c933"
  "drop-U3b     css-text              web,ios,android   d64d4c6e"
)
[[ -n "$(git status --short)" ]] && { echo "tree not clean — refusing"; exit 2; }
for spec in "${ARMS[@]}"; do
  read -r arm secs plats shas <<<"$spec"; run=wave54-ab-$arm; log=/tmp/$run.driver.out
  echo "=== $arm: revert $shas, sections $secs, read platforms $plats ==="
  kill_own_gradle_daemons "$ROOT"                                   # beforeEveryDeviceRun — never ./gradlew --stop
  git checkout -q -B "$run" "$BASE" && git revert --no-edit $shas >/dev/null || { echo "revert failed for $arm"; git revert --abort 2>/dev/null; git checkout -q -f "$BRANCH"; exit 3; }
  echo "arm tree: $(git rev-parse --short HEAD)  ($(git log --format=%s -1 | cut -c1-80))"
  rm -f /tmp/titan-device-pool/provisioned-*                        # force re-provision: rebuild + reinstall from the arm's tree
  tools/titan/gate-driver.sh "$run" --sections "$secs" --skip-fixture-net >"$log" 2>&1 & dpid=$!
  for i in $(seq 1 60); do grep -q 'attempt 1 starting' "$log" 2>/dev/null && break; kill -0 $dpid 2>/dev/null || break; sleep 15; done
  if grep -q 'attempt 1 starting' "$log"; then bash $G/installed-build-hash.sh "$run (A/B arm: $arm = HEAD minus $shas; sections $secs; arm tree $(git rev-parse --short HEAD))" | tail -4
  else echo "driver never reached 'attempt 1 starting' for $arm:"; tail -8 "$log"; fi
  wait $dpid; echo "driver rc=$?"; grep -E 'OK|FAIL|short|abort' "$log" | tail -4 | cut -c1-160
  git checkout -q -f "$BRANCH" && git branch -q -D "$run" && echo "tree restored to $(git rev-parse --short HEAD) ($BRANCH)"
  node tools/titan/results/wave52-gate/ab-diff.mjs "$run" wave54-final --threshold 0.002 --platforms "$plats" > "$OUT/ab-$arm.txt" 2>&1
  echo "read-out ($OUT/ab-$arm.txt):"; grep -vE '^\s*$' "$OUT/ab-$arm.txt" | head -24 | cut -c1-170
done
[[ "$(git rev-parse --short HEAD)" == "$BASE" ]] && echo "ALL ARMS DONE — tree at $BASE on $(git branch --show-current)" || echo "WARNING: tree not at $BASE"
