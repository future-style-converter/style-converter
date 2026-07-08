//
//  UnsupportedSpeechConfig.swift
//  StyleEngine/speech — Phase 10.
//
//  CSS Speech module — aural rendering (volume, voice-*, pitch, rest,
//  cue, pause, azimuth, elevation, speak, speech-rate, richness,
//  stress). iOS has no CSS-speech integration: UIAccessibility's VO
//  engine is driven by accessibility labels, not CSS. Grouped identity
//  triplet per CLAUDE.md hand-off note.
//

import Foundation

struct UnsupportedSpeechConfig: Equatable {
    /// Raw string payload per IR property type — preserved for the
    /// coverage audit only; no downstream consumer.
    var rawByType: [String: String] = [:]
    /// True when any owned property was seen.
    var touched: Bool = false
}
