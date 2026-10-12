#!/usr/bin/env bash
# fix-r2/mut.sh <label> <repo-rel-file> <edit-file> -- <test filters...>
# The L6 fix-r2 bracketed mutation (PLAN §0 "Mutations are bracketed"): writes `MUTATING <file> sha256 <pre>` to
# ../_note.md BEFORE the edit, applies the edit (edit-file: OLD / NEW separated by a line '=====', several pairs separated
# by '#####'; each OLD occurs exactly once), runs the focused :runtime classes, and — when CENSUS_OUT is set — runs
# fix-r2/census.sh on the MUTATED tree into $CENSUS_OUT; then restores the byte-exact copy, writes
# `RESTORED <file> sha256 <post>` (post == pre), and runs the classes again. Worktree = $L6_WT (the TREES line naming
# wave55-fix2-L6). Same edit format and bracket order as fix-r1/fix-r1-mut.sh and reverify-r1/mut.sh.
set -uo pipefail
WT="${L6_WT:?set L6_WT to the fix-r2 worktree (the TREES line naming wave55-fix2-L6)}"
HERE="$(cd "$(dirname "$0")" && pwd)"; NOTE="$HERE/../_note.md"
L="$1"; REL="$2"; EDIT="$3"; shift 4
F="$WT/$REL"; BK="$WT/.l6-fix2-mut-backup/$(basename "$REL").$L"; mkdir -p "$WT/.l6-fix2-mut-backup"
pre=$(shasum -a 256 "$F" | cut -d' ' -f1); cp "$F" "$BK"
echo "MUTATING $REL sha256 $pre   [fix-r2 $L; byte-exact copy in the TREES worktree under .l6-fix2-mut-backup/]" >> "$NOTE"
/opt/homebrew/bin/python3.12 -B - "$F" "$EDIT" <<'PY' || { echo "  $L EDIT FAILED" >> "$NOTE"; cp "$BK" "$F"; exit 1; }
import sys
p, e = sys.argv[1:3]; s = open(p).read()
for pair in open(e).read().split('\n#####\n'):
    old, new = pair.split('\n=====\n')
    old = old.rstrip('\n'); new = new.rstrip('\n')
    assert s.count(old) == 1, f'anchor count {s.count(old)} for {old[:60]!r}'
    s = s.replace(old, new)
open(p, 'w').write(s)
PY
red=$("$HERE/../l6-rt.sh" "$@" 2>&1); redsum=$(echo "$red" | grep -E '^SUMMARY|^FAIL|gradle rc|^e: ' | tr '\n' ' ' | cut -c1-1200)
echo "  fix-r2 $L mutated  → $redsum" >> "$NOTE"
if [ -n "${CENSUS_OUT:-}" ]; then
  cen=$(bash "$HERE/census.sh" "$WT" wave54-final "$CENSUS_OUT" 2>&1 | tail -1 | sed -e 's#^rows: \([0-9]*\) -> .*#rows: \1#')
  echo "  fix-r2 $L mutated  → census on the mutated tree: $cen (TSV: fix-r2/$(basename "$CENSUS_OUT").gz)" >> "$NOTE"
fi
cp "$BK" "$F"; post=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "RESTORED $REL sha256 $post   [fix-r2 $L; post == pre: $([ "$pre" == "$post" ] && echo yes || echo NO)]" >> "$NOTE"
green=$("$HERE/../l6-rt.sh" "$@" 2>&1); greensum=$(echo "$green" | grep -E '^SUMMARY|^FAIL|gradle rc' | tr '\n' ' ')
echo "  fix-r2 $L restored → $greensum" >> "$NOTE"
echo "$L: RED[$redsum] GREEN[$greensum] pre=$pre post=$post"
