#!/usr/bin/env bash
# mutate.sh <label> <file(rel to WT)> <py-file: reads s, writes s> <log> -- <test cmd…>
# Bracketed mutation: MUTATING line to the lane note BEFORE the edit; red run; byte-exact restore; RESTORED line; green run.
set -uo pipefail
WT="${L7_WT:?set L7_WT to the lane worktree}"
NOTE="${L7_NOTE:?set L7_NOTE to the lane note}"
label="$1"; f="$2"; py="$3"; log="$4"; shift 5
cd "$WT"; BK="$(mktemp)"; cp -p "$f" "$BK"; pre="$(shasum -a 256 "$f" | cut -d' ' -f1)"
echo "MUTATING $f sha256 $pre   # $label" >> "$NOTE"
/usr/bin/python3 - "$f" "$py" <<'PY'
import sys; f, py = sys.argv[1], sys.argv[2]
s = open(f, encoding='utf-8').read(); g = {'s': s}; exec(open(py).read(), g)
assert g['s'] != s, 'mutation changed nothing'; open(f, 'w', encoding='utf-8').write(g['s'])
PY
mrc=$?
{ echo "=== $label — mutated $f (python rc $mrc; sha256 now $(shasum -a 256 "$f" | cut -c1-16)…)"; cmd="${*//$WT/<wt>}"; echo "--- red run: ${cmd//${L7_SHARED:-@@none@@}\//}"; } >> "$log"
"$@" > "$log.tmp" 2>&1; rrc=$?
grep -E '^not ok|^# (pass|fail) |FAILED|BUILD|Tests? (run|Suite)|\*\* TEST|error:| FAIL |Test Files |^ +Tests +|AssertionError|[×✗] |Test Case .* failed' "$log.tmp" | head -40 | sed "s|$WT|<wt>|g" >> "$log"; echo "red run rc=$rrc" >> "$log"
cp -p "$BK" "$f"; rm -f "$BK"; post="$(shasum -a 256 "$f" | cut -d' ' -f1)"
echo "RESTORED $f sha256 $post   # $label$([ "$pre" == "$post" ] && echo ' (== pre)' || echo ' (!= pre — NOT RESTORED)')" >> "$NOTE"
echo "--- green run" >> "$log"; "$@" > "$log.tmp" 2>&1; grc=$?
grep -E '^not ok|^# (pass|fail) |FAILED|BUILD|Tests? (run|Suite)|\*\* TEST|error:| FAIL |Test Files |^ +Tests +|AssertionError|[×✗] |Test Case .* failed' "$log.tmp" | head -20 | sed "s|$WT|<wt>|g" >> "$log"; echo "green run rc=$grc"$'\n' >> "$log"; rm -f "$log.tmp"
echo "$label: mutation rc=$mrc red rc=$rrc green rc=$grc restored=$([ "$pre" == "$post" ] && echo yes || echo NO)"
