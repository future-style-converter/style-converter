#!/usr/bin/env python3
"""tools/titan/results/wave54-hyphenate-character/l3-census.py — wave 54 lane L3: the lane's OWN blast-radius census for
U1 (converter) and U2 (natives) over the 1435 per-test IR documents of a run (default wave53-final), plus the
cross-check of the lane's whole carrier set against expectations.json lanes["L3-hyphenate-character"].

U1: every `hyphenate-character` on the wire — typed HyphenateCharacter or Generic passthrough — and the value the
    wave-54 reader would emit. The old reader's output was the declaration with ONE surrounding quote pair stripped,
    so the declaration is reconstructed as "<value>" and decoded per css-syntax-3 §4.3.5/§4.3.7 (a Python twin of
    CssStringParser.kt; the Kotlin is pinned by HyphenateCharacterPropertyParserTest on the same verbatim values).
U2: every component that would carry a NON-auto value after U1 (HyphenateCharacter is not inherited on either native:
    Compose's inherited set and iOS InheritedText.inheritedTypes omit it), and whether a pre-break can READ it —
    a text piece (`text` or meta.runs {text}) holding U+00AD, not `white-space: pre*`; Compose additionally needs a
    non-dictionary run (hyphens != AUTO), iOS reads it on the CF-dictionary fold too (hyphens AUTO + lang).
U3/U3b: read from u3-differential.out.txt (the in-process static extractor differential).
Usage: python3 l3-census.py [run]"""
import json, glob, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
SD = f'{ROOT}/tools/titan/runs/{RUN}/sections'

def decode(css):
    """css-syntax-3 §4.3.5 one string token, or None (mirrors CssStringParser.parse)."""
    s = css.strip()
    if not s or s[0] not in '"\'': return None
    q, out, i = s[0], [], 1
    nl = lambda c: c in '\n\r\f'
    while i < len(s):
        c = s[i]; i += 1
        if c == q: return ''.join(out) if i == len(s) else None
        if nl(c): return None
        if c == '\\':
            if i >= len(s): continue
            if nl(s[i]): i += 2 if s[i:i + 2] == '\r\n' else 1; continue
            m = re.match(r'[0-9a-fA-F]{1,6}', s[i:])
            if m:
                v = int(m.group(0), 16); i += len(m.group(0))
                if i < len(s) and s[i] in ' \t\n\r\f': i += 2 if s[i:i + 2] == '\r\n' else 1
                out.append('�' if v == 0 or 0xD800 <= v <= 0xDFFF or v > 0x10FFFF else chr(v)); continue
            out.append(s[i]); i += 1; continue
        out.append(c)
    return ''.join(out)

u1, u2c, u2i = [], {}, {}
docs = 0
for f in sorted(glob.glob(f'{SD}/*/per-test-ir/*.json')):
    docs += 1; stem = os.path.basename(f)[:-5]
    comps = json.load(open(f, encoding='utf-8'))['components']
    for c in comps:
        props = c.get('properties') or []
        new = None
        for p in props:
            if p['type'] == 'HyphenateCharacter':
                d = p['data']
                if d.get('type') == 'auto': new = None; continue
                old = d.get('value'); dec = decode('"' + old + '"'); new = dec if dec is not None else old
                u1.append((stem, c['id'], json.dumps(old, ensure_ascii=True), json.dumps(new, ensure_ascii=True), 'CHANGES' if new != old else 'byte-identical'))
            elif p['type'] == 'Generic' and p['data'].get('propertyName') == 'hyphenate-character':
                raw = p['data'].get('rawValue', ''); new = decode(raw)
                u1.append((stem, c['id'], 'Generic ' + json.dumps(raw), json.dumps(new, ensure_ascii=True), 'CHANGES (Generic -> typed)'))
        if new is None: continue
        texts = ([c['text']] if isinstance(c.get('text'), str) else []) + [r['text'] for r in ((c.get('meta') or {}).get('runs') or []) if 'text' in r]
        shy = [t for t in texts if '­' in t]
        ws = next((p['data'] for p in props if p['type'] == 'WhiteSpace'), '')
        hy = next((p['data'] for p in props if p['type'] == 'Hyphens'), 'MANUAL')
        if not shy or str(ws).upper().startswith('PRE') or str(ws).upper() == 'BREAK_SPACES':
            continue
        if str(hy).upper() == 'AUTO': u2i.setdefault(stem, []).append(f'{c["id"]} dictionary-fold ({len(shy)} shy pieces)')
        else:
            u2c.setdefault(stem, []).append(f'{c["id"]} {len(shy)} shy pieces')
            u2i.setdefault(stem, []).append(f'{c["id"]} {len(shy)} shy pieces')
    # iOS CF-dictionary fold also reaches hyphens:auto runs WITHOUT U+00AD (CFStringGetHyphenationLocation points)
    for c in comps:
        props = c.get('properties') or []
        # post-U1 non-auto value: typed string, or the Generic passthrough U1 turns into one
        hc = [p for p in props if (p['type'] == 'HyphenateCharacter' and p['data'].get('type') == 'string')
              or (p['type'] == 'Generic' and p['data'].get('propertyName') == 'hyphenate-character')]
        hy = next((p['data'] for p in props if p['type'] == 'Hyphens'), 'MANUAL')
        if hc and str(hy).upper() == 'AUTO' and (c.get('meta') or {}).get('lang') and stem not in u2i:
            u2i.setdefault(stem, []).append(f'{c["id"]} dictionary-fold (no shy; lang {(c.get("meta") or {}).get("lang")})')
        elif hc and str(hy).upper() == 'AUTO' and (c.get('meta') or {}).get('lang') and not any('dictionary' in x and c['id'] in x for x in u2i[stem]):
            u2i[stem].append(f'{c["id"]} dictionary-fold (no shy; lang {(c.get("meta") or {}).get("lang")})')

print(f'run {RUN}: {docs} per-test IR documents')
print(f'\n(1) U1 — hyphenate-character on the wire: {len(u1)} declarations in {len({r[0] for r in u1})} documents')
for r in u1: print(f'   {r[0]:<58} {r[2]:<28} -> {r[3]:<22} {r[4]}')
w1 = sorted({r[0] for r in u1 if r[4].startswith('CHANGES')})
print(f'   U1 wire carriers (value changes): {len(w1)}: {", ".join(s.split("__")[-1] for s in w1)}')
print(f'\n(2) U2-android reach (non-auto, U+00AD piece, not pre, not hyphens:auto): {len(u2c)} documents')
for k, v in u2c.items(): print(f'   {k:<58} {"; ".join(v)}')
print(f'    U2-ios reach (same + the CF-dictionary fold of hyphens:auto runs): {len(u2i)} documents')
for k, v in u2i.items(): print(f'   {k:<58} {"; ".join(v[:2])}{" …" if len(v) > 2 else ""} ({len(v)} components)')

E = json.load(open(f'{ROOT}/tools/titan/results/wave54-plan/expectations.json'))['lanes']['L3-hyphenate-character']
diff = open(os.path.join(os.path.dirname(__file__), 'u3-differential.out.txt')).read()
u3 = sorted({'wpt__' + m.replace('/', '__').replace('.html', '') for m in re.findall(r'^   css/(\S+)\s+\d+ br', diff.split('u3b:')[0], re.M)})
u3b = sorted({'wpt__' + m.replace('/', '__').replace('.html', '') for m in re.findall(r'^   css/(\S+)\s+\d+ br', diff.split('u3b:')[1], re.M)})
print(f'\n(3) U3 (static differential): {len(u3)} documents; U3b: {len(u3b)} documents')
wire = sorted(set(w1) | set(u3))
print(f'\n(4) cross-check vs expectations.json lanes["L3-hyphenate-character"]')
print(f'   wire carriers: mine {len(wire)} / plan {len(E["wireCarriers"])} — equal: {wire == sorted(E["wireCarriers"])}')
for p in ['web', 'ios', 'android']:
    mine = set(u3) - {'wpt__css-cascade__revert-layer-006', 'wpt__css-cascade__revert-val-001', 'wpt__css-cascade__revert-val-002'}
    if p == 'web': mine |= {s for s in w1 if not s.endswith('-005')}
    if p == 'android': mine |= set(u2c)
    if p == 'ios': mine |= set(u2i)
    print(f'   captures {p:<8} mine {len(mine):>2} / plan {len(E["captureCarriers"][p]):>2} — equal: {sorted(mine) == sorted(E["captureCarriers"][p])}'
          + ('' if sorted(mine) == sorted(E["captureCarriers"][p]) else f'  mine-only {sorted(mine - set(E["captureCarriers"][p]))} plan-only {sorted(set(E["captureCarriers"][p]) - mine)}'))
for u in ['U1', 'U2-android', 'U2-ios', 'U3', 'U3b']:
    ru = E['revertUnits'].get(u, {})
    print(f'   revertUnit {u:<11} captures web/ios/android {[len(ru.get("captures", {}).get(p, [])) for p in ("web", "ios", "android")]} wire {len(ru.get("wire", []))}')
