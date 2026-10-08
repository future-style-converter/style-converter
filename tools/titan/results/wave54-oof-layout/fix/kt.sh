#!/bin/bash
# L4 fix pass (wave 54; copy of skeptic/kt.sh, logs under fix/runs/): focused Compose JUnit run with rule-3 retry on a
# FOREIGN compile error or an IC-cache error. Prints a per-class summary from
# the JUnit XML (fresh files only) and exits with gradle's rc.
# Usage: kt.sh <label> <test-filter>...
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
OUT=$ROOT/tools/titan/results/wave54-oof-layout/fix/runs
label=$1; shift
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
args=(); for f in "$@"; do args+=(--tests "$f"); done
for i in 1 2 3 4 5 6; do
  stamp=$(date +%s)
  ( cd "$ROOT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest "${args[@]}" --console=plain ) > "$OUT/$label.gradle.txt" 2>&1; rc=$?
  if grep -q 'Compilation error\|e: file://' "$OUT/$label.gradle.txt" && [ $rc -ne 0 ]; then
    if grep -q 'IC\b\|incremental\|Could not connect to Kotlin compile daemon' "$OUT/$label.gradle.txt"; then rm -rf "$ROOT/runtimes/compose/build/kotlin"; fi
    echo "attempt $i: compile error (see $label.gradle.txt) — waiting 180s" >&2
    grep 'e: file://' "$OUT/$label.gradle.txt" | head -5 >&2
    perl -e 'select(undef,undef,undef,180)'; continue
  fi
  break
done
python3 - "$ROOT/runtimes/compose/build/test-results/testDebugUnitTest" "$stamp" > "$OUT/$label.counts.txt" <<'PY'
import sys, os, re, glob
d, stamp = sys.argv[1], int(sys.argv[2])
tot = [0,0,0,0]
for f in sorted(glob.glob(os.path.join(d, '*.xml'))):
    if os.path.getmtime(f) < stamp - 5: continue
    s = open(f).read()
    m = re.search(r'<testsuite name="([^"]+)" tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', s)
    if not m: continue
    n, t, sk, fa, er = m.group(1), *map(int, m.groups()[1:])
    tot = [tot[0]+t, tot[1]+sk, tot[2]+fa, tot[3]+er]
    fails = re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', s)
    print(f"{n.split('.')[-1]} tests={t} skipped={sk} failures={fa} errors={er}" + (f" FAILED: {fails}" if fails else ""))
print(f"TOTAL tests={tot[0]} skipped={tot[1]} failures={tot[2]} errors={tot[3]}")
PY
echo "gradle rc=$rc" >> "$OUT/$label.counts.txt"
cat "$OUT/$label.counts.txt"
exit $rc
