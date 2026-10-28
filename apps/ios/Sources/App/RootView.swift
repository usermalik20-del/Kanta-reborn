import SwiftUI

// Serializes app presentations and tracks the stage sheet nested inside fullscreen.
@MainActor
final class KantaPresentationGate: ObservableObject {
    enum Owner: Equatable { case navigation, host, stage, fullscreen }
    @Published private(set) var owner: Owner?
    @Published private(set) var dismissing = false
    private struct PendingRequest { let owner: Owner; let action: @MainActor () -> Void }
    private var pendingRequests: [PendingRequest] = []
    private var fullscreenChildActive = false
    private var fullscreenDismissalRequested = false

    func request(_ requested: Owner, action: @escaping @MainActor () -> Void) {
        if owner == .fullscreen, requested == .stage, !dismissing {
            owner = .stage
            fullscreenChildActive = true
            action()
            return
        }
        guard owner == nil && !dismissing else {
            guard !pendingRequests.contains(where: { $0.owner == requested }) else {
                return
            }
            pendingRequests.append(PendingRequest(owner: requested, action: action))
            return
        }
        owner = requested
        action()
    }

    func beginDismissal(_ requested: Owner) {
        if owner == requested { dismissing = true; return }
        if requested == .fullscreen, owner == .stage, fullscreenChildActive {
            fullscreenDismissalRequested = true
            dismissing = true
        }
    }

    func completeDismissal(_ requested: Owner) {
        if requested == .stage, owner == .stage, fullscreenChildActive {
            owner = .fullscreen
            fullscreenChildActive = false
            dismissing = fullscreenDismissalRequested
            fullscreenDismissalRequested = false
            return
        }
        if requested == .fullscreen, owner == .stage, fullscreenChildActive {
            owner = nil
            fullscreenChildActive = false
            fullscreenDismissalRequested = false
            dismissing = false
            activateNextRequest()
            return
        }
        guard owner == requested else { return }
        owner = nil
        dismissing = false
        if requested == .fullscreen {
            fullscreenChildActive = false
            fullscreenDismissalRequested = false
        }
        activateNextRequest()
    }

    private func activateNextRequest() {
        guard !pendingRequests.isEmpty else { return }
        let next = pendingRequests.removeFirst()
        owner = next.owner
        next.action()
    }
}

@MainActor
struct RootView: View {
    @ObservedObject var services: AppServices
    @StateObject private var presentationGate = KantaPresentationGate()
    @Environment(\.scenePhase) private var scenePhase
    @State private var navigationRoot: Route = .menu
    @State private var navigationPath: [Route] = []
    @State private var activeNavigation: NavigationSheet? = nil
    @State private var navigationDismissing = false
    @State private var activeTab: KantaTabBar.Destination = .stage

    private enum NavigationSheet: String, Identifiable {
        case navigation
        var id: String { rawValue }
    }

    private enum Route: String, Hashable, Identifiable {
        case menu, library, queue, remote, settings, legal, icloud, songbook
        var id: String { rawValue }
    }

    init(services: AppServices) {
        self.services = services
        let mode = ProcessInfo.processInfo.environment["GELO_SCREENSHOT_MODE"] ?? ""
        let initialRoute = Route(rawValue: mode)
        _navigationRoot = State(initialValue: initialRoute ?? .menu)
        _navigationPath = State(initialValue: [])
        _activeNavigation = State(initialValue: initialRoute == nil ? nil : .navigation)
    }

    var body: some View {
        ZStack {
            NavigationStack {
                HostView(
                    services: services,
                    presentationGate: presentationGate,
                    navigationSheetPresented: activeNavigation != nil,
                    onOpenNavigation: {
                        guard activeNavigation == nil else { return }
                        presentationGate.request(.navigation) {
                            navigationRoot = .menu
                            navigationPath = []
                            activeNavigation = .navigation
                        }
                    }
                )
                .toolbar(.hidden, for: .navigationBar)
            }
        }
        // The tab bar is an inset rather than an overlay so Host content is
        // never covered, and it never intercepts the stage's own controls.
        .safeAreaInset(edge: .bottom, spacing: 0) {
            KantaTabBar(selection: activeTab, onSelect: select)
        }
        // Keep the navigation sheet on a sibling presentation anchor rather
        // than on the NavigationStack that contains HostView. The shared gate
        // serializes both owners, while separate anchors avoid the iPad losing
        // an embedded Stage sheet in an ancestor/descendant presentation chain.
        .background {
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .allowsHitTesting(false)
                .sheet(item: $activeNavigation, onDismiss: {
                    navigationRoot = .menu
                    navigationPath = []
                    activeTab = .stage
                    navigationDismissing = false
                    presentationGate.completeDismissal(.navigation)
                }) {
                    _ in
                    NavigationStack(path: $navigationPath) {
                        destination(navigationRoot)
                            .navigationDestination(for: Route.self) { route in
                                destination(route)
                            }
                    }
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") {
                                guard !navigationDismissing else { return }
                                navigationDismissing = true
                                presentationGate.beginDismissal(.navigation)
                                activeNavigation = nil
                            }
                            .accessibilityIdentifier("navigation.done")
                        }
                    }
                }
        }
        .preferredColorScheme(.dark)
        .tint(GeloVisualStyle.accent)
        .task {
            services.diagnostics.beginSession()
            await services.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            services.audioOwnership.setForeground(phase == .active)
            if phase != .active { services.browserCompanion?.stop() }
            switch phase {
            case .active: services.diagnostics.setForeground(true)
            case .background: services.diagnostics.setForeground(false)
            default: break
            }
        }
    }

    /// Tab taps route through the same presentation gate the stage uses, so
    /// the bar cannot create a second, uncoordinated presentation.
    private func select(_ destination: KantaTabBar.Destination) {
        activeTab = destination
        switch destination {
        case .stage:
            guard activeNavigation != nil, !navigationDismissing else { return }
            navigationDismissing = true
            presentationGate.beginDismissal(.navigation)
            activeNavigation = nil
        case .library:
            openNavigation(.library)
        case .queue:
            openNavigation(.queue)
        case .more:
            openNavigation(.menu)
        }
    }

    private func openNavigation(_ route: Route) {
        if activeNavigation == nil {
            presentationGate.request(.navigation) {
                navigationRoot = route
                navigationPath = []
                activeNavigation = .navigation
            }
        } else {
            navigationRoot = route
            navigationPath = []
        }
    }

    private func openNavigationDestination(_ route: Route) {
        guard activeNavigation != nil, !navigationDismissing else { return }
        navigationPath.append(route)
    }

    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .menu:
            List {
                Section("KANTA") {
                    Button("Song Library") { openNavigationDestination(.library) }
                    Button("KANTA Songbook") { openNavigationDestination(.songbook) }
                    Button("Settings") { openNavigationDestination(.settings) }
                    Button("Companion Remote") { openNavigationDestination(.remote) }
                    #if !KANTA_PERSONAL_SIGNING
                    Button("iCloud Save") { openNavigationDestination(.icloud) }
                    #endif
                    Button("Legal & Privacy") { openNavigationDestination(.legal) }
                }
            }
            .navigationTitle("More")
        case .library: LibraryView(services: services)
        case .queue: KantaQueueView(services: services)
        case .remote: RemoteView(browserHost: services.browserCompanion)
        case .settings: KantaSettingsView(services: services)
        case .legal: LegalDisclosureView()
        case .icloud: ICloudBackupView(services: services)
        case .songbook: KantaSongbookView(library: services.library)
        }
    }
}
