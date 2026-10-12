#!/bin/bash
# wave-55 L2 (cbb-android) mutation runner — one literal replacement in one file of the lane worktree,
# bracketed in the lane note (PLAN §0 "Mutations are bracketed", §8 step 3 RESUME rule):
#   1. append `MUTATING <file> sha256 <pre>` to _note.md BEFORE the edit;
#   2. apply the replacement (the old literal must occur exactly once) and run the focused test (expected RED);
#   3. restore the file byte-exact from a copy, verify sha256 post == pre, append `RESTORED <file> sha256 <post>`;
#   4. run the same focused test again (expected GREEN).
# Usage: mutate.sh <label> <repo-relative file> <test-filter> [<test-filter> ...]
#        (old/new literals are read from mutations/lit/<label>.old and <label>.new beside this script)
set -u
label=$1; rel=$2; shift 2
HERE="$(cd "$(dirname "$0")" && pwd)"
NOTE="$HERE/_note.md"
WT="$(sed -n 's/^TREES: //p' "$NOTE" | head -1)"
file="$WT/$rel"; old="$HERE/mutations/lit/$label.old"; new="$HERE/mutations/lit/$label.new"
OUT="$HERE/mutations"; mkdir -p "$OUT"
filters=(); for t in "$@"; do filters+=(--tests "$t"); done
run() {  # run the focused filters in the worktree; print the JUnit tally and failing tests
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  rm -rf "$WT/runtimes/compose/build/test-results/testDebugUnitTest"
  # Gradle's failure line names the report under the worktree: scrub the path (no throwaway-worktree path in a record).
  (cd "$WT/apps/android-harness" && nice ./gradlew :runtime:testDebugUnitTest "${filters[@]}" 2>&1) | sed "s|$WT|<lane worktree>|g" > "$1"; local rc=${PIPESTATUS[0]}
  for f in "$WT"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml; do
    [ -f "$f" ] && grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | sed 's/<testsuite name="com.styleconverter.runtime.//'
  done >> "$1.tally"
  return $rc
}
pre=$(shasum -a 256 "$file" | cut -d' ' -f1)
echo "MUTATING $rel sha256 $pre   (label $label)" >> "$NOTE"
cp "$file" "$OUT/.$label.restore"
/opt/homebrew/bin/python3.12 -I - "$file" "$old" "$new" <<'PY'
import sys
p, o, n = sys.argv[1], open(sys.argv[2]).read(), open(sys.argv[3]).read()
s = open(p).read()
assert s.count(o) == 1, f"anchor count {s.count(o)}"
open(p, 'w').write(s.replace(o, n))
PY
applied=$?
mutated=$(shasum -a 256 "$file" | cut -d' ' -f1)
rm -f "$OUT/$label.red.txt.tally" "$OUT/$label.green.txt.tally"
rm -f "$WT"/runtimes/compose/build/*census.now.txt   # no stale census record can pose as the mutated run's
if [ $applied -eq 0 ]; then run "$OUT/$label.red.txt"; red=$?; else red=99; fi
# Keep the census records the mutated run wrote (the restored run overwrites them).
for c in "$WT"/runtimes/compose/build/*census.now.txt; do [ -f "$c" ] && cp "$c" "$OUT/$label.red.$(basename "$c")"; done
rm -f "$WT"/runtimes/compose/build/*census.now.txt
cp "$OUT/.$label.restore" "$file"; rm -f "$OUT/.$label.restore"
post=$(shasum -a 256 "$file" | cut -d' ' -f1)
echo "RESTORED $rel sha256 $post   (label $label; $([ "$pre" = "$post" ] && echo 'post == pre' || echo 'MISMATCH'))" >> "$NOTE"
run "$OUT/$label.green.txt"; green=$?
{
  echo "label=$label file=$rel"
  echo "sha256 pre=$pre mutated=$mutated post=$post restored=$([ "$pre" = "$post" ] && echo OK || echo MISMATCH)"
  echo "mutation applied=$([ $applied -eq 0 ] && echo yes || echo NO)"
  echo "mutated run rc=$red ($([ $red -ne 0 ] && echo RED || echo GREEN))"
  grep -E 'FAILED$|tests completed' "$OUT/$label.red.txt" 2>/dev/null | sed 's/^/  red: /'
  echo "restored run rc=$green ($([ $green -ne 0 ] && echo RED || echo GREEN))"
  grep -E 'tests completed|BUILD' "$OUT/$label.green.txt" | sed 's/^/  green: /'
  sed 's/^/  green tally: /' "$OUT/$label.green.txt.tally" 2>/dev/null
} | tee "$OUT/$label.summary.txt"
