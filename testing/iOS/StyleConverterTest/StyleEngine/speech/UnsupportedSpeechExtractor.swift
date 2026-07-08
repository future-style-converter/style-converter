//
//  UnsupportedSpeechExtractor.swift
//  StyleEngine/speech — Phase 10.
//
//  Owns all 27 CSS Speech property type names. The iOS accessibility
//  surface (VoiceOver) doesn't consume any of these — kept for audit
//  + parity with Android/Web sibling agents.
//

import Foundation

/// Registry ownership. Names mirror the files under
/// `app/irmodels/properties/speech/` with the `Property` suffix stripped.
enum UnsupportedSpeechProperty {
    /// Explicit, diff-auditable list. See the README-phase10 speech
    /// fixture for variant coverage.
    static let names: [String] = [
        // Volume + speak.
        "Volume", "Speak", "SpeakAs",
        // Pause / rest / cue — short + longhand pairs.
        "Pause", "PauseBefore", "PauseAfter",
        "Rest", "RestBefore", "RestAfter",
        "Cue", "CueBefore", "CueAfter",
        // Voice family.
        "VoiceFamily", "VoiceRate", "VoicePitch", "VoiceRange",
        "VoiceStress", "VoiceVolume", "VoiceDuration", "VoiceBalance",
        // Pitch / richness / stress / speech-rate.
        "Pitch", "PitchRange", "Richness", "Stress", "SpeechRate",
        // Positional — azimuth + elevation.
        "Azimuth", "Elevation",
    ]
    /// Set form for PropertyRegistry union.
    static var set: Set<String> { Set(names) }
}

enum UnsupportedSpeechExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedSpeechConfig? {
        var cfg = UnsupportedSpeechConfig()
        let owned = UnsupportedSpeechProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
