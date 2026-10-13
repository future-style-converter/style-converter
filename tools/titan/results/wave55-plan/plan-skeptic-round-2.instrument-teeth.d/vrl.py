import os, sys, importlib.util
sys.dont_write_bytecode = True
sys.argv = [sys.argv[0], 'NONE']
spec = importlib.util.spec_from_file_location('r2', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r2fakes.py')); r2 = importlib.util.module_from_spec(spec); spec.loader.exec_module(r2)
pt = r2.pt; doc = 'dynamic-offset-vrl-rtl-001'; fx = pt.l3_fixed(doc)
print('fixed replay:', 'found' if fx is not None else 'NONE')
for name, a in (('fixed', fx), ('parent-erased', r2.l3_parent(fx, 'erase')), ('parent+3x', r2.l3_parent(fx, 'x+3'))):
    rid = pt.make_run(r2.SCR, f'L3-vrl-{name}', {('css-writing-modes', 'android', doc): a})
    rc, rows = pt.gate(rid, 'L3', 'css-writing-modes', r2.EXP, r2.PY, os.path.join(r2.SCR, f'gate-L3-vrl-{name}.json'))
    r = rows[('positioned-parent-slot.geometry.py', f'[S] {doc} android')]
    print(f'  {name:14} [S] {doc} android ({r["class"]}): {r["verdict"]} — {r["line"][-90:]}')
