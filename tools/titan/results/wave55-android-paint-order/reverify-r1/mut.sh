#!/usr/bin/env bash
# reverify-r1/mut.sh <label> <repo-rel-file> <edit-file> -- <test filters...> — the L6 re-verifier's bracketed mutation,
# run in the re-verifier's worktree ($L6_WT) on the lane's CURRENT patches. Writes `MUTATING <file> sha256 <pre>` to
# reverify-r1/mutations.log.txt BEFORE the edit (not to ../_note.md, whose last line must stay `STATUS: COMPLETE`),
# applies the edit (OLD / NEW split by a '=====' line, pairs split by '#####'; each OLD must occur exactly once), runs the
# focused classes (expect red), restores the byte-exact copy, writes `RESTORED <file> sha256 <post>`, runs again (green).
set -uo pipefail
WT="${L6_WT:?set L6_WT to the re-verifier worktree}"
HERE="$(cd "$(dirname "$0")" && pwd)"; LOG="$HERE/mutations.log.txt"
L="$1"; REL="$2"; EDIT="$3"; shift 4
F="$WT/$REL"; BK="$WT/.l6-reverify-mut-backup/$(basename "$REL").$L"; mkdir -p "$WT/.l6-reverify-mut-backup"
pre=$(shasum -a 256 "$F" | cut -d' ' -f1); cp "$F" "$BK"
echo "MUTATING $REL sha256 $pre   [reverify $L; byte-exact copy under the re-verifier worktree's .l6-reverify-mut-backup/]" >> "$LOG"
/opt/homebrew/bin/python3.12 -B - "$F" "$EDIT" <<'PY' || { echo "  $L EDIT FAILED" >> "$LOG"; cp "$BK" "$F"; exit 1; }
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
echo "  reverify $L mutated  → $redsum" >> "$LOG"
cp "$BK" "$F"; post=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "RESTORED $REL sha256 $post   [reverify $L; post == pre: $([ "$pre" == "$post" ] && echo yes || echo NO)]" >> "$LOG"
green=$("$HERE/../l6-rt.sh" "$@" 2>&1); greensum=$(echo "$green" | grep -E '^SUMMARY|^FAIL|gradle rc' | tr '\n' ' ')
echo "  reverify $L restored → $greensum" >> "$LOG"
echo "$L: RED[$redsum] GREEN[$greensum] pre=$pre post=$post"
