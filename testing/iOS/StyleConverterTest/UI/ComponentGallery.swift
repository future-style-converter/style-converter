//
//  ComponentGallery.swift
//  StyleConverterTest
//
//  Browsable gallery of all IR components, mirroring the web
//  `ComponentGallery.tsx`: search + pagination + expandable props.
//

import SwiftUI

private let pageSize = 50

struct ComponentGallery: View {
    let document: IRDocument
    @State private var searchQuery = ""
    @State private var showNested = true
    @State private var page = 0

    private var allComponents: [IRComponent] {
        let base = showNested ? flatten(document.components) : document.components
        guard !searchQuery.isEmpty else { return base }
        let q = searchQuery.lowercased()
        return base.filter { $0.name.lowercased().contains(q) || $0.id.lowercased().contains(q) }
    }

    private var pagedComponents: [IRComponent] {
        let start = page * pageSize
        let end   = min(start + pageSize, allComponents.count)
        return start < end ? Array(allComponents[start..<end]) : []
    }

    private var totalPages: Int {
        max(1, Int(ceil(Double(allComponents.count) / Double(pageSize))))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().background(Color.white.opacity(0.1))

            ScrollView {
                VStack(spacing: 12) {
                    statsBar
                    paginationBar
                    ForEach(Array(pagedComponents.enumerated()), id: \.element.id) { idx, comp in
                        ComponentCard(
                            component: comp,
                            index: page * pageSize + idx
                        )
                    }
                }
                .padding(16)
            }
        }
        .onChange(of: searchQuery) { _ in page = 0 }
        .onChange(of: showNested) { _ in page = 0 }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("Style Converter")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                Text("iOS Testing")
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
                Spacer()
            }
            TextField("", text: $searchQuery, prompt: Text("Search components…").foregroundColor(.gray))
                .textFieldStyle(.plain)
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.05))
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )
                .cornerRadius(6)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
        }
        .padding(12)
        .background(Color.black.opacity(0.3))
    }

    // MARK: - Stats

    private var statsBar: some View {
        HStack {
            HStack(spacing: 16) {
                statItem(label: "top-level", value: document.components.count)
                statItem(label: showNested ? "total" : "filtered", value: allComponents.count)
            }
            Spacer()
            Toggle(isOn: $showNested) {
                Text("Include nested")
                    .font(.system(size: 13))
                    .foregroundColor(.gray)
            }
            .toggleStyle(.switch)
            .tint(.blue)
            .fixedSize()
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
    }

    private func statItem(label: String, value: Int) -> some View {
        HStack(spacing: 4) {
            Text("\(value)")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.9))
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.gray)
        }
    }

    // MARK: - Pagination

    private var paginationBar: some View {
        HStack(spacing: 8) {
            pageButton("First", disabled: page == 0) { page = 0 }
            pageButton("Prev",  disabled: page == 0) { page = max(0, page - 1) }
            Text(paginationInfo)
                .font(.system(size: 12))
                .foregroundColor(.gray)
                .padding(.horizontal, 8)
            pageButton("Next", disabled: page >= totalPages - 1) { page = min(totalPages - 1, page + 1) }
            pageButton("Last", disabled: page >= totalPages - 1) { page = totalPages - 1 }
        }
        .padding(12)
        .background(Color.white.opacity(0.03))
        .cornerRadius(8)
    }

    private var paginationInfo: String {
        let start = page * pageSize + 1
        let end   = min((page + 1) * pageSize, allComponents.count)
        return "Page \(page + 1) / \(totalPages) (\(start)-\(end) of \(allComponents.count))"
    }

    private func pageButton(_ label: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(.gray)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.white.opacity(disabled ? 0.05 : 0.1))
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
                .cornerRadius(4)
        }
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1.0)
    }
}

// MARK: - Card

private struct ComponentCard: View {
    let component: IRComponent
    let index: Int
    @State private var expanded = false

    var body: some View {
        VStack(spacing: 0) {
            cardHeader
            cardContent
            cardFooter
            if expanded { propsPanel }
        }
        .background(Color.white.opacity(0.03))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .cornerRadius(8)
    }

    private var cardHeader: some View {
        HStack(spacing: 8) {
            Text("#\(index + 1)")
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(.gray.opacity(0.7))
            Text(component.name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
            Spacer()
            Text(component.id)
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.gray.opacity(0.7))
                .lineLimit(1)
                .truncationMode(.middle)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.05))
    }

    private var cardContent: some View {
        HStack {
            Spacer(minLength: 0)
            ComponentRenderer(component: component)
                .frame(minHeight: 80)
            Spacer(minLength: 0)
        }
        .padding(12)
    }

    private var cardFooter: some View {
        HStack {
            Text("\(component.properties.count) props" +
                 (component.children.map { ", \($0.count) children" } ?? ""))
                .font(.system(size: 12))
                .foregroundColor(.gray.opacity(0.7))
            Spacer()
            Button(action: { expanded.toggle() }) {
                Text(expanded ? "Hide Props" : "Show Props")
                    .font(.system(size: 11))
                    .foregroundColor(.gray)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.2))
    }

    private var propsPanel: some View {
        ScrollView {
            Text(formattedProperties)
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.gray)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
        }
        .frame(maxHeight: 200)
        .background(Color.black.opacity(0.3))
    }

    /// Print property types — IRValue's full JSON re-encoding isn't worth the weight here.
    private var formattedProperties: String {
        component.properties
            .map { "- \($0.type)" }
            .joined(separator: "\n")
    }
}

// MARK: - Flatten helper

private func flatten(_ components: [IRComponent]) -> [IRComponent] {
    var out: [IRComponent] = []
    func walk(_ c: IRComponent) {
        out.append(c)
        c.children?.forEach(walk)
    }
    components.forEach(walk)
    return out
}
