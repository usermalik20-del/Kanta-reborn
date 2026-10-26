import GeloKaraokeCore
import SwiftUI
import WebKit
import UIKit

/// A visible player has one terminal lifetime, including repeated links.
@MainActor
struct YouTubeEmbeddedPlayerView: UIViewRepresentable {
    let session: YouTubePreviewLifecycle.Session
    let owner: YouTubeEmbeddedPlayerOwner
    var audioOwnership: KaraokeAudioOwnership? = nil
    let onFailure: (UUID) -> Void

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.allowsPictureInPictureMediaPlayback = false
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        configuration.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.isOpaque = false
        view.backgroundColor = .black
        view.scrollView.isScrollEnabled = false
        guard audioOwnership?.ownsOnlineAudio(session.id) != false else {
            context.coordinator.retire()
            return view
        }
        owner.claim(context.coordinator)
        context.coordinator.attach(view)
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard audioOwnership?.ownsOnlineAudio(session.id) != false else {
            context.coordinator.retire()
            return
        }
        context.coordinator.load(session, in: view)
    }

    func makeCoordinator() -> Coordinator { Coordinator(session: session, onFailure: onFailure) }

    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        coordinator.retire()
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        private let session: YouTubePreviewLifecycle.Session
        private let onFailure: (UUID) -> Void
        private weak var view: WKWebView?
        private var navigation: WKNavigation?
        private var loaded = false
        private var retired = false
        private let allowedHosts: Set<String> = [
            "www.youtube-nocookie.com", "youtube-nocookie.com",
            "www.youtube.com", "youtube.com"
        ]

        init(session: YouTubePreviewLifecycle.Session, onFailure: @escaping (UUID) -> Void) {
            self.session = session
            self.onFailure = onFailure
        }

        func attach(_ view: WKWebView) {
            self.view = view
            view.navigationDelegate = self
            NotificationCenter.default.addObserver(self, selector: #selector(resignActive),
                name: UIApplication.willResignActiveNotification, object: nil)
        }

        func load(_ session: YouTubePreviewLifecycle.Session, in view: WKWebView) {
            guard !retired, !loaded, self.view === view, self.session.id == session.id,
                  UIApplication.shared.applicationState == .active else { return }
            loaded = true
            let url = URL(string: "https://www.youtube-nocookie.com/embed/\(session.reference.videoID)?playsinline=1")!
            navigation = view.load(URLRequest(url: url))
        }

        @objc private func resignActive() { retire() }

        func retire() {
            guard !retired else { return }
            // Make retirement terminal before WebKit can deliver cancellation or process callbacks.
            retired = true
            navigation = nil
            NotificationCenter.default.removeObserver(self)
            guard let view else { return }
            view.navigationDelegate = nil
            self.view = nil

            // Detach synchronously. A live WKWebView can make the accessibility tree
            // expensive to query on iPad while its remote content process is retiring.
            // Do this before any further WebKit IPC so Stop/Done can return the sheet
            // to link entry without leaving the native view attached.
            view.isHidden = true
            view.removeFromSuperview()
            view.stopLoading()

            // Media suspension and blanking are cleanup, not presentation state. Defer
            // them one MainActor turn so they cannot hold up SwiftUI's state transition;
            // the delegate is already nil and the coordinator is terminal.
            Task { @MainActor in
                await Task.yield()
                view.pauseAllMediaPlayback(completionHandler: nil)
                view.setAllMediaPlaybackSuspended(true, completionHandler: nil)
                view.loadHTMLString("", baseURL: nil)
            }
        }

        private func fail(_ webView: WKWebView, navigation: WKNavigation?) {
            guard !retired, view === webView,
                  navigation == nil || self.navigation === navigation else { return }
            retire()
            onFailure(session.id)
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            guard !retired, view === webView else { return }
            self.navigation = navigation
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                     withError error: Error) {
            let failure = error as NSError
            guard failure.domain != NSURLErrorDomain || failure.code != NSURLErrorCancelled else { return }
            fail(webView, navigation: navigation)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            fail(webView, navigation: navigation)
        }

        func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
            fail(webView, navigation: nil)
        }

        func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse,
                     decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
            guard !retired, view === webView else { decisionHandler(.cancel); return }
            if response.isForMainFrame, let http = response.response as? HTTPURLResponse,
               http.statusCode >= 400 {
                decisionHandler(.cancel)
                fail(webView, navigation: nil)
            } else { decisionHandler(.allow) }
        }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard !retired, view === webView, action.targetFrame != nil,
                  let url = action.request.url, url.scheme == "https",
                  let host = url.host?.lowercased(), allowedHosts.contains(host) else {
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }
}

/// Retire the previous native instance before SwiftUI mounts a replacement.
@MainActor
final class YouTubeEmbeddedPlayerOwner: ObservableObject {
    private weak var current: YouTubeEmbeddedPlayerView.Coordinator?

    func claim(_ coordinator: YouTubeEmbeddedPlayerView.Coordinator) {
        current?.retire()
        current = coordinator
    }

    func retire() {
        current?.retire()
        current = nil
    }
}
