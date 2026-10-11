#!/usr/bin/env bash
# tools/titan/results/wave55-gate/ab-arms55.revert-check-proof.sh [<scratch>] — proves ab-arms55.sh's revert check (plan-skeptic
# round 2, process-holes M1) accepts the arms the plan designs and still refuses a revert that cannot apply, WITHOUT any git
# state: the function is extracted verbatim from the runner and fed by a `gitr` stub that serves hand-written commit diffs
# (U, X, W rewriting U's line; N creating a file, Y, B extending it) over a scratch "current tree"; `git apply` runs only as a
# plain patch tool in non-repository scratch dirs (GIT_CEILING_DIRECTORIES). The runner's own git-init self-test
# (ab-arms55.selftest.sh T5j / T5l / T5m + mutant M-onebyone) is the end-to-end proof, re-run by the orchestrator (PLAN §8
# step 6); this file is the fix pass's executed evidence on the current bytes.
#   R1 'W U' (child first, not a first-parent chain): the NEW check applies the reverses cumulatively → ok (exit 0)
#   R2 'U' alone with W on the tree: refused (the chain path, `git apply -R --check` on the current tree)
#   R3 'B N' (N creates n.txt, B extends it): ok
#   R4 'N' alone with B on the tree: refused
#   R5 MUTATION: the round-1 per-commit check (each reverse alone vs the current tree) refuses R1 and R3 — the defect
#   R6 the check writes nothing in the "current tree" (sha of every file before == after) and leaves no mktemp dir behind
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; SH="$(cd "$HERE/../../../.." && pwd -P)"
# round 3 (verify N6): a named scratch dir that does not exist yet is created; an unusable one is refused (it used to leave S
# empty, and the redaction sed below then died on an empty regex by SIGPIPE, exit 141, after the header line)
if [[ -n "${1:-}" ]]; then mkdir -p "$1" || { echo "refusing: cannot create the scratch dir $1"; exit 2; }; fi
S="$(cd "${1:-$(mktemp -d)}" && pwd -P)"; [[ -n "$S" ]] || { echo "refusing: no usable scratch dir"; exit 2; }
case "$S/" in "$SH"/*) echo "refusing: scratch inside the tree"; exit 2 ;; esac
OUTF="$HERE/ab-arms55.revert-check-proof.out.txt"
exec > >(sed -u -E "s#$S#<scratch>#g; s#(/private)?/tmp/claude-[^ ]*#<scratch path>#g" | tee "$OUTF") 2>&1
echo "# ab-arms55.revert-check-proof — $(date -u +%Y-%m-%dT%H:%M:%SZ); runner sha256 $(shasum -a 256 "$HERE/ab-arms55.sh" | cut -c1-16)…"
FAILS=0
P="$S/patches"; AB_ROOT="$S/tree"; rm -rf "$P" "$AB_ROOT" "$S/tmp"; mkdir -p "$P" "$AB_ROOT/src" "$S/tmp"; export TMPDIR="$S/tmp/"
# the commits (parent map: base ← U ← X ← W ← N ← Y ← B ← HEAD); the current tree = after B
printf 'U base\nX U\nW X\nN W\nY N\nB Y\n' > "$S/parents"
cat > "$P/U" <<'D'
diff --git a/src/a.txt b/src/a.txt
--- a/src/a.txt
+++ b/src/a.txt
@@ -1 +1 @@
-a0
+a1
D
cat > "$P/X" <<'D'
diff --git a/src/x.txt b/src/x.txt
new file mode 100644
--- /dev/null
+++ b/src/x.txt
@@ -0,0 +1 @@
+x
D
cat > "$P/W" <<'D'
diff --git a/src/a.txt b/src/a.txt
--- a/src/a.txt
+++ b/src/a.txt
@@ -1 +1 @@
-a1
+a2
D
cat > "$P/N" <<'D'
diff --git a/src/n.txt b/src/n.txt
new file mode 100644
--- /dev/null
+++ b/src/n.txt
@@ -0,0 +1 @@
+n0
D
cat > "$P/Y" <<'D'
diff --git a/src/y.txt b/src/y.txt
new file mode 100644
--- /dev/null
+++ b/src/y.txt
@@ -0,0 +1 @@
+y
D
cat > "$P/B" <<'D'
diff --git a/src/n.txt b/src/n.txt
--- a/src/n.txt
+++ b/src/n.txt
@@ -1 +1,2 @@
 n0
+n1
D
printf 'a2\n' > "$AB_ROOT/src/a.txt"; printf 'x\n' > "$AB_ROOT/src/x.txt"; printf 'n0\nn1\n' > "$AB_ROOT/src/n.txt"; printf 'y\n' > "$AB_ROOT/src/y.txt"
# the gitr stub: rev-parse of <c> / <c>^, diff [--binary|--name-only] <c>^ <c>, apply -R --check on the current tree
gitr() {
  case "$1" in
    rev-parse) local r="${@: -1}"; if [[ "$r" == *^ ]]; then awk -v c="${r%^}" '$1 == c { print $2 }' "$S/parents"; else echo "$r"; fi ;;
    diff) local c="${@: -1}"
          if [[ "$2" == --name-only ]]; then sed -n 's#^diff --git a/\([^ ]*\) b/.*#\1#p' "$P/$c"; else cat "$P/$c"; fi ;;
    apply) (cd "$AB_ROOT" && GIT_CEILING_DIRECTORIES="$AB_ROOT" git apply "${@:2}") ;;
    *) echo "gitr stub: unexpected $*" >&2; return 1 ;;
  esac
}
# the function under test, extracted verbatim from the runner (and the round-1 one from its mutant)
extract() { awk '/^revert_check\(\) \{/ { on = 1 } on { print } on && /^}/ { exit }' "$1"; }
eval "$(extract "$HERE/ab-arms55.sh")"
tree_sha() { (cd "$AB_ROOT" && find . -type f | LC_ALL=C sort | xargs shasum -a 1 | shasum -a 1 | cut -c1-12); }
BEFORE="$(tree_sha)"
want() {  # <label> <want rc 0|1> <shas…>
  local label="$1" w="$2"; shift 2; local msg rc; msg="$(revert_check "$@" 2>&1)"; rc=$?; (( rc == 0 )) || rc=1
  if [[ "$rc" == "$w" ]]; then echo "  as required  $label — rc $rc$( [[ -n "$msg" ]] && echo ": $(tr '\n' ' ' <<<"$msg" | cut -c1-110)")"
  else echo "  !! NOT as required  $label — rc $rc (wanted $w): $(tr '\n' ' ' <<<"$msg" | cut -c1-140)"; FAILS=$((FAILS + 1)); fi; }
echo "## the runner's revert_check (current bytes)"
want "R1 'W U' — the arm INCLUDES W (rewrites U's line); not a chain (W^ = X): the cumulative reverse applies" 0 W U
want "R2 'U' alone with W on the tree: refused (the reverse of U no longer applies)" 1 U
want "R3 'B N' — N creates n.txt, B extends it (Y between): the cumulative reverse applies" 0 B N
want "R4 'N' alone with B on the tree: refused" 1 N
want "R4b 'W U' listed parent first ('U W'): the cumulative order is the arms file's — refused" 1 U W
echo "## R5 MUTATION: the round-1 per-commit check (each reverse alone against the current tree)"
/opt/homebrew/bin/python3.12 -I - "$HERE/ab-arms55.sh" "$S/ab-arms55.M-onebyone.sh" <<'EOF'
import sys
s = open(sys.argv[1]).read(); a = '    local X f c\n'
assert s.count(a) == 1
open(sys.argv[2], 'w').write(s.replace(a, '    for c in "${s[@]}"; do gitr diff --binary "$c^" "$c" | gitr apply -R --check 2>&1 || return 1; done; return 0   # MUTATION M-onebyone\n' + a, 1))
EOF
grep -q 'MUTATION M-onebyone' "$S/ab-arms55.M-onebyone.sh" || { echo "  !! the mutation did not apply"; FAILS=$((FAILS + 1)); }
eval "$(extract "$S/ab-arms55.M-onebyone.sh")"
want "R5a M-onebyone refuses 'W U' (the defect: drop-L4-U2 with an -S1 fix on U2)" 1 W U
want "R5b M-onebyone refuses 'B N' (the defect: drop-NB-ios with NB-ios-b landed)" 1 B N
echo "## R6 nothing written"
[[ "$(tree_sha)" == "$BEFORE" ]] && echo "  as required  R6 the current tree is byte-identical after every check ($BEFORE)" || { echo "  !! R6 the tree changed"; FAILS=$((FAILS + 1)); }
left="$(find "$S/tmp" -mindepth 1 -maxdepth 1 -name 'ab-arms55-rc.*' | wc -l | tr -d ' ')"
[[ "$left" == 0 ]] && echo "  as required  R6 no ab-arms55-rc.* export dir left behind" || { echo "  !! R6 $left export dir(s) left"; FAILS=$((FAILS + 1)); }
echo "ab-arms55.revert-check-proof: $([ $FAILS -eq 0 ] && echo 'every check as required' || echo "$FAILS check(s) NOT as required")"
exit $FAILS
