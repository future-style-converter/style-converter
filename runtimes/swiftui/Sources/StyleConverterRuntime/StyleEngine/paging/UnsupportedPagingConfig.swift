//
//  UnsupportedPagingConfig.swift
//  StyleEngine/paging — Phase 10.
//
//  Break-*/page-break-*/margin-break — paginated-media only. iOS has
//  no paginated layout pipeline.
//

import Foundation

struct UnsupportedPagingConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
