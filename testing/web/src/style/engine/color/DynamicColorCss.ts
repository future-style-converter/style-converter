// DynamicColorCss.ts — reconstructs a CSS color expression from the IR payload
// of a dynamic color (color-mix / light-dark / relative / currentColor / var).
// Browsers natively understand every one of these CSS functions, so our job is
// just string assembly.  Used by BackgroundColorApplier, ColorApplier, etc.

import type { ColorValue } from '../core/types/ColorValue';
import { toCssColor } from '../core/types/ColorValue';

// Serialise a ColorValue to CSS, preferring reconstruction of dynamic shapes.
// Static sRGB falls through to the shared `toCssColor` which emits rgba(...).
export function colorToCss(v: ColorValue): string {
  if (v.kind !== 'dynamic') return toCssColor(v);                     // srgb/unknown path handled there
  return dynamicRawToCss(v.raw);                                      // complex dynamic reconstruction
}

// Translate the 'original' payload of a dynamic color into a CSS fragment.
function dynamicRawToCss(raw: unknown): string {
  if (typeof raw === 'string') return raw;                            // bare 'currentColor' or pre-formed fragment
  if (!raw || typeof raw !== 'object') return 'currentColor';         // safe fallback
  const obj = raw as Record<string, unknown>;
  switch (obj.type) {                                                 // discriminate on IR tag
    case 'color-mix':     return colorMixToCss(obj);                  // color-mix(in <space>, c1 p1%, c2 p2%)
    case 'light-dark':    return lightDarkToCss(obj);                 // light-dark(<light>, <dark>)
    case 'relative':      return relativeToCss(obj);                  // rgb(from red calc(r-50) g b)
    case 'var':           return varToCss(obj);                       // var(--name[, fallback])
    default:              return 'currentColor';                      // unknown shape
  }
}

// color-mix(in <space>, <c1> <p1>%?, <c2> <p2>%?) — both percentages optional.
function colorMixToCss(obj: Record<string, unknown>): string {
  const space = String(obj.colorSpace ?? 'srgb');                     // default space per spec is srgb
  const c1 = String(obj.color1 ?? 'currentColor');                    // first operand color
  const c2 = String(obj.color2 ?? 'currentColor');                    // second operand color
  const p1 = obj.percent1;                                            // may be number or absent
  const p2 = obj.percent2;                                            // may be number or absent
  const left = p1 !== undefined ? `${c1} ${p1}%` : c1;                // append % only if provided
  const right = p2 !== undefined ? `${c2} ${p2}%` : c2;               // same for second operand
  return `color-mix(in ${space}, ${left}, ${right})`;                 // final CSS expression
}

// light-dark(<light>, <dark>) — renders lightColor/darkColor under color-scheme.
function lightDarkToCss(obj: Record<string, unknown>): string {
  const light = String(obj.lightColor ?? 'currentColor');             // light-mode color
  const dark  = String(obj.darkColor  ?? 'currentColor');             // dark-mode color
  return `light-dark(${light}, ${dark})`;                             // spec syntax
}

// relative colors: rgb(from <base> <r> <g> <b>) / hsl(from ...) / etc.
function relativeToCss(obj: Record<string, unknown>): string {
  const fn   = String(obj.function ?? 'rgb');                         // color function: rgb/hsl/oklch/...
  const base = String(obj.baseColor ?? 'currentColor');               // reference color
  const comps = Array.isArray(obj.components) ? obj.components : [];  // per-channel expressions
  const joined = comps.map((c) => String(c)).join(' ');               // space-separated CSS-L4 syntax
  return `${fn}(from ${base} ${joined})`;                             // assembled expression
}

// var(--name[, fallback]) — IR may use {name, fallback} or {expr}.
function varToCss(obj: Record<string, unknown>): string {
  if (typeof obj.expr === 'string') return obj.expr;                  // pre-formed var(...)
  const name = String(obj.name ?? '--unknown');                       // custom-property name
  const fb   = obj.fallback;                                          // optional default
  return fb !== undefined ? `var(${name}, ${String(fb)})` : `var(${name})`;
}
