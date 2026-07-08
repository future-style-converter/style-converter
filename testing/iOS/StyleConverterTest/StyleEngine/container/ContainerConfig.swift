//
//  ContainerConfig.swift
//  StyleEngine/container — Phase 10.
//
//  container, container-name, container-type (CSS container queries).
//  SwiftUI has no container-query primitive — identity.
//

import Foundation

struct ContainerConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
