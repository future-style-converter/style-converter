import os
from PIL import Image
def strip(items, out, scale=1):
    ims=[]
    for p,crop in items:
        im=Image.open(p).convert('RGB').crop(crop)
        if scale!=1: im=im.resize((im.width*scale,im.height*scale),Image.NEAREST)
        ims.append(im)
    W=sum(i.width for i in ims)+6*len(ims); H=max(i.height for i in ims)
    o=Image.new('RGB',(W,H),(255,0,255)); x=0
    for i in ims: o.paste(i,(x,0)); x+=i.width+6
    o.save(out)
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
pp=lambda fid,f: f'pngs/{fid}/{f}'
strip([(f'{P}/positioned-parent-slot-replay/dir-style-02a-android-U1U2.png',(0,110,250,200)),(pp('L3-dir-row6-text+2y','android__dir-style-02a.png'),(0,110,250,200)),(pp('L3-dir-row7-text-vflip','android__dir-style-02a.png'),(0,110,250,200))],'looks/r2-L3-dir.png',scale=3)
strip([(pp('L3-bidi-rtl-run-order-mirrored','android__bidi__bidi-tab-001.png'),(0,60,150,250)),(pp('L3-bidi-yellow-boxes-erased','android__bidi__bidi-tab-001.png'),(0,60,150,250)),(pp('L3-stack-red-shown','android__anchor-position-multicol-nested-001.png'),(150,60,390,200)),(pp('L3-001-parent-erased','android__dynamic-offset-rtl-001.png'),(0,0,140,140)),(pp('L3-001-parent+3x','android__dynamic-offset-rtl-001.png'),(0,0,140,140))],'looks/r2-L3-other.png',scale=2)
strip([(pp('L2-fail-ink-sliver','android__abspos__abspos-autopos-htb-ltr.png'),(0,70,140,200)),(pp('L2-notch7','android__abspos__abspos-autopos-htb-ltr.png'),(0,70,140,200)),(pp('L2-radius-moved','android__backdrop-filter-nested-border-radius-clip.png'),(0,0,260,150)),(pp('L2-fail-ink-sliver','android__backdrop-filter-nested-border-radius-clip.png'),(0,0,260,150))],'looks/r2-L2.png',scale=2)
strip([(f'{P}/ua-heading-margin-replay/text-decoration-inset-005-ios-C.png',(0,120,390,220)),(pp('L4-C-line1-clipped-10px','ios__text-decoration-inset-005.png'),(0,120,390,220)),(pp('L4-C-line1-thirds-swapped','ios__text-decoration-inset-005.png'),(0,120,390,220))],'looks/r2-L4.png',scale=1)
strip([(pp('L5-C-ideographs-swapped-5-6','ios__counter-suffix.png'),(0,60,200,240)),(pp('L5-H-letters-swapped-3-4','ios__counter-suffix.png'),(0,60,200,240)),(pp('L5-T-row9-text-erased','ios__counter-suffix.png'),(0,60,200,300)),(pp('L5-I-line1-ideograph-is-line2s','ios__cjk-decimal__css3-counter-styles-005.png'),(190,90,340,160))],'looks/r2-L5.png',scale=2)
strip([(f'{P}/android-paint-order-replay/abspos__grid-abspos-staticpos-align-self-001-android-U2.png',(0,0,200,200)),(pp('L6-X-one-child-moved-3px','android__abspos__grid-abspos-staticpos-align-self-001.png'),(0,0,200,200))],'looks/r2-L6.png',scale=3)
