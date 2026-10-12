#!/usr/bin/env bash
# tools/titan/wire-differential.sh — a GATE-FLAG wire differential that can fail (wave 55 L7 U2, BACKLOG 0(m)(2)).
#
# What it answers: did a change between two trees move any byte of the IR the gate would hand the runtimes? For each tree
# and each section of a gate run it runs EXACTLY the gate's own steps (tools/titan/section-runner.sh), then
# wire-differential.mjs classifies the two per-test IR trees document by document and its exit code is the verdict.
#   :375  POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node tools/titan/extract-fixture.mjs <tests.list…>
#   :416  node tools/titan/build-combined-fixture.mjs --out fixtures/wpt/_section-<sec>.json --tests <tests.list>
#   :435  ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i fixtures/wpt/_section-<sec>.json -o <out>"
#   :620  node tools/titan/split-combined-ir.mjs --in <out>/tmpOutput.json --out <per-test-ir>
# It STARTS CHROMIUM (the post-load / bidi / view-transition bakes) and the converter: an orchestrator window, never a
# builder lane, never while a gate runs on this host.
#
# Why a new script (the wave-54 record wave54-hyphenate-character/wl3-wire-differential.sh stays as it is): that one
# exited 0 whatever it counted, used a relative out dir inside `( cd "$TREE" … )` (30 ONE-SIDED, exit 0 — the record's
# first [W-L3] attempt), ignored the extract result, printed only WARN on a failed step, skipped a section with no
# tests.list silently, read the sections of the PRE side only, and called any suffix-strip match RENUMBERED.
#
# Usage: wire-differential.sh <pre-tree> <post-tree> <out-dir> --expect <file> [--run <id|dir>] [--sections a,b]
#                             [--dry-run | --classify-only]
#   <pre-tree> <post-tree>  throwaway checkouts (`git worktree add --detach …`) holding node_modules, tools/wpt and
#                           tools/titan/wpt-buckets.json (gitignored: symlink them from the main checkout — without the
#                           bucket index extract-fixture silently drops its lossy and wall tags, so the IR would not be the
#                           gate's) and the root local.properties; JDK 21 (JAVA_HOME, else java_home -v 21).
#   <out-dir>               created first; must not already hold pre/ or post/ (a differential is never overwritten).
#                           Bulky: put it under the gitignored tools/titan/runs/<id>/.
#   --expect <file>         `CONTENT-CHANGED <sec>/<file>` / `RENUMBERED <sec>/<file>` lines; an EMPTY file = "no change".
#   --run <id|dir>          the gate run whose sections/<sec>/tests.list are read (default wave55-open; an id resolves
#                           under THIS checkout's tools/titan/runs/, a value with a `/` is a run directory).
#   --sections a,b          the sections to run (default: every section of the run; each needs a tests.list).
#   --dry-run               resolve, check every refusal, print the plan; run nothing, write nothing.
#   --classify-only         skip the pipeline; classify <out-dir>/{pre,post} with <out-dir>/status.tsv.
# Exit: 2 usage or refusal (an unresolvable path; PRE == POST, or both in one checkout; either tree = THIS script's
#   checkout — extraction writes fixtures/wpt/<sec>/ inside the tree and the shared tree is where gates run; a tree
#   missing its extraction inputs; no <run>/sections; a section without tests.list; an out dir holding a previous
#   differential) · otherwise the classifier's: 0 · 3 pipeline · 4 one-sided · 5 changed set != expect (see the .mjs).
set -uo pipefail

usage() { sed -n '19,32p' "$0" >&2; echo "  REFUSED — $*" >&2; exit 2; }    # every usage problem is exit 2
refuse() { echo "wire-differential: REFUSED — $*" >&2; exit 2; }          # a refusal names its reason, never silent
absdir() { (cd -P "$1" 2>/dev/null && pwd -P); }                          # an existing dir → its physical absolute path
abs_nocreate() {                                                          # a dir that may not exist yet → absolute, physical
  local p="$1" rest=""                                                    # (the dry run must not create the out dir)
  while [[ ! -d "$p" ]]; do rest="/$(basename "$p")$rest"; p="$(dirname "$p")"; done   # climb to the deepest existing ancestor
  echo "$(absdir "$p")$rest"                                              # resolve it, then re-append the missing tail
}
top_of() {                                                                # the checkout a dir belongs to (itself if none)
  local t; t="$(git -C "$1" rev-parse --show-toplevel 2>/dev/null)" && absdir "$t" || echo "$1"
}

HERE="$(absdir "$(dirname "$0")")" || refuse "cannot resolve the script's own directory"   # tools/titan of this checkout
SELF_TOP="$(top_of "$HERE")"                                               # this script's checkout (never a tree under test)
POS=(); EXPECT=""; RUN=wave55-open; SECS_CSV=""; MODE=run                  # defaults: a real run against wave55-open
while (( $# )); do                                                        # flags anywhere; three positionals
  case "$1" in
    --expect|--run|--sections) [[ $# -ge 2 && -n "$2" && "$2" != --* ]] || usage "$1 needs a value"
      case "$1" in --expect) EXPECT="$2" ;; --run) RUN="$2" ;; --sections) SECS_CSV="$2" ;; esac; shift 2 ;;
    --dry-run) MODE=dry; shift ;;                                          # plan only
    --classify-only) MODE=classify; shift ;;                               # read an existing out dir
    -h|--help) sed -n '2,36p' "$0"; exit 0 ;;                              # the header is the manual
    -*) usage "unknown flag $1" ;;                                         # no silently ignored flag
    *) POS+=("$1"); shift ;;
  esac
done
(( ${#POS[@]} == 3 )) || usage "need <pre-tree> <post-tree> <out-dir> (got ${#POS[@]} positional arguments)"
[[ -n "$EXPECT" ]] || usage "--expect <file> is required (an EMPTY file means 'no change expected')"

# Every path absolute and physical BEFORE any cd (the wave-54 relative-OUT defect): the subshells below cd into the trees.
PRE="$(absdir "${POS[0]}")" || refuse "pre-tree ${POS[0]} is not a directory"
POST="$(absdir "${POS[1]}")" || refuse "post-tree ${POS[1]} is not a directory"
if [[ "$MODE" == run ]]; then mkdir -p "${POS[2]}" || refuse "cannot create out-dir ${POS[2]}"; OUT="$(absdir "${POS[2]}")"
else OUT="$(abs_nocreate "${POS[2]}")"; fi                                  # dry / classify: resolve, never create
[[ -n "$OUT" && "$OUT" == /* ]] || refuse "out-dir ${POS[2]} did not resolve to an absolute path"
[[ "$OUT" != *[[:space:]]* ]] || refuse "out-dir $OUT contains whitespace (gradle --args splits on it)"
EXPECT_DIR="$(absdir "$(dirname "$EXPECT")")" || refuse "expect file $EXPECT: its directory does not exist"
EXPECT="$EXPECT_DIR/$(basename "$EXPECT")"; [[ -f "$EXPECT" ]] || refuse "expect file $EXPECT does not exist (an EMPTY file means 'no change')"
if [[ "$RUN" == */* ]]; then RUN_DIR="$(absdir "$RUN")" || refuse "run dir $RUN does not exist"
else RUN_DIR="$SELF_TOP/tools/titan/runs/$RUN"; fi                         # an id: this checkout's gitignored runs/
[[ -d "$RUN_DIR/sections" ]] || refuse "no $RUN_DIR/sections (a missing run)"

# The trees: two distinct checkouts, neither of them this one.
[[ "$PRE" != "$POST" ]] || refuse "PRE == POST ($PRE): a tree compared with itself proves nothing"
TOP_PRE="$(top_of "$PRE")"; TOP_POST="$(top_of "$POST")"                  # a subdir of a checkout resolves to the checkout
[[ "$TOP_PRE" != "$TOP_POST" ]] || refuse "PRE and POST are in one checkout ($TOP_PRE)"
for T in "$PRE" "$POST"; do
  [[ "$(top_of "$T")" != "$SELF_TOP" ]] || refuse "$T is this script's own checkout ($SELF_TOP): extraction writes fixtures/wpt/<sec>/ inside the tree, and the shared tree is where gates run — use throwaway worktrees"
  [[ "$(top_of "$T")" == "$T" ]] || refuse "$T is inside the checkout $(top_of "$T"), not its top (the steps run from the tree root)"
  if [[ "$MODE" != classify ]]; then                                       # the pipeline's inputs, all gitignored
    for need in node_modules tools/wpt tools/titan/wpt-buckets.json tools/titan/extract-fixture.mjs; do
      [[ -e "$T/$need" ]] || refuse "$T has no $need (symlink node_modules, tools/wpt and tools/titan/wpt-buckets.json from the main checkout): a vacuous or non-gate extraction on both sides would read 'identical'"
    done
  fi
done

# The sections: the requested ones, or every section of the run — each with its tests.list, no silent skip.
if [[ -n "$SECS_CSV" ]]; then IFS=',' read -r -a SECS <<<"$SECS_CSV"
else SECS=(); for d in "$RUN_DIR/sections"/*/; do SECS+=("$(basename "$d")"); done; fi
(( ${#SECS[@]} > 0 )) || refuse "no section to run in $RUN_DIR/sections"
for sec in "${SECS[@]}"; do [[ -f "$RUN_DIR/sections/$sec/tests.list" ]] || refuse "section $sec has no $RUN_DIR/sections/$sec/tests.list"; done
SECS_CSV="$(IFS=,; echo "${SECS[*]}")"                                     # the classifier reads exactly these
if [[ "$MODE" == run ]]; then for tag in pre post; do [[ ! -e "$OUT/$tag" ]] || refuse "$OUT/$tag already exists (never overwrite a differential: use a fresh out-dir)"; done; fi

echo "wire-differential ($MODE)"                                          # the resolved plan, every mode
echo "  PRE     $PRE"
echo "  POST    $POST"
echo "  OUT     $OUT"
echo "  EXPECT  $EXPECT ($(grep -cvE '^[[:space:]]*(#|$)' "$EXPECT") expected line(s))"
echo "  RUN     $RUN_DIR (sections: ${SECS[*]})"
if [[ "$MODE" == dry ]]; then
  echo "  per tree and section: extract (POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1) → combine → convert (gradle --no-daemon) → split → $OUT/<tag>/<sec>/per-test-ir; status → $OUT/status.tsv"
  echo "DRY RUN OK — nothing run, nothing written"; exit 0
fi

if [[ "$MODE" == run ]]; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null)}"   # the converter's toolchain (CLAUDE.md)
  printf '# tag\tsection\trc_extract\trc_combine\trc_convert\trc_split\textracted\tfail\n' >"$OUT/status.tsv"
  for tag in pre post; do
    TREE="$PRE"; [[ "$tag" == post ]] && TREE="$POST"                      # the same steps, the two trees
    for sec in "${SECS[@]}"; do
      LIST="$RUN_DIR/sections/$sec/tests.list"; W="$OUT/$tag/$sec"; mkdir -p "$W/out"   # W is absolute: safe inside the cd
      TESTS=(); while IFS= read -r t || [[ -n "$t" ]]; do [[ -n "$t" ]] && TESTS+=("$t"); done <"$LIST"   # bash 3.2-safe; keeps an unterminated last line
      echo "[$tag $sec] ${#TESTS[@]} tests"
      ( cd "$TREE" && POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 nice -n 10 node tools/titan/extract-fixture.mjs "${TESTS[@]}" ) >"$W/extract.txt" 2>&1; rx=$?
      grep '^FAIL ' "$W/extract.txt" >"$OUT/fail-$tag-$sec.txt"            # the FAIL set the classifier compares
      ne="$(grep -c '^extracted ' "$W/extract.txt")"; nf="$(grep -c . "$OUT/fail-$tag-$sec.txt")"
      ( cd "$TREE" && node tools/titan/build-combined-fixture.mjs --out "fixtures/wpt/_section-$sec.json" --tests "$LIST" ) >"$W/combine.txt" 2>&1; rb=$?
      rv=-; rs=-                                                           # '-' = not run (a failure to the classifier)
      if [[ "$rb" == 0 ]]; then
        ( cd "$TREE" && ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i fixtures/wpt/_section-$sec.json -o $W/out" --quiet ) >"$W/convert.txt" 2>&1; rv=$?
      fi
      if [[ "$rv" == 0 ]]; then
        ( cd "$TREE" && node tools/titan/split-combined-ir.mjs --in "$W/out/tmpOutput.json" --out "$W/per-test-ir" ) >"$W/split.txt" 2>&1; rs=$?
      fi
      printf '%s\t%s\t%s\t%s\t%s\t%s\textracted=%s\tfail=%s\n' "$tag" "$sec" "$rx" "$rb" "$rv" "$rs" "$ne" "$nf" | tee -a "$OUT/status.tsv"
    done
  done
fi

# The verdict is the classifier's exit code (0 / 3 / 4 / 5), passed through unchanged. A --classify-only re-read writes
# its own file (named by its expect file), so it never overwrites the pipeline run's classify.txt.
CLS="$OUT/classify.txt"; [[ "$MODE" == classify ]] && CLS="$OUT/classify-only.$(basename "$EXPECT").txt"
node "$HERE/wire-differential.mjs" classify --pre "$OUT/pre" --post "$OUT/post" --expect "$EXPECT" \
  --tests-root "$RUN_DIR/sections" --status "$OUT/status.tsv" --sections "$SECS_CSV" | tee "$CLS"
exit "${PIPESTATUS[0]}"
