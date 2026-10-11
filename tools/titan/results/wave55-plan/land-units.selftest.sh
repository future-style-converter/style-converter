#!/usr/bin/env bash
# land-units.selftest.sh <scratch-dir> — rehearses wave55-plan/land-units.sh in THROWAWAY worktrees and proves each of its
# guards can fail. The shared tree is never touched: the only git commands this script runs itself on a repository are
# `git worktree add --detach <scratch>/wt-* HEAD` and `git worktree remove` of the same dirs; everything else is either
# the landing script under test (acting on a throwaway through --root) or read-only git (log, show, rev-parse,
# status --no-optional-locks, diff --no-index between scratch files). Synthetic patches are cut with `git diff --no-index`
# from a scratch MODEL of the files, so each one is cut on the tree it lands on (seam-registry §2) — stacked where the
# real lanes stack. Every synthetic line says SYNTHETIC; the throwaways (and their dangling commits) are discarded.
#   A  the real path, ONE unit + ONE seam (L1 NB-ios): --check clean and read-only (git-call log), the freshness guard
#      refuses first, then records + NB-ios land in order, the tree is clean, the landed bytes equal the model's
#   B  failure paths on L1: fault knob → reverse-apply restore → --from resume; dirty path; non-applying seam; a unit
#      patch editing a seam file; manifest drift; an ORDER violation; the --check mutation guard (exit 70); a busy tree
#   C  stacked series (L4 four units + L2 pair): alone-fails-but-cumulative-applies, C12 revert dry-runs OK, 7 commits
#      in order; then a fake L2 seam inside L4's context turns the L4 revert dry-run red
#   D  L7: an optional unit with no patch needs its planning files tracked; a loose copy blocks a delivered patch; the
#      records commit force-adds the six gitignored wave-54 out/ files
#   E  (plan-skeptic round 1, process-holes M3; --check only) a limit-killed lane never lands: no STATUS, STATUS: PARTIAL, no
#      TREES: line, a patch whose sha256 differs from the note's, a stale patch the note does not name — each red
#   F  (round 1, M1; --check only) SeamReachabilityTest.kt's three writers (L4 seams 1 → 3, L3 seam 1 → unit-U2, L5 seam 4),
#      each cut on HEAD: at the PINNED anchors the series applies and every revert dry-run passes; at the list END it stops
#   E13-E15 (round 2, process-holes S3) an S1 fix patch `fix-S1-<U>.patch` named on its `patch:` line is accepted (§8 step 8's
#      delivery), the same patch without its line is an error, and a fix patch for a unit the lane does not have is refused
#   G  (round 2, process-holes S2; --check / argument errors only) `--contingency` takes ONE pre-registered unit L:U: none,
#      a non-contingency unit, and L5:U1-ios-LRM before U1-ios has landed are each refused; the ORDER table prints row 30
# LU_SELFTEST_ONLY="E F G" runs only the named sections (E, F and G never commit: --check and the export tree only).
set -uo pipefail
ONLY_SECTIONS=" ${LU_SELFTEST_ONLY:-A B C D E F G} "
want_sec() { [[ "$ONLY_SECTIONS" == *" $1 "* ]]; }
S="$(cd "${1:?usage: land-units.selftest.sh <scratch-dir>}" && pwd -P)"
HERE="$(cd "$(dirname "$0")" && pwd -P)"; LU="$HERE/land-units.sh"
SHARED="$(git -C "$HERE" rev-parse --show-toplevel)"
REALGIT="$(command -v git)"; mkdir -p "$S/gitshim"     # a logging git shim: (cwd, GIT_INDEX_FILE, argv) of every call, then real git
printf '#!/bin/bash\nprintf "%%s\\t%%s\\t%%s\\n" "$(pwd -P)" "${GIT_INDEX_FILE:-}" "$*" >> "${GITSHIM_LOG:?}"\nexec %s "$@"\n' "$REALGIT" > "$S/gitshim/git"; chmod +x "$S/gitshim/git"
R=tools/titan/results
KT=runtimes/compose/src/main/java/com/styleconverter/runtime; KTT=runtimes/compose/src/test/java/com/styleconverter/runtime
SW=runtimes/swiftui/Sources/StyleConverterRuntime; SWT=runtimes/swiftui/Tests/StyleConverterRuntimeTests
CRK=$KT/core/renderer/ComponentRenderer.kt; CRS=$SW/Renderer/ComponentRenderer.swift
SRT=$KTT/core/renderer/SeamReachabilityTest.kt; UEF=$KT/typography/UAElementFontRule.kt; UEFW=$KTT/typography/UAElementFontRuleSeamWiringTest.kt
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "PASS  $*"; }
bad() { FAIL=$((FAIL+1)); echo "FAIL  $*"; }
rc_is() { [[ "$1" == "$2" ]] && ok "$3 (exit $2)" || bad "$3: want exit $1, got $2"; }
has() { grep -qF -- "$2" "$1" && ok "$3" || { bad "$3 — '$2' not in $(basename "$1")"; }; }
hasre() { grep -qE -- "$2" "$1" && ok "$3" || { bad "$3 — /$2/ not in $(basename "$1")"; }; }
snap() { { echo "HEAD $(git -C "$1" rev-parse HEAD)"; echo "index $(shasum -a 256 "$(git -C "$1" rev-parse --git-path index)" | cut -d' ' -f1)"
           git -C "$1" --no-optional-locks status --short --untracked-files=all; } > "$2"; }
mkwt() { local wt="$S/$1"; [[ -e "$wt" ]] && { echo "exists: $wt" >&2; exit 2; }
  git -C "$SHARED" worktree add -q --detach "$wt" HEAD >/dev/null 2>&1 || { echo "worktree add failed: $wt" >&2; exit 2; }; echo "$wt"; }
rmwt() { git -C "$SHARED" worktree remove --force "$1" && echo "      (throwaway $1 removed)"; }
# MODEL: a scratch copy of the files the synthetic patches touch; mkpatch edits the model and appends the diff.
model_init() { MODEL="$S/model.$1"; rm -rf "$MODEL"; mkdir -p "$MODEL"; local f; for f in "${@:3}"; do mkdir -p "$MODEL/$(dirname "$f")"; cp "$2/$f" "$MODEL/$f"; done; }
mkpatch() { # <out.patch> <relpath> <anchor-substring | NEW> <line(s) to insert after the anchor's (last-inserted) line>
  local out="$1" rel="$2" anchor="$3" text="$4" d="$S/mk.tmp"; rm -rf "$d"; mkdir -p "$d/a/$(dirname "$rel")" "$d/b/$(dirname "$rel")"
  if [[ "$anchor" == NEW ]]; then printf '%s\n' "$text" > "$d/b/$rel"; (cd "$d" && git diff --no-index --no-prefix --full-index /dev/null "b/$rel") >> "$out"
  else cp "$MODEL/$rel" "$d/a/$rel"
    python3 -I - "$d/a/$rel" "$d/b/$rel" "$anchor" "$text" <<'PY' || { echo "anchor not found: $anchor" >&2; exit 2; }
import sys
a, b, anc, txt = sys.argv[1:5]; L = open(a).read().split('\n')
i = next(k for k, l in enumerate(L) if anc in l); L[i+1:i+1] = txt.split('\n'); open(b, 'w').write('\n'.join(L))
PY
    (cd "$d" && git diff --no-index --no-prefix --full-index "a/$rel" "b/$rel") >> "$out"; fi
  mkdir -p "$MODEL/$(dirname "$rel")"; cp "$d/b/$rel" "$MODEL/$rel"; rm -rf "$d"; }
gitcls() { awk -F'\t' '{c=$3; sub(/^-C [^ ]+ /,"",c); split(c,w," "); s=w[1]; k=s
  if (s=="apply") { k = ($1 ~ /\/land-units\.[A-Za-z0-9]+\/export\//) ? "apply[export-tree" (c~/ -R /?" -R":"") "]" : \
      (c~/--index/ ? (c~/--check/ ? "apply[--index --check]" : "apply[--index]") : (c~/--numstat/ ? "apply[--numstat]" : (c~/--summary/ ? "apply[--summary]" : (c~/--check/ ? "apply[--check]" : "apply[OTHER]")))) }
  n[k]++} END{for (k in n) printf "%s=%d ", k, n[k]; print ""}' "$1"; }
mkpatch_copy() { # <out.patch> <relpath> <source file>: a creation patch carrying the source's exact bytes
  local d="$S/mk.tmp"; rm -rf "$d"; mkdir -p "$d/b/$(dirname "$2")"; cp "$3" "$d/b/$2"
  (cd "$d" && git diff --no-index --no-prefix --full-index --binary /dev/null "b/$2") >> "$1"; rm -rf "$d"; }
backdate() { find "$1" -exec touch -t 202610110000 {} +; }     # the freshness guard reads mtimes; rehearsal files are minutes old
note() {  # <lane dir> <units>: a COMPLETE note with its TREES: line and one `patch:` line per delivered patch (round 1, M3)
  { printf '# synthetic lane note (land-units rehearsal)\n\nTREES: %s (synthetic)\n\n## %s\n\nSYNTHETIC — never shipped.\n\n' "$1" "$2"
    for p in "$1"/*.patch; do [[ -f "$p" ]] && printf 'patch: %s sha256 %s\n' "${p##*/}" "$(shasum -a 256 "$p" | cut -c1-64)"; done
    printf '\nSTATUS: COMPLETE\n'; } > "$1/_note.md"; }

build_L1() { # <wt>: the lane dir with unit-NB-ios.patch (2 new files) + seam-1.patch (1 line in ComponentRenderer.swift)
  local wt="$1" L="$1/$R/wave55-hyphen-ios-linebox"; mkdir -p "$L"; model_init L1 "$wt" "$CRS"
  mkpatch "$L/unit-NB-ios.patch" "$SW/StyleEngine/typography/line/NormalLineBox.swift" NEW "// SYNTHETIC land-units rehearsal stub (L1 NB-ios) — never shipped
enum NormalLineBox {}"
  mkpatch "$L/unit-NB-ios.patch" "$SWT/NormalLineBoxTests.swift" NEW "// SYNTHETIC land-units rehearsal stub (L1 NB-ios tests) — never shipped"
  mkpatch "$L/seam-1.patch" "$CRS" "|| wptUnbreakableRun || broken.preBroken) && !uprightPlans" "        // SYNTHETIC land-units rehearsal seam (L1 seam 1) — never shipped"
  note "$L" "NB-ios"; }

echo "land-units.selftest.sh — $(date -u '+%Y-%m-%d %H:%M:%S UTC') — shared tree $SHARED at $(git -C "$SHARED" rev-parse --short HEAD) (never touched)"
echo "script under test: $LU (sha256 $(shasum -a 256 "$LU" | cut -c1-16)…)"
SHARED_BEFORE="$S/shared.before"; snap "$SHARED" "$SHARED_BEFORE"

want_sec A && {
# ═══ A ═══════════════════════════════════════════════════════════════════════════════════════════════════════════════
echo; echo "═══ A: the real path, one unit + one seam (L1 NB-ios), in a throwaway worktree"
WA="$(mkwt wt-A)"; build_L1 "$WA"; BASEA="$(git -C "$WA" rev-parse HEAD)"
echo "      synthetic patches:"; for p in "$WA/$R/wave55-hyphen-ios-linebox/"*.patch; do echo "        ${p##*/}: $(grep -c '^diff --git' "$p") file(s), sha256 $(shasum -a 256 "$p" | cut -c1-12)…"; done
snap "$WA" "$S/A.before"; : > "$S/A.gitcalls"
GITSHIM_LOG="$S/A.gitcalls" PATH="$S/gitshim:$PATH" bash "$LU" --check --only L1 --root "$WA" > "$S/A.check.txt" 2>&1; rc=$?
rc_is 0 "$rc" "A1 --check on the throwaway with L1's two patches delivered is clean"
snap "$WA" "$S/A.after"; cmp -s "$S/A.before" "$S/A.after" && ok "A2 --check left HEAD, the index and status byte-identical" || bad "A2 --check changed the throwaway"
awk -F'\t' -v t="$S" '{c=$3; sub(/^-C [^ ]+ /,"",c); split(c,w," "); s=w[1]
  ro = (s=="rev-parse"||s=="symbolic-ref"||s=="log"||s=="cat-file"||s=="ls-files"||s=="hash-object"||(s=="diff-index"&&c~/--cached/)||(s=="apply"&&c~/--(check|numstat|summary)/))
  inexp = ($1 ~ /\/land-units\.[A-Za-z0-9]+\/export\//)
  if (s=="hash-object" && c ~ / -w/) ro=0
  if (!ro && !(s=="apply" && inexp)) {print "MUTATING? " $0; bad=1} else n[s]++} END{for (k in n) printf "%s=%d ", k, n[k]; print ""; exit bad}' "$S/A.gitcalls" > "$S/A.gitcalls.summary"
[[ $? == 0 ]] && ok "A3 every git call of --check was read-only on the tree; the only plain applies ran in the private export dir: $(gitcls "$S/A.gitcalls")" || { bad "A3 a mutating git call in --check"; cat "$S/A.gitcalls.summary"; }
has "$S/A.check.txt" "alone: applies on the current tree" "A4 each patch applies alone on the current tree"
has "$S/A.check.txt" "2 patches apply in order" "A5 the cumulative series applies"
hasre "$S/A.check.txt" "ok +L1:NB-ios +revert \\[ NB-ios \\] reverse-applies" "A6 NB-ios's revert dry-run passes in the export tree"
bash "$LU" --only L1 --root "$WA" > "$S/A.fresh.txt" 2>&1; rc=$?
rc_is 2 "$rc" "A7 the real path REFUSES while lane files are < 120 s old (freshness guard)"; snap "$WA" "$S/A.after2"; cmp -s "$S/A.before" "$S/A.after2" && ok "A8 the refusal touched nothing" || bad "A8 the refusal changed the throwaway"
backdate "$WA/$R/wave55-hyphen-ios-linebox"; : > "$S/A.land.gitcalls"
GITSHIM_LOG="$S/A.land.gitcalls" PATH="$S/gitshim:$PATH" bash "$LU" --only L1 --root "$WA" > "$S/A.land.txt" 2>&1; rc=$?
rc_is 0 "$rc" "A9 the real path lands"
git -C "$WA" log --format='%h %s' "$BASEA..HEAD" > "$S/A.log"; echo "      commits (newest first):"; sed 's/^/        /' "$S/A.log"
[[ $(wc -l < "$S/A.log") -eq 2 ]] && sed -n 2p "$S/A.log" | grep -q " wave55 lane records: " && sed -n 1p "$S/A.log" | grep -q " wave55 L1 NB-ios: " \
  && ok "A10 exactly two commits, in order: the records, then L1 NB-ios" || bad "A10 commit order"
git -C "$WA" show --name-only --format= HEAD~1 | sort > "$S/A.c1"; git -C "$WA" show --name-only --format= HEAD | sort > "$S/A.c2"
(cd "$WA" && find "$R/wave55-hyphen-ios-linebox" -type f | sort) > "$S/A.c1.want"; printf '%s\n' "$CRS" "$SW/StyleEngine/typography/line/NormalLineBox.swift" "$SWT/NormalLineBoxTests.swift" | sort > "$S/A.c2.want"
cmp -s "$S/A.c1" "$S/A.c1.want" && ok "A11 the records commit holds exactly the lane dir ($(wc -l < "$S/A.c1" | tr -d ' ') files)" || { bad "A11 records commit paths"; diff "$S/A.c1.want" "$S/A.c1"; }
cmp -s "$S/A.c2" "$S/A.c2.want" && ok "A12 the NB-ios commit holds exactly its 3 code paths (2 created + the seam file)" || { bad "A12 unit commit paths"; diff "$S/A.c2.want" "$S/A.c2"; }
same=1; for f in "$CRS" "$SW/StyleEngine/typography/line/NormalLineBox.swift" "$SWT/NormalLineBoxTests.swift"; do cmp -s "$WA/$f" "$MODEL/$f" || same=0; git -C "$WA" show "HEAD:$f" | cmp -s - "$MODEL/$f" || same=0; done
(( same )) && ok "A13 the committed and on-disk bytes equal the model's (the patches' intended result)" || bad "A13 landed bytes differ from the model"
[[ -z "$(git -C "$WA" --no-optional-locks status --short --untracked-files=all)" ]] && ok "A14 the throwaway is clean after landing (git status --short empty)" || { bad "A14 not clean"; git -C "$WA" status --short; }
git -C "$WA" show --format=%B -s HEAD | grep -q "^Co-Authored-By: Claude Opus 5.5" && git -C "$WA" show --format=%B -s HEAD | grep -q "unit-NB-ios.patch sha256" \
  && ok "A15 the unit commit body names its patches' sha256 and ends with the trailer" || bad "A15 commit body"
echo "      the real run's git calls: $(gitcls "$S/A.land.gitcalls")"
echo "      the NB-ios commit message, verbatim:"; git -C "$WA" show -s --format=%B HEAD | sed 's/^/        | /'
# the contingency: NB-ios-b's two patches arrive after the stage-1 probe, cut on the landed tree; --contingency lands records + 1
LA="$WA/$R/wave55-hyphen-ios-linebox"; HEADA="$(git -C "$WA" rev-parse HEAD)"
bash "$LU" --contingency L1:NB-ios-b --check --root "$WA" > "$S/A16.txt" 2>&1; rc=$?; rc_is 1 "$rc" "A16 --contingency --check before NB-ios-b is delivered names its two patches MISSING"
has "$S/A16.txt" "MISSING tools/titan/results/wave55-hyphen-ios-linebox/seam-1b.patch" "A17 …seam-1b.patch among them"
mkpatch "$LA/unit-NB-ios-b.patch" "$SW/StyleEngine/typography/line/NormalLineBox.swift" "enum NormalLineBox {}" "// SYNTHETIC verticalPads (L1 NB-ios-b)"
mkpatch "$LA/seam-1b.patch" "$CRS" "        // SYNTHETIC land-units rehearsal seam (L1 seam 1) — never shipped" "        // SYNTHETIC seam 1b (L1 NB-ios-b)"
note "$LA" "NB-ios NB-ios-b"; backdate "$LA"
bash "$LU" --contingency L1:NB-ios-b --check --root "$WA" > "$S/A18.txt" 2>&1; rc=$?; rc_is 0 "$rc" "A18 --contingency --check with both patches is clean (needs NB-ios: found in HEAD's history)"
bash "$LU" --contingency L1:NB-ios-b --root "$WA" > "$S/A19.txt" 2>&1; rc=$?; rc_is 0 "$rc" "A19 --contingency lands"
git -C "$WA" log --reverse --format=%s "$HEADA..HEAD" | cut -d: -f1 | tr '\n' '|' > "$S/A19.order"
[[ "$(cat "$S/A19.order")" == "wave55 lane records|wave55 L1 NB-ios-b|" ]] && ok "A20 …as the new lane records (the two patches) then NB-ios-b; tree clean: $([[ -z "$(git -C "$WA" --no-optional-locks status --short --untracked-files=all)" ]] && echo yes || echo NO)" || bad "A20 contingency order: $(cat "$S/A19.order")"
rmwt "$WA"
}

want_sec B && {
# ═══ B ═══════════════════════════════════════════════════════════════════════════════════════════════════════════════
echo; echo "═══ B: failure paths (each guard turned red, then restored), throwaway worktrees"
WB="$(mkwt wt-B)"; build_L1 "$WB"; backdate "$WB/$R/wave55-hyphen-ios-linebox"; BASEB="$(git -C "$WB" rev-parse HEAD)"
LAND_FAULT_AT=L1:NB-ios bash "$LU" --only L1 --root "$WB" > "$S/B1.txt" 2>&1; rc=$?
rc_is 3 "$rc" "B1 a fault after NB-ios's patches are staged stops the landing"
[[ $(git -C "$WB" rev-list --count "$BASEB..HEAD") == 1 ]] && git -C "$WB" log -1 --format=%s | grep -q "^wave55 lane records" && [[ -z "$(git -C "$WB" --no-optional-locks status --short --untracked-files=all)" ]] \
  && ok "B2 …and reverse-applies the unit: HEAD = the records commit, nothing staged, tree clean" || { bad "B2 restore"; git -C "$WB" status --short; }
has "$S/B1.txt" "restored: the unit's patches reverse-applied" "B3 the restore is reported"
bash "$LU" --only L1 --root "$WB" > "$S/B4.txt" 2>&1; rc=$?
rc_is 3 "$rc" "B4 re-running without --from refuses an EMPTY records commit"
[[ $(git -C "$WB" rev-list --count "$BASEB..HEAD") == 1 && -z "$(git -C "$WB" --no-optional-locks status --short)" ]] && ok "B5 …and changed nothing" || bad "B5 the empty-records refusal changed the tree"
bash "$LU" --only L1 --from L1:NB-ios --root "$WB" > "$S/B6.txt" 2>&1; rc=$?
rc_is 0 "$rc" "B6 --from L1:NB-ios resumes"
[[ $(git -C "$WB" rev-list --count "$BASEB..HEAD") == 2 ]] && git -C "$WB" log -1 --format=%s | grep -q "^wave55 L1 NB-ios: " && [[ -z "$(git -C "$WB" --no-optional-locks status --short --untracked-files=all)" ]] \
  && ok "B7 …landing NB-ios on top of the records; tree clean" || bad "B7 resume"
rmwt "$WB"

WB="$(mkwt wt-B2)"; build_L1 "$WB"; backdate "$WB/$R/wave55-hyphen-ios-linebox"; L="$WB/$R/wave55-hyphen-ios-linebox"
cp "$WB/$CRS" "$S/CRS.orig"; printf '// a lane edited the shared tree\n' >> "$WB/$CRS"; snap "$WB" "$S/B8.before"
bash "$LU" --check --only L1 --root "$WB" > "$S/B8.check.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B8 --check flags a dirty touched path"
has "$S/B8.check.txt" "is MODIFIED in the working tree" "B9 …naming it"
bash "$LU" --only L1 --root "$WB" > "$S/B8.land.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B10 the real path lands nothing on a dirty path"
snap "$WB" "$S/B8.after"; cmp -s "$S/B8.before" "$S/B8.after" && ok "B11 …and touched nothing" || bad "B11 touched"
cp "$S/CRS.orig" "$WB/$CRS"                                            # restore by copy (no git command)
cp "$L/seam-1.patch" "$S/seam-1.good"; sed 's/^ \(        let pinnedBoxHeight = LineBoxMetrics.pinnedBoxHeight(\)$/ \1 \/\/ drifted/' "$S/seam-1.good" > "$L/seam-1.patch"; backdate "$L"
bash "$LU" --check --only L1 --root "$WB" > "$S/B12.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B12 --check flags a seam patch cut on the wrong context"
has "$S/B12.txt" "does NOT apply on the current tree and stacks on nothing" "B13 …alone"; has "$S/B12.txt" "the series STOPS at wave55-hyphen-ios-linebox/seam-1.patch" "B14 …and cumulatively"
cp "$S/seam-1.good" "$L/seam-1.patch"
cat "$S/seam-1.good" >> "$L/unit-NB-ios.patch"; : > "$L/seam-1.patch"; mkpatch "$L/seam-1.patch" "$SWT/NormalLineBoxSeamWiringTests.swift" NEW "// SYNTHETIC"; backdate "$L"
bash "$LU" --check --only L1 --root "$WB" > "$S/B15.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B15 --check flags a unit patch that carries the seam hunk"
has "$S/B15.txt" "(a unit patch) edits the seam file" "B16 …(unit patch edits a seam file)"; has "$S/B15.txt" "does not touch its registered seam file" "B17 …(the seam patch misses its seam file)"
rm -rf "$L"; build_L1 "$WB"; backdate "$WB/$R/wave55-hyphen-ios-linebox"
C="$S/lu-copy"; rm -rf "$C"; mkdir -p "$C"; cp "$LU" "$HERE/landing-manifest.json" "$HERE/expectations.json" "$C/"
bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B18.txt" 2>&1; rc=$?; rc_is 0 "$rc" "B18 control: an unedited copy of the script beside the manifest is in sync"
sed -i '' 's/^26|L1|hyphen-ios-linebox|NB-ios|\(.*\)Blink'"'"'s rounded line box/26|L1|hyphen-ios-linebox|NB-ios|\1a hand-edited line box/' "$C/land-units.sh"
bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B19.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B19 a table edit without --emit-manifest is DRIFT"; has "$S/B19.txt" "manifest DRIFT" "B20 …reported as drift"
cp "$LU" "$C/land-units.sh"; python3 -I - "$C/land-units.sh" <<'PY'
import sys, re
p = sys.argv[1]; s = open(p).read(); a = s.index("table_text() { cat <<'EOF'\n") + len("table_text() { cat <<'EOF'\n"); b = s.index("\nEOF\n", a)
rows = s[a:b].split('\n'); l6 = [r for r in rows if r.split('|')[1] == 'L6']; rest = [r for r in rows if r.split('|')[1] != 'L6']
k = next(i for i, r in enumerate(rest) if r.startswith('18|L2|')); rest[k+1:k+1] = l6
rows = ['%02d|%s' % (i + 1, r.split('|', 1)[1]) for i, r in enumerate(rest)]; open(p, 'w').write(s[:a] + '\n'.join(rows) + s[b:])
PY
bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B21.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B21 moving L6 after L2 breaks an asserted order (C13)"
has "$S/B21.txt" "VIOLATED: L6:U2 must land before L2:U1" "B22 …named, before anything is read"
cp "$LU" "$C/land-units.sh"; python3 -I - "$C/land-units.sh" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read(); a = 'echo\nif (( CHECK )); then\n  echo "SUMMARY'
assert a in s; open(p, 'w').write(s.replace(a, 'echo\ntree_mut git status --short\nif (( CHECK )); then\n  echo "SUMMARY', 1))
PY
snap "$WB" "$S/B23.before"; bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B23.txt" 2>&1; rc=$?
rc_is 70 "$rc" "B23 a mutating call planted in the --check path trips the guard"; has "$S/B23.txt" "INTERNAL: mutating call in --check mode" "B24 …named"
snap "$WB" "$S/B23.after"; cmp -s "$S/B23.before" "$S/B23.after" && ok "B25 …before it ran (tree unchanged)" || bad "B25 the planted call ran"
mkdir -p "$S/busy"; printf '#!/bin/bash\nsleep 30\n' > "$S/busy/ab-arms59.sh"; chmod +x "$S/busy/ab-arms59.sh"
snap "$WB" "$S/B26.before"; (cd "$WB" && exec "$S/busy/ab-arms59.sh") & BP=$!; sleep 1
bash "$LU" --only L1 --root "$WB" > "$S/B26.txt" 2>&1; rc=$?; kill "$BP" 2>/dev/null; wait "$BP" 2>/dev/null
rc_is 2 "$rc" "B26 a gate / A/B-arm process whose cwd is in the tree makes the landing refuse"; has "$S/B26.txt" "busy    a gate / probe / A/B arm runs in this tree" "B27 …named"
snap "$WB" "$S/B26.after"; cmp -s "$S/B26.before" "$S/B26.after" && ok "B28 …and nothing landed (HEAD, index and status unchanged)" || bad "B28 landed while busy"
# the plan-of-record agreement can fail: a copy of expectations.json, mutated the way plan-build.py would be by a decision
cp "$LU" "$C/land-units.sh"; cp "$HERE/expectations.json" "$C/expectations.json"
node -e 'const f=process.argv[1], e=require(f); e.lanes["L6-android-paint-order"].revertUnits.U2.revertOrder=["U2","U1"]; require("fs").writeFileSync(f, JSON.stringify(e,null,1))' "$C/expectations.json"
bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B32.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B32 a plan whose L6 U2 revertOrder differs from the table fails the plan agreement"
has "$S/B32.txt" "plan: L6 U2: revertOrder plan [U2,U1] vs table [U2]" "B33 …named"
cp "$HERE/expectations.json" "$C/expectations.json"
node -e 'const f=process.argv[1], e=require(f), l=e.lanes["L4-ua-heading-margin"]; delete l.revertUnits["U2-android"]; l.revertUnits["U1-android"].revertOrder=["U1-android"]; l.heldUnits={"U2-android":"HELD (GO-SMALL) - rehearsal mutation"}; require("fs").writeFileSync(f, JSON.stringify(e,null,1))' "$C/expectations.json"
bash "$C/land-units.sh" --check --only L1 --root "$WB" > "$S/B34.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B34 a GO-SMALL plan (L4 U2-android held) without --hold fails"
has "$S/B34.txt" "the plan HOLDS L4 U2-android" "B35 …asking for --hold L4:U2-android"
bash "$C/land-units.sh" --check --only L1 --hold L4:U2-android --root "$WB" > "$S/B36.txt" 2>&1; rc=$?; rc_is 0 "$rc" "B36 …and the same plan WITH --hold L4:U2-android agrees (U1-android's revertOrder read without the held unit)"
cp "$HERE/expectations.json" "$C/expectations.json"
bash "$LU" --check --hold L2:U2 --root "$WB" > "$S/B29.txt" 2>&1; rc=$?; rc_is 2 "$rc" "B29 holding L2 U2 without U1 is refused (group cbb: U1 never without U2)"
has "$S/B29.txt" "group cbb: L2:U1 is selected but L2:U2 is not" "B30 …named"
bash "$LU" --check --hold L4:U1-android --root "$WB" > "$S/B31.txt" 2>&1; rc=$?; rc_is 2 "$rc" "B31 holding L4 U1-android but not U2-android is refused (needs)"
# the delivery rules: cross-lane overlap, a patch writing into a lane records dir, a unit and its own seam on one path
L1d="$WB/$R/wave55-hyphen-ios-linebox"; L5d="$WB/$R/wave55-counter-suffix-rows"; LMR="$SW/StyleEngine/lists/ListMarkerRow.swift"; LBM="$SW/StyleEngine/typography/LineBoxMetrics.swift"
HOLD5="L5:U2o-ios,L5:U2i-ios,L5:U2o-android,L5:U2i-android,L5:U3-ios,L5:D"
rm -rf "$L1d" "$L5d"; mkdir -p "$L1d" "$L5d"; model_init X "$WB" "$CRS" "$LMR"
mkpatch "$L5d/unit-U1-ios.patch" "$SW/StyleEngine/lists/ListMarkerBidi.swift" NEW "// SYNTHETIC (L5 U1-ios)"
mkpatch "$L5d/unit-U1-ios.patch" "$LMR" "//  ListMarkerRow.swift" "//  SYNTHETIC doc line (L5 U1-ios)"
mkpatch "$L5d/seam-1.patch" "$CRS" "Text(ScriptFallbackFonts.annotate(markerText," "                    // SYNTHETIC seam 1 (L5 U1-ios)"
mkpatch "$L1d/unit-NB-ios.patch" "$SW/StyleEngine/typography/line/NormalLineBox.swift" NEW "// SYNTHETIC (L1 NB-ios)"
mkpatch "$L1d/unit-NB-ios.patch" "$LMR" "//  SYNTHETIC doc line (L5 U1-ios)" "//  SYNTHETIC doc line (L1 NB-ios) — an L5 file"
mkpatch "$L1d/seam-1.patch" "$CRS" "|| wptUnbreakableRun || broken.preBroken) && !uprightPlans" "        // SYNTHETIC seam 1 (L1)"
note "$L1d" NB-ios; note "$L5d" U1-ios; backdate "$L1d"; backdate "$L5d"
bash "$LU" --check --only L1,L5 --hold "$HOLD5" --root "$WB" > "$S/B37.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B37 two lanes touching one non-shared path is an error"
has "$S/B37.txt" "two lanes touch $LMR: L5 L1" "B38 …named (the series itself applies: $(grep -c 'patches apply in order' "$S/B37.txt") cumulative OK line)"
rm -rf "$L1d" "$L5d"; build_L1 "$WB"; mkpatch "$L1d/unit-NB-ios.patch" "$R/wave55-hyphen-ios-linebox/extra.txt" NEW "SYNTHETIC"; backdate "$L1d"
bash "$LU" --check --only L1 --root "$WB" > "$S/B39.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B39 a unit patch writing into its lane records dir is an error"
has "$S/B39.txt" "writes into the lane records dir: $R/wave55-hyphen-ios-linebox/extra.txt" "B40 …named"
rm -rf "$L1d"; mkdir -p "$L1d"; model_init Y "$WB" "$CRS" "$LBM"
mkpatch "$L1d/unit-NB-ios.patch" "$SW/StyleEngine/typography/line/NormalLineBox.swift" NEW "// SYNTHETIC (L1 NB-ios)"
mkpatch "$L1d/unit-NB-ios.patch" "$LBM" "//  LineBoxMetrics.swift" "//  SYNTHETIC doc line (L1 unit)"
mkpatch "$L1d/seam-1.patch" "$CRS" "|| wptUnbreakableRun || broken.preBroken) && !uprightPlans" "        // SYNTHETIC seam 1 (L1)"
mkpatch "$L1d/seam-1.patch" "$LBM" "//  SYNTHETIC doc line (L1 unit)" "//  SYNTHETIC doc line (L1 seam) — same path as the unit"
note "$L1d" NB-ios; backdate "$L1d"
bash "$LU" --check --only L1 --root "$WB" > "$S/B41.txt" 2>&1; rc=$?; rc_is 1 "$rc" "B41 a unit patch and its own seam patch on one path is an error"
has "$S/B41.txt" "L1:NB-ios: its unit patch and its seam patch both touch $LBM" "B42 …named"
rmwt "$WB"
}

want_sec C && {
# ═══ C ═══════════════════════════════════════════════════════════════════════════════════════════════════════════════
echo; echo "═══ C: a stacked series (L4 four units + the L2 pair) and the C12 revert dry-runs"
WC="$(mkwt wt-C)"; BASEC="$(git -C "$WC" rev-parse HEAD)"
L4="$WC/$R/wave55-ua-heading-margin"; L2="$WC/$R/wave55-cbb-android"; mkdir -p "$L4" "$L2"
model_init C "$WC" "$CRK" "$CRS" "$SRT" "$UEF" "$UEFW"
# L4 U1-android: unit (new file + a doc line in UAElementFontRule.kt) · seam 1 (kt :1164) + patch-borne test + reachability line
mkpatch "$L4/unit-U1-android.patch" "$KT/spacing/UaPositionedHeadingMargin.kt" NEW "// SYNTHETIC (L4 U1-android)"
mkpatch "$L4/unit-U1-android.patch" "$UEF" 'queued as "STILL DEFERRED" since the retro.' "// SYNTHETIC doc line (L4 U1-android)"
mkpatch "$L4/seam-1.patch" "$CRK" "// post-merge drop-everything it replaced is gone." "        // SYNTHETIC seam 1 (L4 U1-android)"
mkpatch "$L4/seam-1.patch" "$KTT/spacing/UaPositionedHeadingMarginSeamWiringTest.kt" NEW "// SYNTHETIC patch-borne (L4 seam 1)"
mkpatch "$L4/seam-1.patch" "$SRT" '"content/PseudoGeneratedBox.kt",' '            "spacing/UaPositionedHeadingMargin.kt", // SYNTHETIC'
mkpatch "$L4/seam-1.patch" "$UEFW" "package " "// SYNTHETIC handoff token (L4 seam 1)"
# L4 U1-ios: unit (new file) · seam 2 (swift :342)
mkpatch "$L4/unit-U1-ios.patch" "$SW/StyleEngine/spacing/UAPositionedHeadingMargin.swift" NEW "// SYNTHETIC (L4 U1-ios)"
mkpatch "$L4/seam-2.patch" "$CRS" "        let ownProperties = reset.own" "        // SYNTHETIC seam 2 (L4 U1-ios)"
# L4 U2-android: stacked on U1-android in UAElementFontRule.kt, seam 3 on seam 1's lines, reachability line on L4's
mkpatch "$L4/unit-U2-android.patch" "$KT/typography/UAHeadingFoldGate.kt" NEW "// SYNTHETIC (L4 U2-android)"
mkpatch "$L4/unit-U2-android.patch" "$UEF" "// SYNTHETIC doc line (L4 U1-android)" "// SYNTHETIC doc line (L4 U2-android)"
mkpatch "$L4/seam-3.patch" "$CRK" "        // SYNTHETIC seam 1 (L4 U1-android)" "        // SYNTHETIC seam 3 (L4 U2-android)"
mkpatch "$L4/seam-3.patch" "$SRT" '"spacing/UaPositionedHeadingMargin.kt", // SYNTHETIC' '            "typography/UAHeadingFoldGate.kt", // SYNTHETIC'
# L4 U2-ios: seam 4 on seam 2's line
mkpatch "$L4/unit-U2-ios.patch" "$SW/StyleEngine/typography/UAHeadingFoldGate.swift" NEW "// SYNTHETIC (L4 U2-ios)"
mkpatch "$L4/seam-4.patch" "$CRS" "        // SYNTHETIC seam 2 (L4 U1-ios)" "        // SYNTHETIC seam 4 (L4 U2-ios)"
# L2 U1 (+ seam 1, kt :1950) and U2
mkpatch "$L2/unit-U1.patch" "$KT/core/variables/ContainingBlockBands.kt" NEW "// SYNTHETIC (L2 U1)"
mkpatch "$L2/seam-1.patch" "$CRK" "                .childContainingBlock(effectiveProperties, containingBlock)" "            // SYNTHETIC seam 1 (L2 U1)"
mkpatch "$L2/unit-U2.patch" "$KT/sizing/PercentContentBoxBand.kt" NEW "// SYNTHETIC (L2 U2)"
note "$L4" "U1-android U1-ios U2-android U2-ios"; note "$L2" "U1 U2"; backdate "$L4"; backdate "$L2"
snap "$WC" "$S/C.before"
bash "$LU" --check --only L2,L4 --root "$WC" > "$S/C.check.txt" 2>&1; rc=$?; rc_is 0 "$rc" "C1 --check on the stacked 6-unit series is clean"
snap "$WC" "$S/C.after"; cmp -s "$S/C.before" "$S/C.after" && ok "C2 …and touched nothing" || bad "C2 touched"
[[ $(grep -c "STACKED (expected" "$S/C.check.txt") -ge 3 ]] && ok "C3 the stacked patches fail ALONE by construction ($(grep -c "STACKED (expected" "$S/C.check.txt") of them), reported as STACKED, not as errors" || bad "C3 stacked reporting"
has "$S/C.check.txt" "11 patches apply in order" "C4 …and the cumulative series applies"
for k in "L4:U1-android|U2-android U1-android" "L4:U1-ios|U2-ios U1-ios" "L4:U2-android|U2-android" "L4:U2-ios|U2-ios" "L2:U1|U2 U1" "L2:U2|U2 U1"; do
  hasre "$S/C.check.txt" "ok +${k%%|*} +revert \\[ ${k#*|} \\] reverse-applies" "C5 revert dry-run OK on the landed series: ${k%%|*} [${k#*|}]"; done
has "$S/C.check.txt" "shared  $CRK: L4 L2 (registered shared file)" "C6 ComponentRenderer.kt, touched by L4 and L2, is reported as the registered shared seam file"
bash "$LU" --only L2,L4 --root "$WC" > "$S/C.land.txt" 2>&1; rc=$?; rc_is 0 "$rc" "C7 the real path lands the stacked series"
git -C "$WC" log --reverse --format='%s' "$BASEC..HEAD" | cut -d: -f1 > "$S/C.order"; printf '%s\n' "wave55 lane records" "wave55 L4 U1-android" "wave55 L4 U1-ios" "wave55 L4 U2-android" "wave55 L4 U2-ios" "wave55 L2 U1" "wave55 L2 U2" > "$S/C.order.want"
cmp -s "$S/C.order" "$S/C.order.want" && ok "C8 seven commits in the table's order: records, L4 U1-android, U1-ios, U2-android, U2-ios, L2 U1, U2" || { bad "C8 order"; cat "$S/C.order"; }
same=1; for f in "$CRK" "$CRS" "$SRT" "$UEF" "$UEFW"; do git -C "$WC" show "HEAD:$f" | cmp -s - "$MODEL/$f" || { same=0; echo "      differs: $f"; }; done
(( same )) && ok "C9 the five stacked shared files land byte-equal to the model" || bad "C9 stacked bytes"
[[ -z "$(git -C "$WC" --no-optional-locks status --short --untracked-files=all)" ]] && ok "C10 the throwaway is clean after landing" || bad "C10 not clean"
for c in $(git -C "$WC" rev-list --reverse "$BASEC..HEAD"); do echo "        $(git -C "$WC" show -s --format='%h %s' "$c" | cut -c1-110)…  [$(git -C "$WC" show --name-only --format= "$c" | wc -l | tr -d ' ') paths]"; done
rmwt "$WC"

WC="$(mkwt wt-C2)"; L4="$WC/$R/wave55-ua-heading-margin"; L2="$WC/$R/wave55-cbb-android"; mkdir -p "$L4" "$L2"
model_init C2 "$WC" "$CRK" "$CRS" "$SRT" "$UEF" "$UEFW"
mkpatch "$L4/unit-U1-android.patch" "$KT/spacing/UaPositionedHeadingMargin.kt" NEW "// SYNTHETIC (L4 U1-android)"
mkpatch "$L4/seam-1.patch" "$CRK" "// post-merge drop-everything it replaced is gone." "        // SYNTHETIC seam 1 (L4 U1-android)"
mkpatch "$L4/unit-U1-ios.patch" "$SW/StyleEngine/spacing/UAPositionedHeadingMargin.swift" NEW "// SYNTHETIC (L4 U1-ios)"
mkpatch "$L4/seam-2.patch" "$CRS" "        let ownProperties = reset.own" "        // SYNTHETIC seam 2 (L4 U1-ios)"
mkpatch "$L4/unit-U2-android.patch" "$KT/typography/UAHeadingFoldGate.kt" NEW "// SYNTHETIC (L4 U2-android)"
mkpatch "$L4/seam-3.patch" "$CRK" "        // SYNTHETIC seam 1 (L4 U1-android)" "        // SYNTHETIC seam 3 (L4 U2-android)"
mkpatch "$L4/unit-U2-ios.patch" "$SW/StyleEngine/typography/UAHeadingFoldGate.swift" NEW "// SYNTHETIC (L4 U2-ios)"
mkpatch "$L4/seam-4.patch" "$CRS" "        // SYNTHETIC seam 2 (L4 U1-ios)" "        // SYNTHETIC seam 4 (L4 U2-ios)"
mkpatch "$L2/unit-U1.patch" "$KT/core/variables/ContainingBlockBands.kt" NEW "// SYNTHETIC (L2 U1)"
# the FAKE: L2's seam lands INSIDE L4's seam-1 / seam-3 context (one line below them) — forward it applies; L4 no longer reverts
mkpatch "$L2/seam-1.patch" "$CRK" "        // SYNTHETIC seam 3 (L4 U2-android)" "        // SYNTHETIC FAKE seam 1 (L2 U1) inside L4's context"
mkpatch "$L2/unit-U2.patch" "$KT/sizing/PercentContentBoxBand.kt" NEW "// SYNTHETIC (L2 U2)"
note "$L4" "fake"; note "$L2" "fake"; backdate "$L4"; backdate "$L2"
bash "$LU" --check --only L2,L4 --root "$WC" > "$S/C11.txt" 2>&1; rc=$?; rc_is 1 "$rc" "C11 a later lane's hunk inside an earlier unit's context: --check goes red"
has "$S/C11.txt" "patches apply in order" "C12 …although the forward series applies"
grep -E "L4:U1-android revert \[ U2-android U1-android \] FAILS|L4:U2-android revert \[ U2-android \] FAILS" "$S/C11.txt" >/dev/null && ok "C13 …because the L4 revert dry-run FAILS on the landed series (the C12 instrument can fail)" || bad "C13 revert dry-run did not fail"
bash "$LU" --only L2,L4 --root "$WC" > "$S/C14.txt" 2>&1; rc=$?; rc_is 1 "$rc" "C14 the real path lands nothing when a revert dry-run fails"
[[ "$(git -C "$WC" rev-parse HEAD)" == "$(git -C "$SHARED" rev-parse HEAD)" ]] && ok "C15 …HEAD unchanged" || bad "C15 landed"
rmwt "$WC"
}

want_sec D && {
# ═══ D ═══════════════════════════════════════════════════════════════════════════════════════════════════════════════
echo; echo "═══ D: L7 — optional units (U1 / U3 written at planning) and the records commit's six force-added wave-54 files"
WD="$(mkwt wt-D)"; BASED="$(git -C "$WD" rev-parse HEAD)"; L7d="$WD/$R/wave55-instruments"; mkdir -p "$L7d"
HOLD7="L7:U4,L7:U6-compose,L7:U6-swift,L7:U6-web,L7:U6-tooling,L7:U5"; AB="$R/wave55-gate/ab-arms55"
while IFS= read -r f; do mkdir -p "$WD/$(dirname "$f")"; cp "$SHARED/$f" "$WD/$f"; done < <(sed -n '/^FORCE_ADD="/,/"$/p' "$LU" | sed 's/^FORCE_ADD="//; s/"$//')
model_init D "$WD"
mkpatch "$L7d/unit-U2.patch" "tools/titan/wire-differential.sh" NEW "# SYNTHETIC (L7 U2) — never shipped"
note "$L7d" "U2 U3"; backdate "$L7d"
bash "$LU" --check --only L7 --hold "$HOLD7" --root "$WD" > "$S/D1.txt" 2>&1; rc=$?; rc_is 1 "$rc" "D1 L7 U3 with no patch while its planning files are untracked: --check goes red"
has "$S/D1.txt" "L7:U3 delivered no patch and $AB.sh is NOT tracked at HEAD" "D2 …naming each untracked file"
has "$S/D1.txt" "L7:U1 (optional, no patch): tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py tracked at HEAD" "D3 L7 U1 with no patch passes: its copy is tracked since a2a37a8e"
for x in .sh .selftest.sh .selftest.out.txt; do mkpatch_copy "$L7d/unit-U3.patch" "$AB$x" "$SHARED/$AB$x"; done; note "$L7d" "U2 U3"; backdate "$L7d"
cp "$SHARED/$AB.sh" "$WD/$AB.sh"     # a loose copy beside the delivered patch
bash "$LU" --check --only L7 --hold "$HOLD7" --root "$WD" > "$S/D4.txt" 2>&1; rc=$?; rc_is 1 "$rc" "D4 a delivered unit-U3.patch with a loose untracked copy on disk: red"
has "$S/D4.txt" "$AB.sh exists on disk, but its first patch CREATES it" "D5 …naming the loose copy"
rm -f "$WD/$AB.sh"
bash "$LU" --check --only L7 --hold "$HOLD7" --root "$WD" > "$S/D6.txt" 2>&1; rc=$?; rc_is 0 "$rc" "D6 U3 delivered as a patch (no loose copy): clean"
bash "$LU" --only L7 --hold "$HOLD7" --root "$WD" > "$S/D7.txt" 2>&1; rc=$?; rc_is 0 "$rc" "D7 the real path lands L7 (records, U2, U3)"
git -C "$WD" log --reverse --format=%s "$BASED..HEAD" | cut -d: -f1 | tr '\n' '|' > "$S/D7.order"
[[ "$(cat "$S/D7.order")" == "wave55 lane records|wave55 L7 U2|wave55 L7 U3|" ]] && ok "D8 three commits in order: records, L7 U2, L7 U3" || bad "D8 order: $(cat "$S/D7.order")"
fa=0; while IFS= read -r f; do git -C "$WD" cat-file -e "HEAD:$f" 2>/dev/null && fa=$((fa+1)); done < <(sed -n '/^FORCE_ADD="/,/"$/p' "$LU" | sed 's/^FORCE_ADD="//; s/"$//')
[[ "$fa" == 6 ]] && git -C "$WD" show --name-only --format= HEAD~2 | grep -q "wave54-oof-layout/fix/dry/h1h2/out/expectations.json" && ok "D9 the records commit force-added the six gitignored wave-54 out/ files (instruments.md:300)" || bad "D9 force-add: $fa of 6 tracked"
same=1; for x in .sh .selftest.sh .selftest.out.txt; do git -C "$WD" show "HEAD:$AB$x" | cmp -s - "$SHARED/$AB$x" || same=0; done
(( same )) && ok "D10 the U3 commit carries the runner byte-equal to the toolkit stage's files" || bad "D10 U3 bytes"
[[ -z "$(git -C "$WD" --no-optional-locks status --short --untracked-files=all)" ]] && ok "D11 the throwaway is clean after landing" || { bad "D11 not clean"; git -C "$WD" status --short | head; }
rmwt "$WD"
}

want_sec E && {
# ═══ E ═══  (round 1, process-holes M3: the note guards; --check only)
echo; echo "═══ E: a limit-killed lane never lands (note guards; --check only, read-only)"
WE="$(mkwt wt-E)"; model_init E "$WE"; LE="$WE/$R/wave55-android-paint-order"; mkdir -p "$LE"; K6=runtimes/compose/src/main/java/com/styleconverter/runtime/borders
for u in U1 U2 U3; do mkpatch "$LE/unit-$u.patch" "$K6/SyntheticLU$u.kt" NEW "// SYNTHETIC (land-units E, L6 $u) — never shipped"; done
note "$LE" "U1 U2 U3"; backdate "$LE"; snap "$WE" "$S/E.before"
bash "$LU" --check --only L6 --root "$WE" > "$S/E0.txt" 2>&1; rc_is 0 "$?" "E0 control: a COMPLETE note with TREES: and the three patch lines is clean"
cp "$LE/_note.md" "$S/E.note.good"
grep -v '^STATUS:' "$S/E.note.good" > "$LE/_note.md"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E1.txt" 2>&1; rc_is 1 "$?" "E1 a note with NO STATUS line (a killed attempt) is an error"; has "$S/E1.txt" "no STATUS line — a lane lands only on STATUS: COMPLETE" "E2 …named"
sed 's/^STATUS: COMPLETE$/STATUS: PARTIAL — U2 mutation not restored/' "$S/E.note.good" > "$LE/_note.md"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E3.txt" 2>&1; rc_is 1 "$?" "E3 STATUS: PARTIAL is an error (was a warning)"; has "$S/E3.txt" "STATUS: PARTIAL — U2 mutation not restored — a lane lands only on STATUS: COMPLETE" "E4 …named"
grep -v '^TREES:' "$S/E.note.good" > "$LE/_note.md"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E5.txt" 2>&1; rc_is 1 "$?" "E5 a note with no TREES: line is an error"; has "$S/E5.txt" "has no TREES: line" "E6 …named"
cp "$S/E.note.good" "$LE/_note.md"; mkpatch "$LE/unit-U1.patch" "$K6/SyntheticLU1b.kt" NEW "// SYNTHETIC second path, re-cut AFTER the note"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E7.txt" 2>&1; rc_is 1 "$?" "E7 a patch re-cut after the note (its sha256 differs) is an error"; has "$S/E7.txt" "differs from the note's" "E8 …named"
rm -f "$LE/unit-U1.patch"; mkpatch "$LE/unit-U1.patch" "$K6/SyntheticLUU1.kt" NEW "// SYNTHETIC (land-units E, L6 U1) — never shipped"   # the original bytes again
cp "$S/E.note.good" "$LE/_note.md"; printf 'diff --git a/x b/x\n' > "$LE/unit-UX.patch"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E9.txt" 2>&1; rc_is 1 "$?" "E9 a stale patch the ORDER table does not name (a dead attempt) is an error"; has "$S/E9.txt" "is not a patch the ORDER table names" "E10 …named"
rm -f "$LE/unit-UX.patch"; backdate "$LE"
# round 2 (S3): the S1 fix patches of §8 step 8
mkpatch "$LE/fix-S1-U2.patch" "$K6/SyntheticLUfixU2.kt" NEW "// SYNTHETIC S1 fix of L6 U2 (land-units E13) — never shipped"; note "$LE" "U1 U2 U3"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E13.txt" 2>&1; rc_is 0 "$?" "E13 an S1 fix patch fix-S1-U2.patch named on its patch: line is accepted (§8 step 8)"
grep -v '^patch: fix-S1-U2.patch ' "$LE/_note.md" > "$S/E14.note"; cp "$S/E14.note" "$LE/_note.md"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E14.txt" 2>&1; rc_is 1 "$?" "E14 the same fix patch WITHOUT its patch: line is an error"; has "$S/E14.txt" "names no 'patch: fix-S1-U2.patch sha256" "E14 …named"
rm -f "$LE/fix-S1-U2.patch"; mkpatch "$LE/fix-S1-U9.patch" "$K6/SyntheticLUfixU9.kt" NEW "// SYNTHETIC S1 fix of a unit L6 does not have"; note "$LE" "U1 U2 U3"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E15.txt" 2>&1; rc_is 1 "$?" "E15 a fix patch for a unit the lane does not have (U9) is refused"; has "$S/E15.txt" "fix-S1-U9.patch is not a patch the ORDER table names" "E15 …named"
rm -f "$LE/fix-S1-U9.patch"; cp "$S/E.note.good" "$LE/_note.md"; backdate "$LE"
bash "$LU" --check --only L6 --root "$WE" > "$S/E11.txt" 2>&1; rc_is 0 "$?" "E11 restored: clean again (the guards fire on the defect, not on the lane)"
snap "$WE" "$S/E.after"; cmp -s "$S/E.before" "$S/E.after" && ok "E12 the nine --check runs left HEAD, the index and status byte-identical" || bad "E12 --check changed the throwaway"
rmwt "$WE"
}

want_sec F && {
# ═══ F ═══  (round 1, process-holes M1: SeamReachabilityTest.kt's three writers; --check only)
echo; echo "═══ F: SeamReachabilityTest.kt — five module lines from three lanes, each cut on HEAD (--check only)"
HOLDF="L4:U1-ios,L4:U2-ios,L5:U1-ios,L5:U2o-ios,L5:U2i-ios,L5:U2i-android,L5:U3-ios,L5:D"
build_F() {  # <wt> <END|PINNED>: L4 (U1-android seam 1 → U2-android seam 3), L3 (U1 seam 1 → U2 unit), L5 (U2o-android seam 4); each lane's model starts from HEAD
  local wt="$1" mode="$2" L4="$1/$R/wave55-ua-heading-margin" L3="$1/$R/wave55-positioned-parent-slot" L5="$1/$R/wave55-counter-suffix-rows" a4 a3 a5
  mkdir -p "$L4" "$L3" "$L5"
  if [[ "$mode" == PINNED ]]; then a4='"content/PseudoGeneratedBox.kt",'; a5='"images/ImageCandidateChain.kt",'; a3='"transforms/TransformInheritance.kt",'
  else a4='"transforms/TransformInheritance.kt",'; a5="$a4"; a3="$a4"; fi       # END: every writer appends after the last entry
  model_init F4 "$wt" "$CRK" "$SRT"
  mkpatch "$L4/unit-U1-android.patch" "$KT/spacing/UaPositionedHeadingMargin.kt" NEW "// SYNTHETIC (F: L4 U1-android)"
  mkpatch "$L4/seam-1.patch" "$CRK" "// post-merge drop-everything it replaced is gone." "        // SYNTHETIC seam 1 (F: L4 U1-android)"
  mkpatch "$L4/seam-1.patch" "$SRT" "$a4" '            "spacing/UaPositionedHeadingMargin.kt", // SYNTHETIC F'
  mkpatch "$L4/unit-U2-android.patch" "$KT/typography/UAHeadingFoldGate.kt" NEW "// SYNTHETIC (F: L4 U2-android)"
  mkpatch "$L4/seam-3.patch" "$CRK" "        // SYNTHETIC seam 1 (F: L4 U1-android)" "        // SYNTHETIC seam 3 (F: L4 U2-android)"
  mkpatch "$L4/seam-3.patch" "$SRT" '"spacing/UaPositionedHeadingMargin.kt", // SYNTHETIC F' '            "typography/UAHeadingFoldGate.kt", // SYNTHETIC F'
  model_init F3 "$wt" "$CRK" "$SRT"
  mkpatch "$L3/unit-U1.patch" "$KT/layout/position/PositionedParentSlotGate.kt" NEW "// SYNTHETIC (F: L3 U1)"
  mkpatch "$L3/seam-1.patch" "$CRK" "val outOfFlowSlotMount: Modifier =" "                        // SYNTHETIC seam 1 (F: L3 U1)"
  mkpatch "$L3/seam-1.patch" "$SRT" "$a3" '            "layout/position/PositionedParentSlotGate.kt", // SYNTHETIC F'
  mkpatch "$L3/unit-U2.patch" "$KT/layout/position/PositionedParentRtlOrigin.kt" NEW "// SYNTHETIC (F: L3 U2)"
  mkpatch "$L3/unit-U2.patch" "$SRT" '"layout/position/PositionedParentSlotGate.kt", // SYNTHETIC F' '            "layout/position/PositionedParentRtlOrigin.kt", // SYNTHETIC F'
  model_init F5 "$wt" "$CRK" "$SRT"
  mkpatch "$L5/unit-U2o-android.patch" "$KT/lists/ListMarkerSuffixGap.kt" NEW "// SYNTHETIC (F: L5 U2o-android)"
  mkpatch "$L5/seam-4.patch" "$CRK" "gapPx = with(markerSnapDensity) { ListMarkerRow.gapDp.roundToPx() }," "                // SYNTHETIC seam 4 (F: L5 U2o-android)"
  mkpatch "$L5/seam-4.patch" "$SRT" "$a5" '            "lists/ListMarkerSuffixGap.kt", // SYNTHETIC F'
  note "$L4" "U1-android U2-android"; note "$L3" "U1 U2"; note "$L5" "U2o-android"; backdate "$L4"; backdate "$L3"; backdate "$L5"; }
WF="$(mkwt wt-F)"; build_F "$WF" PINNED; snap "$WF" "$S/F.before"
bash "$LU" --check --only L3,L4,L5 --hold "$HOLDF" --root "$WF" > "$S/F1.txt" 2>&1; rc_is 0 "$?" "F1 PINNED anchors, each cut on HEAD: --check clean (L4 → L3 → L5, §4 order)"
has "$S/F1.txt" "shared  $SRT: L4 L3 L5 (registered shared file)" "F2 …SeamReachabilityTest.kt is the registered shared file of three lanes"
for k in "L4:U1-android|U2-android U1-android" "L4:U2-android|U2-android" "L3:U1|U2 U1" "L3:U2|U2" "L5:U2o-android|U2o-android"; do
  hasre "$S/F1.txt" "ok +${k%%|*} +revert \\[ ${k#*|} \\] reverse-applies" "F3 revert dry-run OK on the landed series: ${k%%|*} [${k#*|}]"; done
snap "$WF" "$S/F.after"; cmp -s "$S/F.before" "$S/F.after" && ok "F4 --check touched nothing" || bad "F4 touched"
rmwt "$WF"
WF="$(mkwt wt-F2)"; build_F "$WF" END
bash "$LU" --check --only L3,L4,L5 --hold "$HOLDF" --root "$WF" > "$S/F5.txt" 2>&1; rc_is 1 "$?" "F5 the same writers at the list END, each cut on HEAD: --check goes red"
has "$S/F5.txt" "the series STOPS at" "F6 …the cumulative series stops (L3 / L5 cannot apply after L4)"
rmwt "$WF"
}

want_sec G && {
# ═══ G ═══  (round 2, process-holes S2: --contingency takes ONE pre-registered unit; --check / argument errors only)
echo; echo "═══ G: --contingency <L:U> (argument and dependency refusals; --check only)"
WG="$(mkwt wt-G)"; snap "$WG" "$S/G.before"
bash "$LU" --contingency --check --root "$WG" > "$S/G1.txt" 2>&1; rc_is 2 "$?" "G1 --contingency with no unit is refused"; has "$S/G1.txt" "needs ONE unit, L:U" "G1 …named"
bash "$LU" --contingency L5:U1-ios --check --root "$WG" > "$S/G2.txt" 2>&1; rc_is 2 "$?" "G2 --contingency on a unit that is not a contingency row is refused"; has "$S/G2.txt" "not a contingency row of the ORDER table" "G2 …named (it lists L1:NB-ios-b L5:U1-ios-LRM)"
has "$S/G2.txt" "L5:U1-ios-LRM" "G2 …the L5 LRM re-cut is a contingency row"
bash "$LU" --contingency L5:U1-ios-LRM --check --root "$WG" > "$S/G3.txt" 2>&1; rc_is 2 "$?" "G3 --contingency L5:U1-ios-LRM before U1-ios has landed is refused"; has "$S/G3.txt" "needs L5:U1-ios, which is neither selected nor landed" "G3 …named"
bash "$LU" --print-order --root "$WG" > "$S/G4.txt" 2>&1; rc_is 0 "$?" "G4 --print-order"; hasre "$S/G4.txt" "^  30 +L5 +U1-ios-LRM +unit-U1-ios-LRM.patch .*needs\[U1-ios\] revert\[U1-ios-LRM\] contingency" "G4 …row 30: L5 U1-ios-LRM, needs U1-ios, a contingency"
snap "$WG" "$S/G.after"; cmp -s "$S/G.before" "$S/G.after" && ok "G5 the G runs left HEAD, the index and status byte-identical" || bad "G5 changed the throwaway"
rmwt "$WG"
}

echo; snap "$SHARED" "$S/shared.after"
cmp -s "$SHARED_BEFORE" "$S/shared.after" && ok "Z1 the shared tree's HEAD, index and status are byte-identical before / after the whole self-test" || { bad "Z1 shared tree changed"; diff "$SHARED_BEFORE" "$S/shared.after"; }
left="$(git -C "$SHARED" worktree list --porcelain | grep -c "^worktree $S/")"; [[ "$left" == 0 ]] && ok "Z2 no throwaway worktree left registered" || bad "Z2 $left throwaway(s) left"
echo; echo "SELFTEST  $PASS passed, $FAIL failed"; (( FAIL == 0 ))
