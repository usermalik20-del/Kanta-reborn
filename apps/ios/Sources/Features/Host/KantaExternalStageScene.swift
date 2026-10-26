import SwiftUI
import UIKit

/// The main iPhone/iPad always remains the controller. An iOS external-display
/// scene gets only the performance stage—never a second transport or queue.
@MainActor
final class KantaExternalStageCoordinator: ObservableObject {
    static let shared = KantaExternalStageCoordinator()

    @Published private(set) var isConnected = false
    @Published private(set) var isPresenting = false

    private var scene: UIWindowScene?
    private var window: UIWindow?
    private weak var host: HostViewModel?
    private weak var settings: KantaUserSettings?

    private init() {}

    func connect(_ incomingScene: UIWindowScene) {
        scene = incomingScene
        isConnected = true
        showStage()
    }

    func disconnect(_ outgoingScene: UIWindowScene) {
        guard scene === outgoingScene else { return }
        window?.isHidden = true
        window = nil
        scene = nil
        isConnected = false
        isPresenting = false
    }

    func activate(host: HostViewModel, settings: KantaUserSettings) {
        self.host = host
        self.settings = settings
        showStage()
    }

    func deactivate(host: HostViewModel) {
        guard self.host === host else { return }
        self.host = nil
        settings = nil
        showStage()
    }

    private func showStage() {
        guard let scene else {
            isPresenting = false
            return
        }
        let window: UIWindow
        if let existing = self.window {
            window = existing
        } else {
            window = UIWindow(windowScene: scene)
            window.backgroundColor = .black
            self.window = window
        }

        if let host, let settings {
            window.rootViewController = UIHostingController(
                rootView: KantaExternalStageView(
                    model: host, settings: settings
                )
            )
            isPresenting = true
        } else {
            window.rootViewController = UIHostingController(
                rootView: KantaExternalStageStandby()
            )
            isPresenting = false
        }
        window.isHidden = false
        // Noninteractive external scene must not steal keyboard focus.
    }
}

@MainActor
private struct KantaExternalStageView: View {
    @ObservedObject var model: HostViewModel
    @ObservedObject var settings: KantaUserSettings

    var body: some View {
        ZStack {
            Color.black
            KaraokeStageView(
                asset: settings.stageVisualsEnabled
                    ? model.backgroundAsset : nil,
                builtInPreset: settings.stageVisualsEnabled
                    ? settings.selectedVisualPreset : nil,
                isPlaying: model.snapshot.transport == .playing,
                lyricPage: model.lyricPage
            )
            .padding(0)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if model.snapshot.transport == .playing,
               let singer = model.snapshot.nowSinging,
               !singer.isEmpty {
                VStack {
                    HStack {
                        Text("NOW SINGING · " + singer)
                            .font(.headline)
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.black.opacity(0.6), in: Capsule())
                            .padding(24)
                        Spacer()
                    }
                    Spacer()
                }
            }

            if model.snapshot.transport != .playing,
               let singer = model.snapshot.queue.first?.requestedBy,
               !singer.isEmpty {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Text("UP NEXT · " + singer)
                            .font(.headline)
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.black.opacity(0.6), in: Capsule())
                            .padding(24)
                    }
                }
            }
        }
        .ignoresSafeArea()
    }
}

@MainActor
private struct KantaExternalStageStandby: View {
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 18) {
                KantaBrandMark(size: 96)
                Text("KANTA Stage")
                    .font(.largeTitle.bold())
                Text("Open KANTA Karaoke on the connected device.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
            .foregroundStyle(.white)
        }
    }
}

/// UIKit supplies an independent noninteractive UIWindowScene when the system
/// supports extended output. When AirPlay only mirrors the device, iOS does
/// not create this scene and KANTA never claims extended output is active.
@MainActor
final class KantaExternalStageSceneDelegate: UIResponder, UIWindowSceneDelegate {
    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let external = scene as? UIWindowScene else { return }
        KantaExternalStageCoordinator.shared.connect(external)
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        guard let external = scene as? UIWindowScene else { return }
        KantaExternalStageCoordinator.shared.disconnect(external)
    }
}

