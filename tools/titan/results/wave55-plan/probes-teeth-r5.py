#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-teeth-r5.py — the ROUND-5 cases of the standing rejection set: BACKGROUND-ONLY paint
# (plan-skeptic-round-5.verify.md M1-M6; fix-r6.done.md). Loaded by probes-teeth.py run_all() after probes-teeth-r4.py (never
# run alone); every case is (lane, sections, fid, {(sec, plat, test): picture}, {(script, key): pre-registered verdict}).
#
# Why: rounds 1-4 closed fakes that move, erase, rearrange or add ink of the key's OWN colour / level in the key's OWN rows. Round
# 5's verifier painted where no read of a key looks — 50 % copies of the class ink (not strict green / not dark), black /
# #66ccff / #cccccc / yellow / iOS-grey ink in a colour the masks never test, lines in rows no XOR covers — and 704 fakes adding
# > 2 px, on 33 of 43 gating keys and 17 tally entries, passed (every finding stands on background-only paint). Round 6 closes the
# class GENERICALLY (geometry-gate.py's RESIDUAL read, residual-read.py; plus the L3 dyn magenta count ±2 for a hole of the
# parent's own colour, which equals the base and so no residual can see). The cases below pin EVERY such fake: each fake RUN of
# the verifier's sweep holding a > 2-px acceptance (346 runs), rebuilt with the verifier's own builders
# (plan-skeptic-round-5.fakes/added_ink5.py, copied verbatim from its fakes5.py) from its own records
# (plan-skeptic-round-5.verify.d/fakes5.L*.json, sha256-pinned below: a changed record is a refusal, not a silently different
# set), every capture the run faked included, each pre-registered FAIL on exactly the keys that accepted it — the gating keys, and
# for a tally entry counted picture-correct, every key of that entry. A fake adding ≤ 2 px is the named 2-px floor (PLAN §0,
# D39) and is not pinned here; probes-selfcheck.txt §T5 re-runs the verifier's generator over every lane and lists every one.
# Writes nothing itself: probes-teeth.py writes the fake runs under its --scratch dir.
import hashlib, importlib.util, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
_s = importlib.util.spec_from_file_location('added_ink5', os.path.join(HERE, 'plan-skeptic-round-5.fakes', 'added_ink5.py'))
a5 = importlib.util.module_from_spec(_s); _s.loader.exec_module(a5)
V5 = os.path.join(HERE, 'plan-skeptic-round-5.verify.d')
RECORDS = {   # the verifier's records, verbatim (plan-skeptic-round-5.verify.md §4)
    'L1': '95d96440f69fe2e55f8ed1d0f221fd5f7ed8ab69c3a20398e45e0e83a2072b08', 'L2': '65b3f666564e49dccf1b08b2b778deb1d432cb42e15f01b0dba29d93de7d1c47',
    'L3': 'b5df92cfd3cad5c8a20c9be1617d6d5e759aa5c7b960af1a82e18c253c5df119', 'L4': '10fbcedb27aa22ebcad632e4fca9d97db4c946ec11a51f0f32746cf057a83f4a',
    'L5': '0d0e0ef88edc1380071f9d98e92ec2056faf3caee70ed02265da1b1f46de36ac', 'L6': 'dc64edbf22c0c4cc11b1672ee436f2ea6cd2b7810051c29bd79e6931096d1de5'}
FLOOR = 2          # PLAN §0 / D39: a fake adding ≤ 2 px under a probe's own mask is the named floor, not pinned


def records(lane):
    """The verifier's per-zone records of one lane (refused when the bytes are not the pinned ones)."""
    p = os.path.join(V5, f'fakes5.{lane}.json')
    h = hashlib.sha256(open(p, 'rb').read()).hexdigest()
    if h != RECORDS[lane]:
        raise SystemExit(f'probes-teeth-r5: {p} sha256 {h[:16]}… is not the pinned verifier record {RECORDS[lane][:16]}…')
    return json.load(open(p))


def pinned(lane):
    """[(run name, [records of that fake run], [the > 2-px accepted records])] in the verifier's order."""
    recs = records(lane)
    acc = lambda r: r['added'] > FLOOR and (r['gatingPassQ'] or r['tallyPass'])
    out, seen = [], {}
    for r in recs:
        run = os.path.basename(r['run'])
        if run not in seen:
            seen[run] = len(out); out.append((run, [], []))
        out[seen[run]][1].append(r)
        if acc(r):
            out[seen[run]][2].append(r)
    return [o for o in out if o[2]]


def cases(pt, lane):
    """The round-5 (background-only paint) cases of one lane; pt is the loaded probes-teeth module (unused: added_ink5 loads it)."""
    if lane not in RECORDS:
        return []
    exp = json.load(open(os.path.join(HERE, 'expectations.json')))
    tally = {e['cell']: [tuple(k.split(' ', 1)) for k in e['keys']] for part in ('full', 'part', 'paintOrderOnly')
             for e in exp['pictureCorrectTally'].get(part, [])}
    groups = {(ln, gid): (secs, repl) for ln, secs, gid, repl in a5.groups()}
    zcache, out = {}, []
    for run, rs, accepted in pinned(lane):
        m = re.search(r'-z(\d+)c(\d+)-(.+)$', run)
        j, ci, variant = int(m[1]), int(m[2]), m[3]
        secs, repl0 = groups[(lane, rs[0]['group'])]
        repl = dict(repl0)
        for r in rs:                                     # every capture this fake run changed, as the verifier built it
            x = (r['sec'], r['plat'], r['test'])
            if (r['group'], x) not in zcache:
                F = repl0[x]; B = a5.pt.cap('wave54-final', x[0], x[2], x[1])
                zs = []
                for zname, boxes, keys in a5.zones_of(*x):
                    z = a5.Zone(F, B, boxes); zs.append([a5.K(z, c) for c in z.classes])
                zcache[(r['group'], x)] = zs
            ks = zcache[(r['group'], x)][j]
            assert tuple(int(t) for t in ks[ci].c) == tuple(r['class']), (run, x)
            repl[x] = a5.build(ks[ci], variant)
        want = {}
        for r in accepted:
            for q in r['gatingPassQ']:
                want[tuple(q.split(' ', 1))] = 'FAIL'
            for cell in r['tallyPass']:
                # stage 1: a cell whose keys the regeneration WITHDREW (a reverted unit's) has no tally entry any more — it gates
                # nothing, so the verifier's pre-registered FAIL on it is not asserted (PLAN §10 item 15; run_all drops withdrawn keys)
                for k in tally.get(cell, []):
                    want[k] = 'FAIL'
        out.append((lane, secs, run, repl, want))
    return out
