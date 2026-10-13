#!/usr/bin/env bash
# skeptic-mut.sh <worktree> <log> <label> <repo-rel-file> <python-edit-file> -- <test filters...>
# The L6 skeptic's bracketed mutation replay: MUTATING (sha256 BEFORE the edit) → edit (the python file holds two
# strings OLD / NEW separated by a line '=====', several pairs separated by '#####'; each OLD occurs exactly once) → focused run (expect red) → byte-exact
# restore from a copy → RESTORED (sha256 after) → focused run (expect green). Never touches the shared tree.
set -uo pipefail
WT="$1"; LOG="$2"; L="$3"; REL="$4"; EDIT="$5"; shift 6
F="$WT/$REL"; BK="$WT/.sk-mut-backup/$(basename "$REL").$L"; mkdir -p "$WT/.sk-mut-backup"
run() {  # focused :runtime run → "rc=… SUMMARY … FAIL …"
  local R="$WT/runtimes/compose/build/test-results/testDebugUnitTest"; rm -rf "$R"
  local args=(); for f in "$@"; do args+=(--tests "$f"); done
  local out; out=$(cd "$WT/apps/android-harness" && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:testDebugUnitTest "${args[@]}" -q 2>&1); local rc=$?
  echo "rc=$rc $(echo "$out" | grep -E '^e: ' | head -2 | tr '\n' ' ')$(/opt/homebrew/bin/python3.12 -B - "$R" <<'PY'
import sys, glob, os, xml.etree.ElementTree as ET
t = f = n = 0; bad = []
for x in sorted(glob.glob(os.path.join(sys.argv[1], 'TEST-*.xml'))):
    r = ET.parse(x).getroot(); n += 1; t += int(r.get('tests'))
    for c in r.iter('testcase'):
        if c.find('failure') is not None or c.find('error') is not None: bad.append(r.get('name').split('.')[-1] + '::' + c.get('name'))
print(f'SUMMARY suites={n} tests={t} failed={len(bad)} ' + ' | '.join(bad))
PY
)"
}
pre=$(shasum -a 256 "$F" | cut -d' ' -f1); cp "$F" "$BK"
echo "MUTATING $REL sha256 $pre   [$L]" >> "$LOG"
/opt/homebrew/bin/python3.12 -B - "$F" "$EDIT" <<'PY' || { echo "  $L EDIT FAILED" >> "$LOG"; cp "$BK" "$F"; exit 1; }
import sys
p, e = sys.argv[1:3]; s = open(p).read()
for pair in open(e).read().split('\n#####\n'):  # several OLD/NEW pairs in one mutation
    old, new = pair.split('\n=====\n')
    assert s.count(old) == 1, f'anchor count {s.count(old)}'
    s = s.replace(old, new)
open(p, 'w').write(s)
PY
echo "  $L mutated  → $(run "$@")" >> "$LOG"
cp "$BK" "$F"; post=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "RESTORED $REL sha256 $post   [$L; post == pre: $([ "$pre" == "$post" ] && echo yes || echo NO)]" >> "$LOG"
echo "  $L restored → $(run "$@")" >> "$LOG"
tail -4 "$LOG"
