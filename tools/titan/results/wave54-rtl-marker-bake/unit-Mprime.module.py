#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/unit-Mprime.module.py — writes tools/titan/bidi-marker-bake.mjs for unit M′:
# the wave-53 module restored byte-for-byte in CODE from `git show b0edb788^:tools/titan/bidi-marker-bake.mjs` (fix-pass
# state), with only two comment blocks re-trued for wave 54 (the banner, and planMarker's `origin` = the enclosing
# bake ROOT's padding-box origin). Reads the restored bytes from git, so it is reproducible on any checkout.
import subprocess, sys
src = subprocess.run(['git', 'show', 'b0edb788^:tools/titan/bidi-marker-bake.mjs'], capture_output=True, check=True).stdout.decode('utf-8')
lines = src.split('\n')
# The wave-53 banner is lines 1-31 (index 0-30); line 32 is the blank before the imports.
assert lines[1].startswith('// tools/titan/bidi-marker-bake.mjs') and lines[31] == '', 'banner shape changed'
banner = '''//
// tools/titan/bidi-marker-bake.mjs — the ::marker of a list item INSIDE a bidi-bake
// root, baked the way bidi-bake.mjs bakes text: wave-53 hunk M (b0edb788^) restored
// as wave-54 lane L1 unit M′ (tools/titan/results/wave54-plan/rtl-marker-bake.md).
//
// THE SILENT FALLTHROUGH IT CLOSES: the bidi bake retires a root's `direction`, but
// its walker collects TEXT NODES only and a `::marker` is no DOM node, so the one
// fact still depending on that direction — the marker's SIDE — was dropped.
// css-counter-styles/counter-suffix: web hangs the `dir=rtl` markers LEFT (x46-58),
// iOS/Android paint none, the ref hangs `.1 .2 .א .ב` RIGHT (x133-145) — css-lists-3:
// an `outside` marker sits on the item's INLINE-START side, UA `::marker { unicode-
// bidi: isolate; font-variant-numeric: tabular-nums; white-space: pre }` (App. A).
//
// Per kept list-item BOX (never a root: every runtime paints a root's marker today)
// Chromium gives the STRING + BOX (CDP DOMSnapshot: the marker pseudo node's layout
// box and LayoutText) and the GLYPHS (a hidden probe span in the computed ::marker
// style), the probe advance self-checked against the box at MARKER_PROBE_EPS. Out
// come positioned runs + `list-style-type: none` on the item (no `meta.markerText`).
// WAVE 54: the runs are OWNED BY THE ENCLOSING (relative) BAKE ROOT, measured from
// its padding box — under the abspos `<li>` the Compose Column loop gave run 2 +20 px
// and run 3 no slot (brief §3); bidi-bake.mjs planBidiBake makes that choice. A no-
// strong-letter run ("1.") is split per grapheme: one rtl `.1` run would rest on bidi.
//
// MODELS of Chromium, not its answer, each stamped on the item and MARKER-scoped
// (never a whole-test bail): no CDP box → the analytic inline-start content edge
// (`marker-box-modelled`; a box missing the advance by > EPS takes it too,
// `marker-probe-mismatch`); no CDP string → the counter-style bake's on a CLONE,
// ". " suffix space restored (`marker-text-modelled`). An image marker, no first-
// line run, a marker font unlike the first line's or an item not re-found by its
// rect leaves it as it was, stamped `marker-not-baked`.'''.split('\n')
assert len(banner) <= 31, len(banner)
out = banner + lines[31:]
text = '\n'.join(out)
old_doc = '''/** Pure planning for ONE kept list-item BOX: `f` its facts, `origin` its padding-
 *  box origin, `first` its logically-first own run `{ run, style }`. → null (no
 *  ::marker), `{ lossy }` (declined: item unchanged, stamped) or `{ boxProps, runs, lossy }`. */'''
new_doc = '''/** Pure planning for ONE kept list-item BOX: `f` its facts, `origin` the padding-box origin of the
 *  bake ROOT that will own the runs (wave 54), `first` the item's logically-first own run `{ run, style }`.
 *  → null (no ::marker), `{ lossy }` (declined: item unchanged, stamped) or `{ boxProps, runs, lossy }`. */'''
assert text.count(old_doc) == 1
text = text.replace(old_doc, new_doc)
open('tools/titan/bidi-marker-bake.mjs', 'w', encoding='utf-8').write(text)
print('tools/titan/bidi-marker-bake.mjs:', text.count('\n') + (0 if text.endswith('\n') else 1), 'lines')
