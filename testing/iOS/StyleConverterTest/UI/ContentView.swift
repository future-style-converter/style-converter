//
//  ContentView.swift
//  StyleConverterTest
//
//  Top-level gallery content: loads tmpOutput.json and routes between
//  the screenshot-capture phase (on first launch) and the browseable
//  gallery view.
//

import SwiftUI

struct ContentView: View {
    @State private var document: IRDocument?
    @State private var error: String?
    @State private var captureComplete = false

    var body: some View {
        Group {
            if let error = error {
                ErrorView(message: error, retry: loadDocument)
            } else if let doc = document {
                if captureComplete {
                    ComponentGallery(document: doc)
                } else {
                    ScreenshotCaptureView(
                        document: doc,
                        onComplete: { captureComplete = true }
                    )
                }
            } else {
                ProgressView("Loading IR…")
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .onAppear(perform: loadDocument)
    }

    private func loadDocument() {
        error = nil
        guard let url = Bundle.main.url(forResource: "tmpOutput", withExtension: "json") else {
            error = "tmpOutput.json not found in bundle. Run ./test-ios.sh to generate it."
            return
        }
        do {
            let data = try Data(contentsOf: url)
            document = try JSONDecoder().decode(IRDocument.self, from: data)
        } catch {
            self.error = "Failed to decode IR: \(error.localizedDescription)"
        }
    }
}

private struct ErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("Error Loading Document")
                .font(.headline)
                .foregroundColor(Color(red: 0.97, green: 0.44, blue: 0.44))
            Text(message)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.6))
                .multilineTextAlignment(.center)
            Button("Retry", action: retry)
                .buttonStyle(.bordered)
                .tint(.red)
        }
        .padding(32)
    }
}
