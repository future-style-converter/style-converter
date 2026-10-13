#!/usr/bin/env python3
# tools/titan/results/wave55-android-paint-order/census.py <base.tsv[.gz]> <u1.tsv[.gz]> <full.tsv[.gz]>
#   e.g. python3 -B census.py census-base.tsv.gz census-u1.tsv.gz census-full.tsv.gz > census.out.txt
#
# Lane L6's OWN blast-radius census, from the JVM chain census (L6ChainCensusTest.kt.txt, run by census.sh) on three
# trees of the lane worktree: base 611c9f93 (no L6 unit), U1 (+ unit-U1 + unit-U3: U2 reverted) and full (U1 U2 U3).
# A component whose folded Compose modifier chain differs between two trees is one the unit changed; nothing else can
# paint differently (the units touch only the border paint path). A changed chain moves PIXELS only where something is
# drawn after the band in one order and before it in the other: content inner of the node (children, own text / inline
# runs, ::before / ::after pseudo boxes, list markers), the element's opacity group (U2 moves the band inside it), or an
# outline overlapping the band (the outline now paints after the band). A changed component with none of these is
# raster-identical by construction (the background still paints first, the band next, identical arithmetic).
# Two refinements read from the verbatim IR itself: an outline paints OUTSIDE the border box unless its offset is
# negative (OutlineApplier.drawStrokeRing: the ring spans [offset, offset + width] outward, a plain rect), so only a
# negative OutlineOffset can overlap the band; and a `Display: CONTENTS` box that ContentsUnboxing.isUnboxable accepts
# (static position, no pseudo payload) is unboxed by the renderer, which strips its Border* declarations
# (ContentsUnboxing.resolve → inheritableSubset; ContentsUnboxingTest pins it) — no band is painted on device. A
# contents box WITH pseudos or a non-static position keeps its box (isUnboxable false) and is counted like any other.
# The units change the band's position for EVERY band of their route, also where the folded element-class signature
# happens to read the same (the slow path's drawWithContent node keeps its class), so the changed sets are taken by
# route: U1 = every self-paint band, U2 = every legacy band; the signature diff must be a subset of them.
# The document-level behavioural may-move set is cross-checked against expectations.json lanes["L6-android-paint-order"]
# (captureCarriers, mayMove, sharedStems) and the ring-fence; a stem outside them is census GROWTH.
import csv
import glob
import gzip
import io
import json
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
EXP = os.path.join(HERE, '..', 'wave55-plan', 'expectations.json')
opn = lambda p: io.TextIOWrapper(gzip.open(p)) if p.endswith('.gz') else open(p)
base, u1, full = (list(csv.DictReader(opn(p), delimiter='\t')) for p in sys.argv[1:4])
key = lambda r: (r['sec'], r['stem'], r['id'])
B = {key(r): r for r in base}
U = {key(r): r for r in u1}
F = {key(r): r for r in full}
assert B.keys() == U.keys() == F.keys(), 'the three censuses must cover the same components'
print(f'components {len(B)} in {len({k[1] for k in B})} per-test IR documents (wave54-final, verbatim)')
errs = [k for k in B if B[k]['sig'].startswith('ERR')]
print(f'components whose JVM chain could not be built (same on all three trees: {all(F[k]["sig"] == B[k]["sig"] == U[k]["sig"] for k in errs)}): {len(errs)} {[k[2] for k in errs]}')


RUN = os.path.join(HERE, '..', '..', 'runs', 'wave54-final', 'sections')
IR = {}
PSEUDO = set()
for f in glob.glob(os.path.join(RUN, '*', 'per-test-ir', '*.json')):
    for c in json.load(open(f)).get('components', []):
        k = (f.split(os.sep)[-3], os.path.basename(f)[:-5], c['id'])
        IR[k] = {p['type']: p['data'] for p in c.get('properties', [])}
        if c.get('pseudos') is not None or c.get('selectors') or c.get('media'): PSEUDO.add(k)


def outline_inner(k):
    """True when the outline can overlap the band: a negative outline-offset (css-ui-4 §3.3)."""
    off = IR.get(k, {}).get('OutlineOffset')
    px = off.get('px') if isinstance(off, dict) else None
    return px is not None and px < 0


def contents(k):
    """ContentsUnboxing.isUnboxable: `display: contents`, static position, no pseudos / selectors / media → the box
    is unboxed and its Border* declarations stripped (no band on device)."""
    P = IR.get(k, {})
    return P.get('Display') == 'CONTENTS' and P.get('Position', 'STATIC') == 'STATIC' and k not in PSEUDO


def exposure(r, k=None):
    """What can paint over / under the band differently once its order changes (see the header)."""
    out = []
    if k is not None and contents(k): return []
    if int(r['kids']) > 0: out.append('child')
    if r['text'] == 'true': out.append('text')
    if r['runs'] == 'true': out.append('runs')
    if r['pseudos'] == 'true': out.append('pseudo')
    if r['marker'] == 'true': out.append('marker')
    if r['partialOpacity'] == 'true': out.append('opacity<1')
    if r['outline'] == 'true' and (k is None or outline_inner(k)): out.append('outline<0')
    return out


def delta(A, Z, label, route_selfpaint):
    sigch = {k for k in A if A[k]['sig'] != Z[k]['sig']}
    ch = [k for k in Z if Z[k]['band'] == 'true' and Z[k]['selfPaint'] == route_selfpaint]
    print(f'\n== {label}: {len(ch)} components whose band moves behind the content (route selfPaint={route_selfpaint})')
    print(f'  signature-changed {len(sigch)}; signature-changed outside the route (must be 0): {len(sigch - set(ch))}; '
          f'route members whose class signature reads the same (slow-path drawWithContent kept its class): {len(set(ch) - sigch)}')
    outer = [k for k in ch if Z[k]['outline'] == 'true' and not outline_inner(k)]
    cont = [k for k in ch if contents(k)]
    print(f'  outlines on the route: {len([k for k in ch if Z[k]["outline"] == "true"])} ({len(outer)} with offset >= 0: outside the border box, no band overlap)')
    print(f'  display:contents on the route (unboxed, borders stripped on device): {len(cont)} {[k[1] for k in cont]}')
    route = Counter((Z[k]['band'], Z[k]['selfPaint'], Z[k]['fast'], Z[k]['shape']) for k in ch)
    for (band, sp, fast, shape), n in route.most_common():
        print(f'  band={band} selfPaint={sp} fastPath={fast} outline-shape={shape}: {n}')
    nonband = [k for k in ch if Z[k]['band'] != 'true']
    print(f'  changed components WITHOUT a painted band (must be 0): {len(nonband)} {nonband[:5]}')
    trans = Counter((A[k]['sig'].split('>')[-1] if A[k]['sig'] else '', Z[k]['sig'].split('>')[-1] if Z[k]['sig'] else '') for k in ch)
    print(f'  innermost element before -> after (top 6): {trans.most_common(6)}')
    exp = defaultdict(list)
    for k in ch:
        e = exposure(Z[k], k)
        exp['inert (raster-identical by construction)' if not e else '+'.join(e)].append(k)
    for cls, ks in sorted(exp.items(), key=lambda kv: -len(kv[1])):
        print(f'  exposure {cls}: {len(ks)} components in {len({k[1] for k in ks})} documents')
    exposed = {k[1] for k in ch if exposure(Z[k], k)}
    return ch, exposed


ch1, ex1 = delta(B, U, 'U1 (base -> U1+U3)', 'true')
ch2, ex2 = delta(U, F, 'U2 (U1+U3 -> full)', 'false')
selfpaint = [k for k in ch1]
print(f'\nU1 self-paint components: {len(selfpaint)}; outline kinds {Counter(F[k]["shape"] for k in selfpaint)} '
      f'(generic = EllipticalCornerShape → androidx drawGenericBorder branch; 0 in the corpus means that branch is device-unexercised)')
for k in sorted(selfpaint):
    print(f'  {k[1]}  {k[2]}  exposure={exposure(F[k], k) or "inert"}')
gen_all = [k for k in F if F[k]['shape'] == 'generic']
print(f'self-paint radius-only (no band) components on the generic outline, not changed by L6: {len(gen_all)}')

e = json.load(open(EXP))
L = e['lanes']['L6-android-paint-order']
car = set(L['captureCarriers']['android'])
may = set(L['mayMove']['android'])
shared = set(L['sharedStems'])
ring = set(e['ringFenced']['captures']['android'])
union = set(e['unionCaptureCarriers']['android'])
covered = car | may | shared | ring
mine = ex1 | ex2
print(f'\n== cross-check against expectations.json (L6 carriers {len(car)} · mayMove {len(may)} · sharedStems {len(shared)} · ring-fenced {len(ring)} = {len(covered)} android stems)')
print(f'behavioural may-move (documents with a changed AND exposed component): U1 {len(ex1)} · U2 {len(ex2)} · union {len(mine)}')
print(f'  inside the plan\'s set: {len(mine & covered)}')
grow = sorted(mine - covered)
print(f'  GROWTH (exposed, outside carriers ∪ mayMove ∪ sharedStems ∪ ring-fence): {len(grow)}')
for s in grow:
    who = 'another lane\'s carrier' if s in union else 'nobody\'s carrier'
    ks = [k for k in ch1 + ch2 if k[1] == s and exposure(F[k], k)]
    print(f'    {s}  [{who}]  ' + '; '.join(f'{k[2]} {"+".join(exposure(F[k], k))}' for k in ks[:4]))
print(f'  plan stems with NO exposed changed component (conservative over-coverage): {len(covered - mine)}')
for s in sorted(car - mine):
    print(f'    carrier without an exposed changed component: {s}')
print(f'  plan carriers covered: {len(car & mine)}/{len(car)}')
