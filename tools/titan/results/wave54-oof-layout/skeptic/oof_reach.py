#!/usr/bin/env python3
"""Skeptic (wave 54 L4) — an INDEPENDENT static reach census for OOF-android /
OOF-ios over the verbatim per-test IR. Re-implements the rule table from the
spec/plan text (not from the lane's Kotlin/Swift), then lists every document
in which ANY decision the unit can move is reachable:
  F  a FIXED box with no declared inset (absent or "auto")       [both natives]
  Eo a NEW establisher (contain layout|paint|strict|content, non-none filter /
     backdrop-filter, will-change filter|backdrop-filter|contain; box not
     itself absolute/fixed; not already a transform CB) with an out-of-flow
     DESCENDANT at any depth not already under a transform CB      [android]
  Eb a NEW establisher, not RELATIVE, with an out-of-flow DIRECT child [android: Box branch]
  Ef a NEW establisher with a FIXED descendant at any depth not already under a
     transform CB                                                 [ios]
  Es a NEW establisher with an ABSOLUTE descendant under a column-span:all box [ios]
Usage: oof_reach.py <run-dir>   (e.g. tools/titan/runs/wave53-final)"""
import json, sys, glob, os

INSETS = {"Top","Right","Bottom","Left","InsetBlockStart","InsetBlockEnd","InsetInlineStart","InsetInlineEnd"}

def ptype(props):
    # Last Position wins (any position property; uppercase string on the wire).
    t = None
    for p in props:
        if p["type"] == "Position" and isinstance(p["data"], str): t = p["data"].upper()
    return t or "STATIC"

def declares_inset(props):
    return any(p["type"] in INSETS and p["data"] != "auto" for p in props)

def contain_claims(d):
    toks = []
    if isinstance(d, list): toks = [str(x).upper() for x in d if isinstance(x, str)]
    elif isinstance(d, str): toks = d.upper().split()
    elif isinstance(d, dict): return d.get("layout") in (True, "true") or d.get("paint") in (True, "true")
    return any(t in ("LAYOUT","PAINT","STRICT","CONTENT") for t in toks)

def new_establisher(props):
    fires = False
    for p in props:
        t, d = p["type"], p["data"]
        if t == "Contain" and contain_claims(d): fires = True
        if t in ("Filter","BackdropFilter") and ((isinstance(d, list) and d) or isinstance(d, dict)): fires = True
        if t == "WillChange" and isinstance(d, list) and any(isinstance(h, dict) and h.get("name") in ("filter","backdrop-filter","contain") for h in d): fires = True
    return fires and ptype(props) not in ("ABSOLUTE","FIXED")

def transform_cb(props):
    for p in props:
        t, d = p["type"], p["data"]
        if t == "Transform" and isinstance(d, dict):
            if d.get("type") == "functions":
                if d.get("list"): return True
            else: return True
        if t in ("Translate","Rotate","Scale","Perspective") and isinstance(d, dict) and d.get("type") != "none": return True
        if t == "TransformStyle" and d == "PRESERVE_3D": return True
        if t == "WillChange" and isinstance(d, list) and any(isinstance(h, dict) and h.get("name") in ("transform","perspective") for h in d): return True
    return False

def is_spanner(props):
    return any(p["type"] == "ColumnSpan" and str(p["data"]).upper() == "ALL" for p in props)

def tree(doc):
    comps = doc["components"]
    by = {c["id"]: dict(c, kids=[]) for c in comps}
    roots = []
    for c in comps:
        par = (c.get("slot") or {}).get("parent")
        (by[par]["kids"] if par in by else roots).append(by[c["id"]])
    return roots

def census(path):
    doc = json.load(open(path))
    roots = tree(doc)
    hits = set()
    def walk(n, tf, ancestors_new):
        props = n.get("properties") or []
        pt = ptype(props)
        if pt == "FIXED" and not declares_inset(props): hits.add("F")
        ne = new_establisher(props) and not transform_cb(props)
        if ne and pt != "RELATIVE" and any(ptype(k.get("properties") or []) in ("ABSOLUTE","FIXED") for k in n["kids"]): hits.add("Eb")
        if pt in ("ABSOLUTE","FIXED") and ancestors_new and not tf: hits.add("Eo")
        if pt == "FIXED" and ancestors_new and not tf: hits.add("Ef")
        ctf = tf or transform_cb(props)
        for k in n["kids"]: walk(k, ctf, ancestors_new or ne)
    def span_walk(n, under_new, under_span):
        props = n.get("properties") or []
        if under_new and under_span and ptype(props) == "ABSOLUTE": hits.add("Es")
        ne = new_establisher(props)
        for k in n["kids"]: span_walk(k, under_new or ne, under_span or is_spanner(props))
    for r in roots: walk(r, False, False); span_walk(r, False, False)
    return hits

run = sys.argv[1]
files = sorted(glob.glob(os.path.join(run, "sections/*/per-test-ir/*.json")), key=os.path.basename)
print(f"documents {len(files)}")
andr, ios = [], []
for f in files:
    h = census(f)
    name = os.path.basename(f)[:-5]
    if h & {"F","Eo","Eb"}: andr.append((name, sorted(h & {"F","Eo","Eb"})))
    if h & {"F","Ef","Es"}: ios.append((name, sorted(h & {"F","Ef","Es"})))
print(f"ANDROID reach {len(andr)}")
for n, h in andr: print("  A", n, ",".join(h))
print(f"IOS reach {len(ios)}")
for n, h in ios: print("  I", n, ",".join(h))
