//
//  BackgroundPositionConfig.swift
//  StyleEngine/background — Phase 4.
//
//  Holds the parsed BackgroundPositionX and BackgroundPositionY values.
//  CSS lets you write `background-position: left center` shorthand,
//  which the Kotlin converter pre-expands to the two longhands we
//  consume here. Each axis is one of:
//    keyword  (TOP / RIGHT / BOTTOM / LEFT / CENTER)
//    length   ({px: N})
//    percent  (0..100)
//

import Foundation

// Per-axis value. Symmetric between X and Y.
enum BackgroundAxisPosition: Equatable {
    // Normalised keyword label, e.g. "LEFT". Applier maps to 0/0.5/1.
    case keyword(String)
    // Absolute pixels.
    case px(Double)
    // Percent 0..100; applier divides by 100 for UnitPoint math.
    case percent(Double)
}

struct BackgroundPositionConfig: Equatable {
    // Horizontal axis — nil when no BackgroundPositionX in IR.
    var x: BackgroundAxisPosition? = nil
    // Vertical axis — nil when no BackgroundPositionY in IR.
    var y: BackgroundAxisPosition? = nil

    // True when either axis was provided. Applier short-circuits on nil.
    var hasAny: Bool { x != nil || y != nil }
}
