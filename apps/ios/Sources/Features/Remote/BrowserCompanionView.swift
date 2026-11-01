import CryptoKit
import Foundation
import GeloKaraokeCore
import LocalNetworkControl
import SwiftUI
import WebKit
import Security

@MainActor
struct BrowserCompanionView: View {
    @ObservedObject var host: BrowserRemoteHost
    @Environment(\.scenePhase) private var scenePhase
    @State private var hostAddress = ""
    @State private var localAddresses: [String] = []
    @State private var remoteAddress = ""
    @State private var fingerprint = ""
    @State private var remote: Destination?
    @State private var notice = ""
    @State private var remoteError = ""
    private struct Destination: Identifiable { let id = UUID(); let address: String; let fingerprint: String }
    var body: some View {
        List {
            Section("Host on this device") {
                Text("Keep KANTA open on the same Wi-Fi. Hosting starts only when you select Host.")
                if !localAddresses.isEmpty {
                    Picker("This device's Wi-Fi address", selection: $hostAddress) {
                        Text("Select address").tag("")
                        ForEach(localAddresses, id: \.self) { Text($0).tag($0) }
                    }
                }
                TextField("This device's private IPv4 address", text: $hostAddress)
                    .keyboardType(.numbersAndPunctuation).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .accessibilityIdentifier("browser.host.address")
                Button("Host on this device") { startHost() }
                    .disabled(host.active || host.preparing || (try? CompanionProtocol.privateAddress(hostAddress)) == nil)
                    .accessibilityIdentifier("browser.host.start")
                Text(host.status).accessibilityIdentifier("browser.host.status")
                if host.active {
                    LabeledContent("Paired guests", value: String(host.guestCount))
                    Text("Browser / app address").font(.caption)
                    Text(host.address).textSelection(.enabled)
                    Text("Private pairing code").font(.caption)
                    Text(host.code).font(.system(.body, design: .monospaced)).textSelection(.enabled).privacySensitive()
                    Text("Certificate SHA-256").font(.caption)
                    Text(host.fingerprint).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                    Button("Stop hosting / revoke all guests", role: .destructive) { host.stop() }
                        .accessibilityIdentifier("browser.host.stop")
                }
            }
            Section("Companion Remote") {
                Text("Enter the address and certificate fingerprint displayed by either an iOS or Android HTTPS host. The remote opens inside KANTA.")
                TextField("Host private IPv4 address", text: $remoteAddress)
                    .keyboardType(.numbersAndPunctuation).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .accessibilityIdentifier("browser.remote.address")
                TextField("Host certificate SHA-256 (64 hexadecimal characters)", text: $fingerprint)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .accessibilityIdentifier("browser.remote.fingerprint")
                Button("Open Companion Remote") {
                    guard scenePhase == .active, validRemote else { return }
                    remoteError = ""
                    remote = Destination(address: remoteAddress, fingerprint: fingerprint.lowercased())
                }.disabled(!validRemote).accessibilityIdentifier("browser.remote.open")
            }
            Section("Browser guests") {
                Text("Enter the host's full https:// address in your browser. Before entering its pairing code, compare the site's certificate SHA-256 fingerprint with the host. Cancel if it differs. A local certificate warning alone does not verify identity. Some browsers may require accepting the local certificate; no system-wide certificate installation is needed.")
                Text("Pairing closes after five minutes or three rejected attempts. Up to four guests can pair; each session lasts up to ten minutes. Hosting expires after thirty minutes or when KANTA leaves the foreground. Returning does not restart it. Disconnecting never stops host playback. No microphone, camera or media file is shared.")
                Text("Previous, seek, YouTube transport, Favorites, Host Cam and Audio FX are visibly unavailable until their host capabilities are implemented. Use the host's existing controls for those features.")
            }
            if !notice.isEmpty { Text(notice).foregroundStyle(.orange) }
        }
        .navigationTitle("Host / Companion Remote")
        .task { localAddresses = await Task.detached { BrowserRemoteHost.localAddresses() }.value }
        .sheet(item: $remote) { destination in
            NavigationStack {
                PinnedCompanionBrowser(address: destination.address, fingerprint: destination.fingerprint, failure: $remoteError)
                    .overlay { if !remoteError.isEmpty { Text(remoteError).padding().frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.black) } }
                    .navigationTitle("Companion Remote")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Disconnect") { remote = nil } } }
            }
        }
        .onChange(of: scenePhase) { _, phase in if phase != .active { remote = nil; host.stop() } }
    }
    private var validRemote: Bool {
        (try? CompanionProtocol.privateAddress(remoteAddress)) != nil && fingerprint.utf8.count == 64 && fingerprint.utf8.allSatisfy { (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0) }
    }
    private func startHost() {
        guard scenePhase == .active else { return }
        do {
            var assets: [String: Data] = [:]
            let root = Bundle.main.url(forResource: "CompanionRemote", withExtension: nil)
            for (path, name) in [("/", "index.html"), ("/remote.js", "remote.js"), ("/remote.css", "remote.css")] {
                guard let url = root?.appendingPathComponent(name) else { throw CompanionError.invalidInput }
                assets[path] = try Data(contentsOf: url)
            }
            for index in 1...8 {
                let number = String(format: "%02d", index)
                let path = "/assets/covers/\(number).webp"
                guard let url = root?.appendingPathComponent("art/covers/\(number).webp") else { throw CompanionError.invalidInput }
                assets[path] = try Data(contentsOf: url)
            }
            host.start(address: hostAddress, assets: assets); notice = ""
        } catch { notice = "Companion interface resources could not be opened." }
    }
}

/// Nonpersistent, origin-restricted WebKit with one user-transcribed certificate pin.
@MainActor
private struct PinnedCompanionBrowser: UIViewRepresentable {
    let address: String
    let fingerprint: String
    @Binding var failure: String
    func makeCoordinator() -> Coordinator { Coordinator(address: address, fingerprint: fingerprint, failure: $failure) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration(); configuration.websiteDataStore = .nonPersistent()
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.navigationDelegate = context.coordinator; web.uiDelegate = context.coordinator
        web.load(URLRequest(url: URL(string: "https://\(address):45732/")!))
        return web
    }
    func updateUIView(_ web: WKWebView, context: Context) {}
    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        coordinator.active = false; web.stopLoading(); web.navigationDelegate = nil; web.uiDelegate = nil
        web.loadHTMLString("", baseURL: nil)
    }
    @MainActor final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        let address: String
        let fingerprint: String
        var active = true
        let failure: Binding<String>
        init(address: String, fingerprint: String, failure: Binding<String>) { self.address = address; self.fingerprint = fingerprint; self.failure = failure }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping @MainActor @Sendable (WKNavigationActionPolicy) -> Void) {
            guard active, let url = navigationAction.request.url, url.scheme == "https", url.host == address,
                  url.port == 45732, url.user == nil, url.password == nil else { decisionHandler(.cancel); return }
            decisionHandler(.allow)
        }
        func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping @MainActor @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
            guard active, challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
                  challenge.protectionSpace.host == address, challenge.protectionSpace.port == 45732,
                  let trust = challenge.protectionSpace.serverTrust,
                  let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate], let certificate = chain.first else { completionHandler(.cancelAuthenticationChallenge, nil); return }
            let actual = SHA256.hash(data: SecCertificateCopyData(certificate) as Data).map { String(format: "%02x", $0) }.joined()
            guard actual == fingerprint else { completionHandler(.cancelAuthenticationChallenge, nil); return }
            completionHandler(.useCredential, URLCredential(trust: trust))
        }
        func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping @MainActor @Sendable (WKPermissionDecision) -> Void) { decisionHandler(.deny) }
        func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
            if active { failure.wrappedValue = "Companion page closed unexpectedly. Disconnect and pair again; inspect the host queue before repeating a reservation." }
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            if active { failure.wrappedValue = "Companion connection failed. Check the host address, exact certificate fingerprint and shared Wi-Fi. Close this screen and retry." }
        }
    }
}
