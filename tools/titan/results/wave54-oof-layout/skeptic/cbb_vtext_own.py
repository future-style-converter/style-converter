#!/usr/bin/env python3
"""Skeptic (wave 54 L4) — the vertical-text reader at the RIGHT level.
VerticalUprightTextFlow reads LocalContainingBlock.heightPx inside the text-
bearing component's OWN provider (ComponentRenderer publishes
childContainingBlock around RenderComponentContent), i.e. the block the
component establishes, not the one it is laid out in. List every text-bearing
node under a vertical writing mode (own or inherited) whose OWN block height
can differ under CBB-android: own definite Height (px / font-relative / expr,
or % of a parent that moved), a non-zero block-axis band, content-box.
Separately mark runs that can take the UPRIGHT path at all (a non-ASCII code
point, or text-orientation: upright). Usage: cbb_vtext_own.py <run-dir>"""
import json, sys, glob, os
sys.path.insert(0, os.path.dirname(__file__))
def last(props, *types):
    v = None
    for p in props:
        if p["type"] in types: v = p["data"]
    return v
def nonzero(d):
    if isinstance(d, dict):
        if isinstance(d.get("px"), (int, float)): return d["px"] != 0
        return "expr" in d or isinstance(d.get("original"), dict)
    return False
def definite(d):
    return isinstance(d, dict) and (isinstance(d.get("px"), (int, float)) or "expr" in d or isinstance(d.get("original"), dict) or d.get("type") == "percentage")
run = sys.argv[1]
hits = []
for f in sorted(glob.glob(os.path.join(run, "sections/*/per-test-ir/*.json")), key=os.path.basename):
    doc = json.load(open(f))
    comps = doc["components"]; by = {c["id"]: c for c in comps}
    def chain(c):
        while c is not None:
            yield c
            par = (c.get("slot") or {}).get("parent"); c = by.get(par)
    for c in comps:
        text = c.get("text") or "".join(r.get("text", "") for r in (c.get("runs") or []) if isinstance(r, dict))
        if not text.strip(): continue
        wm = next((last(a.get("properties") or [], "WritingMode") for a in chain(c) if last(a.get("properties") or [], "WritingMode") is not None), None)
        if not (isinstance(wm, str) and (wm.startswith("VERTICAL") or wm.startswith("SIDEWAYS"))): continue
        props = c.get("properties") or []
        bs = last(props, "BoxSizing")
        band = any(nonzero(last(props, t)) for t in ("PaddingTop","PaddingBottom","BorderTopWidth","BorderBottomWidth","PaddingBlockStart","PaddingBlockEnd"))
        if definite(last(props, "Height", "BlockSize")) and band and not (isinstance(bs, str) and bs.upper() == "BORDER_BOX"):
            to = next((last(a.get("properties") or [], "TextOrientation") for a in chain(c) if last(a.get("properties") or [], "TextOrientation") is not None), None)
            upright = any(ord(ch) > 0x7f for ch in text) or (isinstance(to, str) and to.upper() == "UPRIGHT")
            hits.append((os.path.basename(f), c["id"], wm, to, "UPRIGHT-CAPABLE" if upright else "ascii-rotated", repr(text[:20])))
print(f"own-level vertical-text readers: {len(hits)}")
for h in hits: print(" ", *h)
