import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
ROW = pt.L5_ROW; OK = 'counter-suffix-rows-replay/ok-all'; d5 = pt._d5
def prof(a, k):
    L = [b for b in pt.l5_blobs(a, 30, 64, *ROW[k]) if b[1] - b[0] >= 4][0]; p = np.zeros(14); v = d5(a[ROW[k][0]:ROW[k][1], L[0]:L[0] + 14]).sum(axis=0).astype(float); p[:len(v)] = v; return p
c = lambda p, q: round(float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum())), 3)
r = ref('css-counter-styles', 'counter-suffix')
for l, a in [('ref', r), ('ok-web', pt.png(f'{OK}/counter-suffix.web.png')), ('ok-ios', pt.png(f'{OK}/counter-suffix.ios.png')), ('ok-android', pt.png(f'{OK}/counter-suffix.android.png')),
             ('H-letters-swapped-3-4 ios', fk('L5-H-letters-swapped-3-4', 'ios', 'counter-suffix'))]:
    m = [[c(prof(a, k), prof(r, j)) for j in (3, 4)] for k in (3, 4)]
    say(f'  [H] {l:28} row3 vs ref(row3,row4) {m[0]}  row4 vs ref(row3,row4) {m[1]}  → nearest: row3={3 if m[0][0] > m[0][1] else 4} row4={3 if m[1][0] > m[1][1] else 4}')
