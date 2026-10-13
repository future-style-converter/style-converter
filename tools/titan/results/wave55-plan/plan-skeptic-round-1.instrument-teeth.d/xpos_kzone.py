# xpos_kzone.py — two measurements the note cites: (1) the [X] tally honesty check: in the lane's OWN U2 replay, how far each
# abspos child (4-connected fill component >= 20 px) sits from its nearest ref component; (2) the proposed [K] zone
# extension (x64-389, y64-159: rows 3-6 item text) on the ok-all replay vs wave54-final, and on the H-item-text-gone fake.
from lib import *
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
def comps(m):
    H, W = m.shape; seen = np.zeros_like(m); out = []
    for y in range(H):
        for x in range(W):
            if m[y, x] and not seen[y, x]:
                st = [(y, x)]; seen[y, x] = True; ys = []; xs = []
                while st:
                    cy, cx = st.pop(); ys.append(cy); xs.append(cx)
                    for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= ny < H and 0 <= nx < W and m[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True; st.append((ny, nx))
                out.append((min(xs), min(ys), max(xs), max(ys), len(xs)))
    return out
print('(1) [X] fixed U2 replays: abspos children vs the ref (component bbox top-left, Chebyshev distance to the nearest ref child)')
for sec, t, f, fn in [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', yellow, 'yellow'), ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002', yellow, 'yellow'),
                      ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', teal, 'teal'), ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001', teal, 'teal'),
                      ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001', teal, 'teal'), ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', teal, 'teal')]:
    R = A(ref(sec, t)); Fx = A(Image.open(f'{P55}/android-paint-order-replay/{t.replace("/", "__")}-android-U2.png'))
    rc = [c for c in comps(f(R)) if c[4] >= 20]; fc = [c for c in comps(f(Fx)) if c[4] >= 20]
    d = [min((max(abs(c[0] - e[0]), abs(c[1] - e[1])) for e in fc), default=999) for c in rc]
    print(f'  {t.split("/")[-1]:46} {fn:6} ref children {len(rc):3}  fixed {len(fc):3}  displaced >2 px: {sum(x > 2 for x in d):3}  worst {max(d) if d else 0}')
print('(2) [K] zone extension x64-389 y64-159 (rows 3-6 item text): differing px vs wave54-final')
OK5 = f'{P55}/counter-suffix-rows-replay/ok-all'
for p in ('ios', 'android'):
    B = A(cap('wave54-final', 'css-counter-styles', 'counter-suffix', p))
    for name, a in [('ok-all (fixed)', A(Image.open(f'{OK5}/counter-suffix.{p}.png'))),
                    ('H-item-text-gone', A(Image.open(f'{SCR}/fakes/L5-H-item-text-gone/{p}-screenshots__wpt__css-counter-styles__counter-suffix.png')))]:
        print(f'  {p:8} {name:18} {int((a[64:160, 64:] != B[64:160, 64:]).any(axis=2).sum())} px → {"OK" if not (a[64:160, 64:] != B[64:160, 64:]).any() else "WRONG"}')
