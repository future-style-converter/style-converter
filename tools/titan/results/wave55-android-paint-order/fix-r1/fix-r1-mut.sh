#!/usr/bin/env bash
# fix-r1/fix-r1-mut.sh <label> <repo-rel-file> <edit-file> -- <test filters...>
# The L6 fix lane's bracketed mutation (PLAN §0 "Mutations are bracketed"): writes `MUTATING <file> sha256 <pre>` to
# ../_note.md BEFORE the edit, applies the edit (edit-file: OLD / NEW separated by a line '=====', several pairs separated
# by '#####'; each OLD occurs exactly once), runs the focused :runtime classes (expect red), restores the byte-exact copy,
# writes `RESTORED <file> sha256 <post>` (post == pre), and runs them again (expect green). Worktree = $L6_WT (TREES line).
set -uo pipefail
WT="${L6_WT:?set L6_WT to the fix worktree (the first TREES line of _note.md)}"
HERE="$(cd "$(dirname "$0")" && pwd)"; NOTE="$HERE/../_note.md"
L="$1"; REL="$2"; EDIT="$3"; shift 4
F="$WT/$REL"; BK="$WT/.l6-fix-mut-backup/$(basename "$REL").$L"; mkdir -p "$WT/.l6-fix-mut-backup"
pre=$(shasum -a 256 "$F" | cut -d' ' -f1); cp "$F" "$BK"
echo "MUTATING $REL sha256 $pre   [fix-r1 $L; byte-exact copy in the TREES worktree under .l6-fix-mut-backup/]" >> "$NOTE"
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
red=$("$HERE/../l6-rt.sh" "$@" 2>&1); redsum=$(echo "$red" | grep -E '^SUMMARY|^FAIL|gradle rc|^e: ' | tr '\n' ' ' | cut -c1-900)
echo "  fix-r1 $L mutated  → $redsum" >> "$NOTE"
cp "$BK" "$F"; post=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "RESTORED $REL sha256 $post   [fix-r1 $L; post == pre: $([ "$pre" == "$post" ] && echo yes || echo NO)]" >> "$NOTE"
green=$("$HERE/../l6-rt.sh" "$@" 2>&1); greensum=$(echo "$green" | grep -E '^SUMMARY|^FAIL|gradle rc' | tr '\n' ' ')
echo "  fix-r1 $L restored → $greensum" >> "$NOTE"
echo "$L: RED[$redsum] GREEN[$greensum] pre=$pre post=$post"
