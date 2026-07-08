package com.styleconverter.test.style.speech

// Phase 10 facade — every CSS Speech (aural) property is parse-only on
// every platform target (Compose Android, SwiftUI iOS, Web). There is no
// mobile/desktop/web audio pipeline that renders CSS speech hints, so the
// applier is a no-op for all 27 properties. This file registers the
// property names on PropertyRegistry so the Phase 10 coverage matrix in
// testing/README.md shows `speech` as covered (for "parse and drop"
// semantics) rather than silently falling through to the legacy
// StyleApplier switch.
//
// SpeechExtractor + SpeechConfig already exist and build a SpeechConfig
// from a small subset (Speak / SpeakAs / PauseBefore / PauseAfter /
// VoiceFamily / VoicePitch / VoiceRate / VoiceVolume). The rest of the
// family lands here as registration-only — future work may extend
// SpeechExtractor if a voice-rendering pipeline is ever added.
//
// Parser-gap notes (see README-phase10.md):
//   * Volume / VoiceVolume share a VolumeValue sum type (keyword /
//     percentage / number / raw / global).
//   * SpeakAs accepts any whitespace-separated tokens unchecked.
//   * VoiceFamily splits on comma, no validation.
//   * VoiceRate / VoicePitch / VoiceRange / VoiceStress / SpeechRate /
//     Pitch are stored as raw trimmed strings — no keyword validation.
//   * VoiceBalance / PitchRange / Richness / Stress are plain numbers.
//   * Azimuth: 12 named positions + `behind` modifier + angle fallback.
//   * Elevation: 5 named positions or an angle (else null).
//   * Cue* only accept `none` or `url(...)`.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers all 27 CSS Speech IR property names under the `speech` owner.
 * The applier is a no-op on every platform target — these properties are
 * claimed here purely for coverage auditing.
 */
object SpeechRegistration {

    init {
        PropertyRegistry.migrated(
            // ---- volume + speak family ----
            "Volume", "Speak", "SpeakAs",
            // ---- pause / rest / cue + their before/after longhands ----
            "Pause", "PauseBefore", "PauseAfter",
            "Rest", "RestBefore", "RestAfter",
            "Cue", "CueBefore", "CueAfter",
            // ---- voice-* family (8) ----
            "VoiceFamily", "VoicePitch", "VoiceRange",
            "VoiceRate", "VoiceStress", "VoiceVolume",
            "VoiceBalance", "VoiceDuration",
            // ---- legacy aural properties (pre-CSS-3 Speech module) ----
            "Pitch", "PitchRange", "Richness", "Stress",
            "SpeechRate", "Azimuth", "Elevation",
            owner = "speech"
        )
    }
}
