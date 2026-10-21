import os, sys, importlib.util
sys.dont_write_bytecode = True
sys.argv = [sys.argv[0], 'NONE']
spec = importlib.util.spec_from_file_location('r2', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r2fakes.py')); r2 = importlib.util.module_from_spec(spec); spec.loader.exec_module(r2)
pt, np = r2.pt, r2.np
t = 'text-decoration-inset-011'
def mirror_band(a, k):              # count-preserving: band k's ink mirrored inside its own x extent (translation rule reads extents only)
    b = pt.bands(pt._dk(a), 112, 400)[k]; top, bot, x0, x1 = b; o = a.copy(); o[top - 1:bot + 2, x0:x1 + 1] = a[top - 1:bot + 2, x0:x1 + 1][:, ::-1]; return o
for arm in ('C', 'U1'):
    repl = {('css-text-decor', p, t): mirror_band(pt.png(f'ua-heading-margin-replay/{t}-{p}-{arm}.png'), 0) for p in ('ios', 'android')}
    rid = pt.make_run(r2.SCR, f'L4-011-{arm}-band1-mirrored', repl)
    for (sec, plat, test), arr in repl.items():
        os.makedirs(f'{r2.SCR}/pngs/L4-011-{arm}-band1-mirrored', exist_ok=True); r2.I(arr).save(f'{r2.SCR}/pngs/L4-011-{arm}-band1-mirrored/{plat}__{t}.png')
    rc, rows = pt.gate(rid, 'L4', 'css-text-decor', r2.EXP, r2.PY, os.path.join(r2.SCR, f'gate-L4-011-{arm}.json'))
    for p in ('ios', 'android'):
        r = rows[('ua-heading-margin.geometry.py', f'[M] {t} {p}')]; print(f'  arm {arm}: [M] {t} {p} ({r["class"]}): {r["verdict"]} — {r["line"][-100:]}')
