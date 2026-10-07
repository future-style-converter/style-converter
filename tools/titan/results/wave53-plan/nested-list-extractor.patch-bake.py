import sys
p=sys.argv[1]; s=open(p).read()
def rep(old,new):
    global s
    assert s.count(old)==1,(old,s.count(old)); s=s.replace(old,new)
rep("    lastNeg: 0,      // the LAST NON-ZERO −increment (signed) — the trailing term\n",
    "    lastNeg: 0,      // the LAST NON-ZERO −increment (signed) — the trailing term\n    lastNegBy: null,  // which box made that step (element index / pseudo key)\n")
rep("function setCounter(set, name, value) {", "function setCounter(set, name, value, who = null) {")
rep("    inst.implied = inst.negSum + value;\n",
    "    // §4.4.2 step 3.3 breaks BEFORE 3.4, then step 4 adds the last non-zero\n    // incrementNegated: equal to the old sum when the setter stepped itself.\n    inst.implied = inst.negSum + value + (inst.lastNegBy === who ? 0 : inst.lastNeg);\n")
rep("function bumpCounter(set, name, delta) {", "function bumpCounter(set, name, delta, who = null) {")
rep("    if (delta !== 0) inst.lastNeg = -delta;\n", "    if (delta !== 0) { inst.lastNeg = -delta; inst.lastNegBy = who; }\n")
rep("function applyPairs(set, raw, dflt, kind) {", "function applyPairs(set, raw, dflt, kind, who = null) {")
rep("    if (kind === 'set') setCounter(set, p.name, p.value);\n    else bumpCounter(set, p.name, p.value);\n",
    "    if (kind === 'set') setCounter(set, p.name, p.value, who);\n    else bumpCounter(set, p.name, p.value, who);\n")
rep("function pseudoSet(bag, own) {", "function pseudoSet(bag, own, who = null) {")
rep("  if (!applyPairs(set, props['counter-set'], 0, 'set')) return null;\n  if (!applyPairs(set, props['counter-increment'], 1, 'increment')) return null;\n",
    "  if (!applyPairs(set, props['counter-set'], 0, 'set', who)) return null;\n  if (!applyPairs(set, props['counter-increment'], 1, 'increment', who)) return null;\n")
rep("        bumpCounter(own, 'list-item', li && li.reversed ? -1 : 1);\n", "        bumpCounter(own, 'list-item', li && li.reversed ? -1 : 1, nodeIdx);\n")
rep("      if (!applyPairs(own, incRaw, 1, 'increment')) { ok = false; return; }\n", "      if (!applyPairs(own, incRaw, 1, 'increment', nodeIdx)) { ok = false; return; }\n")
rep("      if (listItem && Number.isFinite(liValue)) setCounter(own, 'list-item', liValue);\n", "      if (listItem && Number.isFinite(liValue)) setCounter(own, 'list-item', liValue, nodeIdx);\n")
rep("      if (!applyPairs(own, node.properties?.['counter-set'], 0, 'set')) { ok = false; return; }\n", "      if (!applyPairs(own, node.properties?.['counter-set'], 0, 'set', nodeIdx)) { ok = false; return; }\n")
rep("        const scoped = pseudoSet(bag, own);\n", "        const scoped = pseudoSet(bag, own, `${nodeIdx}::${name}`);\n")
open(p,'w').write(s); print('bake patched')
