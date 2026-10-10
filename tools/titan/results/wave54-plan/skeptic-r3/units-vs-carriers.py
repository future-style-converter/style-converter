# skeptic-r3: for every corpus prediction row, the units whose captures carry its cell vs the row's `units` / `requires`.
# A withdrawn row (all its units reverted) is held to must-not-move by adjudicate R5; if a KEPT unit not named in
# `units` still carries the cell, R5 fails on a correct tree. Also: must-not-move cells vs carriers; control geometry
# keys whose cell is a carrier (a control FAIL there is caught by neither R4 nor R7).
import json, sys
E = json.load(open(sys.argv[1]))
def cell_of_stem(st, plat): return '/'.join(st.split('__')[1:]) + '.html ' + plat
carriers = {}
for ln, l in E['lanes'].items():
    for u, U in l['revertUnits'].items():
        for plat, stems in U['captures'].items():
            for s in stems: carriers.setdefault(cell_of_stem(s, plat), set()).add((ln, u))
bad = 0; nopred = 0
for ln, l in E['lanes'].items():
    for p in l['predictions']:
        if p['kind'] == 'non-corpus': continue
        by = {u for (L, u) in carriers.get(p['cell'], set()) if L == ln}
        other = {L for (L, u) in carriers.get(p['cell'], set()) if L != ln}
        if by != set(p['units']) or other:
            bad += 1; print(f'MISMATCH {ln} {p["cell"]}: units {p["units"]} carriers-in-lane {sorted(by)} other-lanes {sorted(other)}')
print(f'prediction rows checked; mismatches {bad}')
# carriers with no prediction row
pc = {p['cell'] for l in E['lanes'].values() for p in l['predictions']}
nc = sorted(c for c in carriers if c not in pc)
print(f'carrier cells without a prediction row: {len(nc)}')
for c in nc: print('   ', c, sorted(carriers[c]))
