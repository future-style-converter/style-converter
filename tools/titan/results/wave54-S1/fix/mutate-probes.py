#!/usr/bin/env python3
# wave54-S1/fix/mutate-probes.py — mutation proof for the S1-fix left-edge pin, in a mini export (never the shared
# tree). Usage: python3 mutate-probes.py <scratch-export-dir>. For each probe: the
# pin's executable claim is "the nodot fake prints GEOMETRY WRONG on web (both probes) and iOS (lists-bakes)". Mutate
# the new check away (`if not near(...)` -> `if False and not near(...)`) -> the claim goes RED (OK comes back);
# restore from the saved bytes, assert sha256 before == after, re-run -> GREEN.
import hashlib, os, shutil, subprocess, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))   # repo root
EXP = sys.argv[1]
shutil.rmtree(EXP, ignore_errors=True)
for rel in ('tools/titan/results/wave53-plan/geometry_common.py', 'tools/titan/results/wave53-plan/lists-bakes.geometry.py',
            'tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py', 'tools/titan/results/wave54-rtl-marker-bake/skeptic/geom-fakes.py'):
    os.makedirs(os.path.dirname(f'{EXP}/{rel}'), exist_ok=True); shutil.copy2(f'{R}/{rel}', f'{EXP}/{rel}')
os.makedirs(f'{EXP}/tools/titan/runs', exist_ok=True)
os.symlink(f'{R}/tools/titan/runs/wave53-probe', f'{EXP}/tools/titan/runs/wave53-probe')   # the fakes' source captures
os.symlink(f'{R}/tools/wpt', f'{EXP}/tools/wpt')                                           # the frozen refs
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
def claim():
    """Run the fakes; return the nodot verdict lines the pin must keep WRONG."""
    out = subprocess.run(['python3', f'{EXP}/tools/titan/results/wave54-rtl-marker-bake/skeptic/geom-fakes.py', f'{EXP}/fakes'],
                         capture_output=True, text=True).stdout.splitlines()
    sec, got = None, {}
    for l in out:
        if l.startswith('--- fake'):
            sec = l
        elif sec and 'nodot' in sec:
            probe = 'rtl' if 'rtl-marker' in sec else 'lists'
            plat = l.split()[2] if l.startswith('[M]') else l.split()[1]
            got[(probe, plat)] = 'WRONG' if 'GEOMETRY WRONG' in l else 'OK'
    need = [('rtl', 'web'), ('lists', 'web'), ('lists', 'ios')]
    return all(got.get(k) == 'WRONG' for k in need), {f'{a}:{b}': got.get((a, b)) for a, b in need}
MUT = {'tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py': ('if not near(min(xs), MARKER_LEFT, 2):', 'if False and not near(min(xs), MARKER_LEFT, 2):'),
       'tools/titan/results/wave53-plan/lists-bakes.geometry.py': ('if not near(min(xs), 133, 2):', 'if False and not near(min(xs), 133, 2):')}
ok, v = claim(); print(f'baseline: claim {"GREEN" if ok else "RED"} {v}')
for rel, (old, new) in MUT.items():
    p = f'{EXP}/{rel}'; saved = open(p, 'rb').read(); before = sha(p)
    s = saved.decode(); assert s.count(old) == 1, rel
    open(p, 'w', encoding='utf-8').write(s.replace(old, new))
    ok, v = claim(); print(f'MUT {os.path.basename(rel)} (left-edge check disabled; mutated sha {sha(p)[:8]}): claim {"GREEN" if ok else "RED"} {v}')
    open(p, 'wb').write(saved); after = sha(p)
    print(f'  restore: sha256 before {before[:16]} after {after[:16]} -> {"BYTE-EXACT" if before == after else "DIFFERS"}; '
          f'shared-tree file equal: {after == sha(f"{R}/{rel}")}')
    ok, v = claim(); print(f'  re-run: claim {"GREEN" if ok else "RED"} {v}')
