#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r4.measure.py — the MEASURED margins behind round 4's reads (fix-r4.done.md; plan-skeptic-
# round-4.verify.md M1-M4). Read-only over runs, refs and the replay folders; prints to stdout (→ fix-r4.measure.out.txt).
#   §1 L4 `face xor:` on the fixed arms (C, D: every face-present native line, as the probe prints it) and, computed here with the
#      probe's rule, on WEB's face (not judged: a control row) — the passing pictures' margin.
#   §2 L4 why FACE_RUN: the inset-001…004 anchors are the 2em bold face the natives ALREADY paint (a collapsed-chain UA h1); their
#      distance from Chromium's ref is the distance a device face will sit from it at stage 1.
#   §3 L5 the new [H] / [T] / [C] / [I] reads on ok-all (as the probe prints them) and the cross-renderer distances that make
#      TWIN_RUN the stage-1 mechanism (Android's tabular '1' vs the ref's / web's; iOS's own marker glyphs vs Android's).
#   §4 L6 `extra` under four allowed regions on the six U2 replays and on every strictly-additive fake of the [X] group
#      (plan-skeptic-round-4.fakes/added_ink4.py, the verifier's builders): the region chosen must read 0 on the replays and
#      reject every fake but the 1-px floor.
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/fix-r4.measure.py   (from the repo root)
import importlib.util, os, re, subprocess
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PY = ['/opt/homebrew/bin/python3.12', '-B']
REFS = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
W54 = f'{ROOT}/tools/titan/runs/wave54-final/sections'
A = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(np.int16)
lvl = lambda a: (a.min(axis=2) < 200).astype(np.int16) + (a.sum(axis=2) < 300).astype(np.int16)
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
capp = lambda sec, test, plat, run='wave54-final': f'{ROOT}/tools/titan/runs/{run}/sections/{sec}/{DIRS[plat]}/wpt__{sec}__{test.replace("/", "__")}.png'
def probe(args):
    return subprocess.run(PY + [os.path.join(HERE, args[0])] + args[1:], cwd=ROOT, capture_output=True, text=True).stdout.splitlines()
def moved(m, dx, dy):
    h, w = m.shape; o = np.zeros_like(m)
    o[max(0, dy):min(h, h + dy), max(0, dx):min(w, w + dx)] = m[max(0, -dy):min(h, h - dy), max(0, -dx):min(w, w - dx)]; return o
def bands(m, y0, y1, gap=2):
    out, cur, blank = [], None, 0
    for y in range(y0, y1):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            cur = [y, y, xs.min(), xs.max()] if cur is None else [cur[0], y, min(cur[2], xs.min()), max(cur[3], xs.max())]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > gap:
                out.append(cur); cur = None; blank = 0
    return out + ([cur] if cur else [])

print('# fix-r4.measure.out.txt — round-4 measured margins (fix-r4.measure.py); every number below is executed, none typed')
print('\n## §1 L4 `face xor:` — the fixed arms as the probe prints them (FACE_XOR_TOL 2) and web\'s face (computed here, not judged)')
for arm in ('C', 'D'):
    for l in probe(['ua-heading-margin.geometry.py', 'wave54-final', '--arm', arm]):
        m = re.match(r'(\[[MF]\] text-decoration-inset-0(?:05|06|14)) +(ios|android) .* xor (\S+) → (GEOMETRY \w+)', l)
        if m:
            print(f'  arm {arm}  {m[1]} {m[2]:8} xor {m[3]:12} {m[4]}')
for t in ('005', '006', '014'):
    r, wb = A(f'{REFS}/css-text-decor/text-decoration-inset-{t}.png'), A(capp('css-text-decor', f'text-decoration-inset-{t}', 'web'))
    rl, wl = lvl(r), lvl(wb); rb, bb = bands(r.sum(axis=2) < 300, 96, 400), bands(wb.sum(axis=2) < 300, 96, 400)
    xs = []
    for (tt, bt, _, _), (rt, rbt, _, _) in zip(bb, rb):
        y0, y1 = min(tt, rt) - 2, max(bt, rbt) + 3; pred = np.zeros_like(rl); pred[rt - 2:rbt + 3] = rl[rt - 2:rbt + 3]
        xs.append(min(int(np.abs(wl[y0:y1] - moved(pred, dx, dy)[y0:y1]).sum()) for dx in range(-3, 4) for dy in (-1, 0, 1)))
    print(f'  web  text-decoration-inset-{t}: bands {len(bb)} vs ref {len(rb)}; per-band xor {xs} (Chromium vs the web harness\'s underline / AA: why web keeps the round-3 reads)')

print('\n## §2 L4 the device-face distance — inset-001…004 (the natives already paint the 2em bold face) vs the ref, y96-259')
for t in ('001', '002', '003', '004'):
    rr = A(f'{REFS}/css-text-decor/text-decoration-inset-{t}.png')[96:260]
    for plat in ('ios', 'android'):
        a = A(capp('css-text-decor', f'text-decoration-inset-{t}', plat))[96:260]
        rd, ad = int((rr.sum(axis=2) < 300).sum()), int((a.sum(axis=2) < 300).sum())
        best = min((int(np.abs(lvl(a) - np.roll(np.roll(lvl(rr), dy, 0), dx, 1)).sum()), dx, dy) for dy in range(-4, 5) for dx in range(-4, 5))
        print(f'  inset-{t} {plat:8} dark px {ad} vs ref {rd} ({100.0 * (ad - rd) / rd:+.1f} %); level XOR {best[0]} at dx {best[1]:+d} dy {best[2]:+d}')
print('  → a ref tolerance re-set to a device face would admit added ink up to that distance: stage 1 re-pins FACE_RUN instead (D39)')

print('\n## §3 L5 the new reads on ok-all (as the probe prints them) and the cross-renderer distances')
for l in probe(['counter-suffix-rows.geometry.py', 'replay:ok-all', 'wave54-final']):
    if re.match(r'\[(H|T|C|I)\] \S+ (ios|android) ', l):
        print('  ' + re.sub(r' (row\d:|y\d+:marker).*?(?= ink | →)', ' …', l)[:200])
cs = {p: A(capp('css-counter-styles', 'counter-suffix', p)) for p in ('web', 'ios', 'android')}
cs['ref'] = A(f'{REFS}/css-counter-styles/counter-suffix.png')
W = (slice(208, 232), slice(136, 151))
for a_, b_ in (('android', 'ref'), ('android', 'web'), ('android', 'ios')):
    wa, wb2 = np.zeros_like(lvl(cs[a_])), np.zeros_like(lvl(cs[b_])); wa[W] = lvl(cs[a_])[W]; wb2[W] = lvl(cs[b_])[W]
    n = min(int(np.abs(wa - moved(wb2, dx, dy)).sum()) for dx in range(-2, 3) for dy in (-1, 0, 1))
    print(f'  [T] row-9 digit window: {a_} vs {b_} level XOR at the best offset {n}' + ('   (iOS base: the PROPORTIONAL \'1\' U3-ios replaces)' if b_ == 'ios' else ''))
for k, (y0, y1) in ((3, (64, 88)), (4, (88, 112))):
    ia, aa = lvl(cs['ios'])[y0:y1, 30:64], lvl(cs['android'])[y0:y1, 30:64]
    print(f'  [H] row {k} marker zone: iOS base level px {int(ia.sum())} vs Android base {int(aa.sum())} (other glyph shapes and order: why iOS reads its OWN glyphs, moved)')

print('\n## §4 L6 `extra` under four allowed regions (≤ 1 passes) — the U2 replays, then every strictly-additive fake of the [X] group')
_s = importlib.util.spec_from_file_location('pt', os.path.join(HERE, 'probes-teeth.py')); pt = importlib.util.module_from_spec(_s); _s.loader.exec_module(pt)
_s = importlib.util.spec_from_file_location('ai4', os.path.join(HERE, 'plan-skeptic-round-4.fakes', 'added_ink4.py')); ai4 = importlib.util.module_from_spec(_s); _s.loader.exec_module(ai4); ai4.pt = pt
def dil(m):
    o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]; return o
def extras(a, base, fill):
    fa, fb, bd = fill(a), fill(base), base.sum(axis=2) < 150; nw = base.min(axis=2) < 250
    return {'dilate1(fill ∪ band) [round 3]': int((fa & ~dil(fb | bd)).sum()), 'fill ∪ band [undilated]': int((fa & ~(fb | bd)).sum()),
            'fill ∪ dilate1(band)': int((fa & ~(fb | dil(bd))).sum()), 'fill ∪ band ∪ (dilate1(band) ∩ base not white) [round 4]': int((fa & ~(fb | bd | (dil(bd) & nw))).sum())}
grp = [c for c in pt.l6_cases() if c[2] == 'X-fixed'][0][3]
low, nfake = {}, 0
for (sec, plat, test), F in grp.items():
    fill = next(f for s_, t_, f in pt.L6_X if t_ == test); B = A(capp(sec, test, 'android'))
    name = test.split('/')[-1]
    print(f'  replay {name:48} ' + ' | '.join(f'{v}' for v in extras(F, B, fill).values()))
    ks = ai4.classes(F, B)
    for v in ai4.WHOLE + [f'c{ci}:{n}' for ci in range(2) for n in ai4.PERCLASS + [p for i in range(ai4.MAXB) for p in ai4.PERBAND(i)]]:
        o = ai4.whole(F, v) if not v.startswith('c') else (ai4.variant(ks[int(v[1])], v.split(':', 1)[1]) if int(v[1]) < len(ks) else None)
        if o is None or (o == F).all() or name.endswith('safe-002'):
            continue
        nfake += 1
        for k, x in extras(o, B, fill).items():
            if x <= 1:
                low.setdefault(k, []).append(f'{name[-28:]} {v} ({x})')
print('  (columns: ' + ' | '.join(extras(F, B, fill).keys()) + ')')
print(f'  {nfake} faked [X] captures (safe-002 left out: no passing fixed picture, round-3 N5); faked captures read ≤ 1 under each region:')
for k in extras(F, B, fill):
    l = low.get(k, [])
    print(f'    {k:60} {len(l):3}' + (f'   e.g. {"; ".join(sorted(set(x.split()[1] for x in l))[:8])}' if l else ''))
