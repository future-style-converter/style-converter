#!/usr/bin/env bash
# Fix-pass L1 mutations FX1-FX5, run in the node export (never the shared tree).
SP=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/fix
X=$SP/nodeX; T=(tools/titan/bidi-bake.test.mjs tools/titan/counter-bake.test.mjs); cd "$X" || exit 2
m(){ local id=$1; shift; echo -n "$id "; python3 "$SP/mut.py" "$X" "$@" "${T[@]}"; }
m FX1 tools/titan/bidi-marker-bake.mjs "    if (!el) { out[it.key] = { error: 'list item not re-found by rect' }; continue; }" "    if (!el) continue;"
m FX2 tools/titan/bidi-marker-bake.mjs "items.filter((i) => facts[i.key] && !facts[i.key].error)" "items.filter((i) => facts[i.key])"
m FX3 tools/titan/bidi-bake.mjs "    if (lossy?.length) {\n      cmp._lossy = true;\n      cmp._lossyReasons = [...new Set([...(cmp._lossyReasons ?? []), ...lossy])];\n    }\n" ""
m FX4 tools/titan/bidi-marker-bake.mjs "y: first.run.y + q.y - q0," "y: first.run.y + q.y,"
m FX5 tools/titan/counter-bake.mjs 'const scoped = pseudoSet(bag, own, `${nodeIdx}::${name}`);' 'const scoped = pseudoSet(bag, own, nodeIdx);'
