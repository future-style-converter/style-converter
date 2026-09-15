#!/usr/bin/env python3
# measure-shadow-sigma.py — wave 50 lane B8, BACKLOG queue 6(c).
#
# WHY: queue 6(c) ("Android box-shadow ~2.4x over-blur") had to be settled by
# measuring the Gaussian standard deviation each platform actually paints,
# against the css-backgrounds-3 6.1.2 rule that a blur radius `r` means a
# Gaussian with sigma = r/2 (Blink: skia_utils BlurRadiusToStdDev). This
# script does that on the COMMITTED baselines, so the claim is reproducible
# from the repo alone with no device.
#
# HOW: a CSS box-shadow is a Gaussian blur of a solid (rounded) rectangle, so
# its coverage field is separable —
#     f(x,y) = A * [P((x-xL)/s) - P((x-xR)/s)] * [P((y-yT)/s) - P((y-yB)/s)]
# with P the standard normal CDF. The capture composites that coverage over
# the harness ground (26,26,46) at the shadow colour's alpha, so the coverage
# is recovered per pixel as (px - ground) / ((shadow - ground) * alpha) on the
# channel with the largest |shadow - ground|. Fitting (s, xL, xR, yT, yB, A)
# by least squares over every pixel OUTSIDE the element's own opaque box and
# the harness's white label glyphs (both masked by a brightness threshold plus
# a dilation, since either would otherwise dominate the residual) returns the
# device sigma directly. An inset shadow is the same field complemented
# (css-backgrounds-3 6.1.3: the shadow is drawn inside the padding box) and is
# fitted over the box interior instead.
#
# Usage: python3 tools/titan/results/wave50-B8/measure-shadow-sigma.py
# Requires numpy + scipy + pillow (host tooling only; nothing in the pipeline
# depends on this file).
import json, math, os, sys
import numpy as np
from PIL import Image
from scipy.special import ndtr
from scipy.optimize import least_squares
from scipy.ndimage import binary_dilation

# …/tools/titan/results/wave50-B8/<file> → repo root is five levels up.
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..'))
BASE = os.path.join(ROOT, 'tools', 'visual', 'baseline')
GROUND = np.array([26., 26., 46.])          # the harness capture ground
PLATFORMS = ('web', 'Android', 'iOS')

def load(plat, comp):
    return np.array(Image.open(os.path.join(BASE, f'{plat}__{comp}.png')).convert('RGB')).astype(float)

def coverage(a, shadow, alpha):
    den = np.array(shadow, float) - GROUND
    ch = int(np.argmax(np.abs(den)))        # most-separated channel = best SNR
    return (a[:, :, ch] - GROUND[ch]) / (den[ch] * alpha), ch

def fit_outset(comp, shadow, alpha, box, offset, sigma_target, dilate=4, bright=110):
    """box = (xL,yT,xR,yB) of the element's border box in capture px."""
    out = {}
    for plat in PLATFORMS:
        a = load(plat, comp); h, w, _ = a.shape
        occl = binary_dilation(a.mean(2) > bright, np.ones((dilate*2+1,)*2, bool))
        f, ch = coverage(a, shadow, alpha)
        Y, X = np.mgrid[0:h, 0:w].astype(float)
        m = ~occl
        xs, ys, fs = X[m], Y[m], f[m]
        p0 = [sigma_target, box[0]+offset[0], box[2]+offset[0], box[1]+offset[1], box[3]+offset[1], 1.0]
        def res(p):
            s, xL, xR, yT, yB, A = p
            return A*(ndtr((xs-xL)/s)-ndtr((xs-xR)/s))*(ndtr((ys-yT)/s)-ndtr((ys-yB)/s)) - fs
        sol = least_squares(res, p0, bounds=([0.3]+[-300]*4+[0.2], [40]+[900]*4+[4.0]))
        s, xL, xR, yT, yB, A = sol.x
        out[plat] = dict(sigma=round(float(s), 3), amplitude=round(float(A), 3),
                         rms=round(float(math.sqrt((sol.fun**2).mean())), 5),
                         rect=[round(float(v), 1) for v in (xL, yT, xR, yB)], channel=ch)
    return out

def fit_inset(comp, shadow, alpha, box, sigma_target, fill_rgb, radius=0):
    """Inset shadow: css-backgrounds-3 6.1.3 paints it INSIDE the padding box,
    so the field composites over the element's own fill, not the harness
    ground, and the coverage is the COMPLEMENT of the blurred box silhouette.
    Fitted over the box interior with the corner squares masked out (a corner
    radius is not expressible in the separable model and would bias sigma)."""
    out = {}
    for plat in PLATFORMS:
        a = load(plat, comp)
        x0, y0, x1, y1 = box
        sub = a[y0:y1, x0:x1]
        hh, ww, _ = sub.shape
        # The element's fill is the fixture's declared background — NOT the
        # brightest interior pixel, which is a harness label glyph.
        fill = np.array(fill_rgb, float)
        den = (np.array(shadow, float) - fill)
        ch = int(np.argmax(np.abs(den)))
        f = (sub[:, :, ch] - fill[ch]) / (den[ch] * alpha)
        Y, X = np.mgrid[0:hh, 0:ww].astype(float)
        # Keep only pixels that lie on the fill→shadow ramp (t·fill for some
        # t): anything else is a label glyph or an antialiased border pixel.
        t = (sub[:, :, ch] / fill[ch])[:, :, None]
        on_ramp = (np.abs(sub - t * fill).max(2) <= 12)
        m = on_ramp & ~(((X < radius) | (X >= ww - radius)) & ((Y < radius) | (Y >= hh - radius)))
        xs, ys, fs = X[m], Y[m], f[m]
        p0 = [sigma_target, 0.0, float(ww), 0.0, float(hh), 1.0]
        def res(p):
            s, xL, xR, yT, yB, A = p
            inside = (ndtr((xs-xL)/s)-ndtr((xs-xR)/s))*(ndtr((ys-yT)/s)-ndtr((ys-yB)/s))
            return A*(1.0-inside) - fs
        sol = least_squares(res, p0, bounds=([0.3]+[-300]*4+[0.2], [40]+[900]*4+[4.0]))
        out[plat] = dict(sigma=round(float(sol.x[0]), 3), amplitude=round(float(sol.x[5]), 3),
                         rms=round(float(math.sqrt((sol.fun**2).mean())), 5),
                         samples=int(m.sum()), channel=ch)
    return out

if __name__ == '__main__':
    report = {
        '_note': 'wave 50 lane B8 — device sigma measured on the committed baselines; '
                 'css-backgrounds-3 6.1.2 target sigma = blur/2.',
        'measurements': {}
    }
    # visual-test.json Shadow_Simple: `5px 5px 10px rgba(0,0,0,.3)`, box 16,16-66,56.
    report['measurements']['023_Shadow_Simple'] = {
        'css': '5px 5px 10px rgba(0,0,0,0.3)', 'sigmaTarget': 5.0, 'wellConditioned': False,
        'caveat': 'the 5px/5px offset parks most of the field under the opaque box; '
                  'the visible sliver leaves sigma and amplitude correlated',
        'fit': fit_outset('023_Shadow_Simple', (0, 0, 0), 0.30, (16, 16, 66, 56), (5, 5), 5.0)}
    # Shadow_Colored: `0 4px 15px rgba(52,152,219,.5)` — the conditioned case.
    report['measurements']['024_Shadow_Colored'] = {
        'css': '0 4px 15px rgba(52,152,219,0.5)', 'sigmaTarget': 7.5, 'wellConditioned': True,
        'fit': fit_outset('024_Shadow_Colored', (52, 152, 219), 0.50, (16, 16, 66, 56), (0, 4), 7.5)}
    # Edge_InsetRoundShadow: `inset 0 0 20px rgba(0,0,0,.5)`, radius 20px.
    report['measurements']['107_Edge_InsetRoundShadow'] = {
        'css': 'inset 0 0 20px rgba(0,0,0,0.5)', 'sigmaTarget': 10.0, 'wellConditioned': False,
        'caveat': 'corner squares masked (radius 20px); the fill is read from the capture, '
                  'so a platform that paints the fill differently shifts the amplitude, not sigma',
        'fit': fit_inset('107_Edge_InsetRoundShadow', (0, 0, 0), 0.50, (16, 16, 66, 66), 10.0,
                          fill_rgb=(243, 156, 18), radius=20)}
    print(json.dumps(report, indent=1))
