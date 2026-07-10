//
//  RootAlignment.swift
//  StyleConverterRuntime — fidelity wave 1.
//
//  Root-level self-alignment for capture surfaces. The web capture page
//  lays every top-level component out as an item whose `justify-self`
//  positions it inside the canvas column: grid-2col's standalone child
//  crops `b` (justify-self: center) and `c` (justify-self: end) render
//  centred / right-aligned in the 358px canvas on web, while the iOS
//  CaptureCanvas pinned everything `.topLeading` (0.798 / 0.786
//  iOS-web). The canvas can't reach the internal LayoutExtractor, so
//  this tiny public helper maps the component's inline-axis
//  self-alignment onto the SwiftUI frame Alignment the canvas needs.
//

import SwiftUI

public enum RootAlignment {

    /// Resolve the frame alignment for a top-level component: CSS
    /// `justify-self: center|end` moves the box along the inline axis;
    /// everything else (start/stretch/auto/absent) keeps the block-flow
    /// origin (top-leading). Vertical alignment stays `top` — the
    /// capture canvas hugs its content vertically so the block axis has
    /// no free space to distribute.
    public static func alignment(for component: IRComponent) -> Alignment {
        // Pull the layout aggregate; justify-self is the only field the
        // canvas positioning consumes today.
        let agg = LayoutExtractor.extract(from: component.properties)
        switch agg?.justifySelf {
        case .center:            return .top          // centre of the inline axis
        case .end, .selfEnd:     return .topTrailing  // inline-axis end
        default:                 return .topLeading   // CSS block-flow origin
        }
    }
}
