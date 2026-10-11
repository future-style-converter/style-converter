import sys, os
from PIL import Image
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
REFS=f'{R}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
P=f'{R}/tools/titan/results/wave55-plan'
def cap(run,sec,test,plat):
    d={'web':'screenshots','ios':'ios-screenshots','android':'android-screenshots'}[plat]
    return f'{R}/tools/titan/runs/{run}/sections/{sec}/{d}/wpt__{sec}__{test.replace("/","__")}.png'
def ref(sec,test): return f'{REFS}/{sec}/{test.replace("/","__")}.png'
def strip(paths, out, crop=None, scale=1):
    ims=[Image.open(p).convert('RGB') for p in paths]
    if crop: ims=[i.crop(crop) for i in ims]
    if scale!=1: ims=[i.resize((i.width*scale,i.height*scale),Image.NEAREST) for i in ims]
    W=sum(i.width for i in ims)+6*len(ims); H=max(i.height for i in ims)
    o=Image.new('RGB',(W,H),(255,0,255)); x=0
    for i in ims: o.paste(i,(x,0)); x+=i.width+6
    o.save(out)
L=sys.argv[1]
if L=='L3':
    strip([ref('selectors','dir-style-02a'), cap('wave54-final','selectors','dir-style-02a','web'), f'{P}/positioned-parent-slot-replay/dir-style-02a-android-U1U2.png'],'looks/dir.png',crop=(0,100,390,260),scale=2)
    strip([ref('css-text','bidi/bidi-tab-001'), cap('wave54-final','css-text','bidi/bidi-tab-001','web'), f'{P}/positioned-parent-slot-replay/bidi-tab-001-android-U2.png' if os.path.exists(f'{P}/positioned-parent-slot-replay/bidi-tab-001-android-U2.png') else f'{P}/positioned-parent-slot-replay/bidi-tab-001-android-U1U2.png'],'looks/bidi.png',crop=(0,0,390,280))
    strip([ref('css-anchor-position','anchor-position-multicol-nested-001'), cap('wave54-final','css-anchor-position','anchor-position-multicol-nested-001','web')],'looks/stack.png',crop=(0,0,390,300))
    strip([ref('css-writing-modes','dynamic-offset-rtl-001'), cap('wave54-final','css-writing-modes','dynamic-offset-rtl-001','web'),cap('wave54-final','css-writing-modes','dynamic-offset-rtl-001','android')],'looks/dyn.png',crop=(0,0,390,200))
if L=='L5':
    strip([ref('css-counter-styles','counter-suffix'), f'{P}/counter-suffix-rows-replay/ok-all/counter-suffix.ios.png', f'{P}/counter-suffix-rows-replay/ok-all/counter-suffix.android.png'],'looks/cs.png',crop=(0,0,390,310),scale=2)
    strip([ref('css-counter-styles','cjk-decimal/css3-counter-styles-005'), f'{P}/counter-suffix-rows-replay/ok-all/cjk-decimal__css3-counter-styles-005.ios.png', f'{P}/counter-suffix-rows-replay/ok-all/cjk-decimal__css3-counter-styles-005.android.png'],'looks/c5.png',crop=(0,0,390,200))
if L=='L2':
    strip([ref('css-flexbox','abspos/abspos-autopos-htb-ltr'), cap('wave54-probe','css-flexbox','abspos/abspos-autopos-htb-ltr','android'), ref('filter-effects','backdrop-filter-nested-border-radius-clip'), cap('wave54-probe','filter-effects','backdrop-filter-nested-border-radius-clip','android'),ref('filter-effects','backdrop-filter-nested-border-radius-clip-4'), cap('wave54-probe','filter-effects','backdrop-filter-nested-border-radius-clip-4','android')],'looks/l2.png',crop=(0,0,280,220))
if L=='L6':
    strip([ref('css-flexbox','abspos/flex-abspos-staticpos-align-self-safe-001'), f'{P}/android-paint-order-replay/abspos__flex-abspos-staticpos-align-self-safe-001-android-U2.png', ref('css-flexbox','abspos/flex-abspos-staticpos-align-self-safe-002'), f'{P}/android-paint-order-replay/abspos__flex-abspos-staticpos-align-self-safe-002-android-U2.png'],'looks/l6.png',crop=(0,0,390,300))
