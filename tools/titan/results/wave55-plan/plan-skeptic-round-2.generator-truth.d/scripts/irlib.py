# gt2 own IR reader: loads every per-test IR document of a run, resolves slot.parent chains,
# and merges inherited types the way InheritedText.merge does (own wins; inherited fills undeclared types).
import json, os, glob
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
INHERITED={"FontFamily","FontSize","FontWeight","FontStyle","FontStretch","LetterSpacing","LineHeight","WordSpacing",
 "TextAlign","TextTransform","TextIndent","WhiteSpace","TabSize","Direction","WritingMode","Color","Visibility","Cursor",
 "ListStyleType","ListStylePosition","ListStyleImage","ListStyle","Quotes","TextShadow","OverflowWrap","WordWrap","WordBreak",
 "Hyphens","TextEmphasisStyle","TextEmphasisColor","TextEmphasisPosition","RubyAlign","RubyPosition","RubyMerge","RubyOverhang",
 "CaptionSide","BorderCollapse","BorderSpacing","EmptyCells","Orphans","Widows","AccentColor"}
def docs(run='wave54-final'):
    for f in sorted(glob.glob(f'{ROOT}/tools/titan/runs/{run}/sections/*/per-test-ir/*.json')):
        sec=f.split('/sections/')[1].split('/')[0]
        st=os.path.basename(f)[:-5]
        yield sec, st, json.load(open(f))
def props(c):
    return c.get('properties') or []
def last(c,t):
    v=None
    for p in props(c):
        if p['type']==t: v=p['data']
    return v
def has(c,t): return any(p['type']==t for p in props(c))
class Doc:
    def __init__(self,d):
        self.comps=d['components']; self.byid={c['id']:c for c in self.comps}
        self.kids={}
        for c in self.comps:
            par=(c.get('slot') or {}).get('parent')
            self.kids.setdefault(par,[]).append(c)
    def parent(self,c):
        par=(c.get('slot') or {}).get('parent'); return self.byid.get(par)
    def chain(self,c):
        out=[]; x=c
        while x is not None: out.append(x); x=self.parent(x)
        return out
    def eff(self,c,t):
        """(value, depth) of the winning declaration: own last, else nearest ancestor's (inherited types only)."""
        for i,x in enumerate(self.chain(c)):
            if has(x,t):
                if i>0 and t not in INHERITED: return None,None
                return last(x,t), i
        return None,None
