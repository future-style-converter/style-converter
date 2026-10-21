#!/usr/bin/env bash
# mut.sh <label> <repo-rel-file> <old1:new1[,old2:new2…]> - <test filters...>
# Bracketed pin mutation: MUTATING line BEFORE the edit, red run, byte-exact restore, RESTORED line, green run.
set -uo pipefail
WT="${L6_WT:?set L6_WT to the lane worktree (the TREES line of _note.md)}"
NOTE="$(cd "$(dirname "$0")" && pwd)/_note.md"
S=$(dirname "$0")
L="$1"; REL="$2"; PAIRS="$3"; shift 4
F="$WT/$REL"; BK="$WT/.l6-mut-backup/$(basename "$REL").$L"
mkdir -p "$WT/.l6-mut-backup"
pre=$(shasum -a 256 "$F" | cut -d' ' -f1)
cp "$F" "$BK"
echo "MUTATING $REL sha256 $pre   [$L; byte-exact copy in the TREES worktree under .l6-mut-backup/]" >> "$NOTE"
python3 - "$F" "$PAIRS" <<'PY' || { echo "EDIT FAILED"; cp "$BK" "$F"; exit 1; }
import sys
p,pairs=sys.argv[1:3]; s=open(p).read()
for pair in pairs.split(','):
    o,n=pair.split(':'); a=open(o).read(); b=open(n).read()
    assert s.count(a)==1, "anchor count %d for %s" % (s.count(a), o)
    s=s.replace(a,b)
open(p,'w').write(s)
PY
red=$("$S/l6-rt.sh" "$@" 2>&1); redsum=$(echo "$red" | grep -E '^SUMMARY|^FAIL|gradle rc|e: ' | tr '\n' ' ' | cut -c1-600)
echo "  $L mutated  → $redsum" >> "$NOTE"
cp "$BK" "$F"
post=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "RESTORED $REL sha256 $post   [$L; post == pre: $([ "$pre" == "$post" ] && echo yes || echo NO)]" >> "$NOTE"
green=$("$S/l6-rt.sh" "$@" 2>&1); greensum=$(echo "$green" | grep -E '^SUMMARY|^FAIL|gradle rc' | tr '\n' ' ')
echo "  $L restored → $greensum" >> "$NOTE"
echo "$L: RED[$redsum] GREEN[$greensum] pre=$pre post=$post"
