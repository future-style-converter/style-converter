#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/u3-pins.sh — wave 54 lane L3: the U3 / U3b pins and their executed
# mutations, run INSIDE seam-verify.sh (seam-3 [+ seam-3b] applied under the extract-fixture.mjs lock).
# Usage: u3-pins.sh u3 | u3b
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$R"; M="$R/tools/titan/results/wave54-hyphenate-character/mutate.sh"
X=tools/titan/extract-fixture.mjs; T=tools/titan/extract-fixture-br-line-context.test.mjs
echo "--- pins ($1)"; node --test "$T" tools/titan/extract-fixture.test.mjs 2>&1 | grep -E '^# (tests|pass|fail|skipped)|^not ok' ; rc=${PIPESTATUS[0]}
if [ "$1" = u3 ]; then
  "$M" "U3-a drop the re-arm (seam-3 applied)" $X '          childLineCtx.hasInline = true;' '          void 0; /* mutated */' -- node --test "$T"
  "$M" "U3-b count white-space-only runs as content (seam-3 applied)" $X '/[^ \t\n\r\f]/.test(e.text))) {' '/[\s\S]/.test(e.text))) {' -- node --test "$T"
else
  "$M" "U3b-a the inherited reading: an ancestor's line box (seam-3+3b applied)" $X 'hostLineBox: brHostLineBoxPx(props) };' 'hostLineBox: brHostLineBoxPx(props) ?? lineCtx?.hostLineBox ?? null };' -- node --test "$T"
  "$M" "U3b-b drop the host line box from the br rule (seam-3+3b applied)" $X "(lineCtx?.hostLineBox ?? '20px');" "'20px';" -- node --test "$T"
fi
exit $rc
