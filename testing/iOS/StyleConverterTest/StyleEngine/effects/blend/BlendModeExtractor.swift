//
//  BlendModeExtractor.swift
//  StyleEngine/effects/blend — Phase 4.
//
//  Parses MixBlendMode (single UPPERCASE string) and BackgroundBlendMode
//  (array of UPPERCASE strings). The string vocabulary is the CSS spec
//  list with underscores; we map to SwiftUI's BlendMode enum values.
//

import SwiftUI

enum BlendModeProperty {
    static let names: [String] = ["MixBlendMode", "BackgroundBlendMode"]
}

enum BlendModeExtractor {

    // Extract both in one pass; nil when neither property was present.
    static func extract(from properties: [IRProperty]) -> BlendModeConfig? {
        var cfg = BlendModeConfig()
        var touched = false
        for prop in properties {
            switch prop.type {
            case "MixBlendMode":
                if let s = prop.data.stringValue {
                    cfg.mix = mapBlend(s)
                    touched = true
                }
            case "BackgroundBlendMode":
                if case .array(let arr) = prop.data {
                    cfg.background = arr.compactMap { v in
                        guard let s = v.stringValue else { return nil }
                        return mapBlend(s)
                    }
                    touched = true
                }
            default:
                break
            }
        }
        return touched ? cfg : nil
    }

    // CSS blend-mode keyword (UPPERCASE, words underscore-separated) →
    // SwiftUI BlendMode. Unknown / unsupported values return nil so the
    // applier can leave the view unblended.
    //
    // Supported on SwiftUI (maps directly):
    //   NORMAL → .normal, MULTIPLY → .multiply, SCREEN → .screen,
    //   OVERLAY → .overlay, DARKEN → .darken, LIGHTEN → .lighten,
    //   COLOR_DODGE → .colorDodge, COLOR_BURN → .colorBurn,
    //   HARD_LIGHT → .hardLight, SOFT_LIGHT → .softLight,
    //   DIFFERENCE → .difference, EXCLUSION → .exclusion,
    //   HUE → .hue, SATURATION → .saturation,
    //   COLOR → .color, LUMINOSITY → .luminosity,
    //   PLUS_LIGHTER → .plusLighter, PLUS_DARKER → .plusDarker,
    //   SOURCE_ATOP → .sourceAtop,
    //   DESTINATION_OVER → .destinationOver,
    //   DESTINATION_OUT → .destinationOut
    // Unsupported on SwiftUI — returns nil:
    //   (none currently in Phase 4 fixture set)
    static func mapBlend(_ keyword: String) -> BlendMode? {
        switch keyword.uppercased() {
        case "NORMAL":           return .normal
        case "MULTIPLY":         return .multiply
        case "SCREEN":           return .screen
        case "OVERLAY":          return .overlay
        case "DARKEN":           return .darken
        case "LIGHTEN":          return .lighten
        case "COLOR_DODGE":      return .colorDodge
        case "COLOR_BURN":       return .colorBurn
        case "HARD_LIGHT":       return .hardLight
        case "SOFT_LIGHT":       return .softLight
        case "DIFFERENCE":       return .difference
        case "EXCLUSION":        return .exclusion
        case "HUE":              return .hue
        case "SATURATION":       return .saturation
        case "COLOR":            return .color
        case "LUMINOSITY":       return .luminosity
        case "PLUS_LIGHTER":     return .plusLighter
        case "PLUS_DARKER":      return .plusDarker
        case "SOURCE_ATOP":      return .sourceAtop
        case "DESTINATION_OVER": return .destinationOver
        case "DESTINATION_OUT":  return .destinationOut
        default:                 return nil
        }
    }
}
