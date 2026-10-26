import SwiftUI
import GeloKaraokeCore

// MARK: - Workspace Architecture
/// Core workspace container for persistent stage and transient accessories
/// Implements the "Always on Stage" design pattern from Compact Workspace Redesign
@MainActor
struct KantaWorkspace: View {
    @ObservedObject var services: AppServices
    @ObservedObject var presentationState: WorkspacePresentationState
    @State private var activeAccessory: WorkspaceAccessory? = nil
    
    var body: some View {
        ZStack {
            // 1. Karaoke stage (persistent)
            PersistentStageView(services: services, presentationState: presentationState)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            
            // 2. Accessory presentation layer (transient, single active)
            AccessoryPresentationLayer(
                services: services,
                presentationState: presentationState,
                activeAccessory: $activeAccessory
            )
        }
        .background(KantaBackground().allowsHitTesting(false))
        .workspaceToolbar
        .workspaceVisualHierarchy
    }
}

// MARK: - WorkspacePresentationState
@MainActor
@Observable
class WorkspacePresentationState: NSObject {
    var presentationGate: KantaPresentationGate
    var activeAccessory: WorkspaceAccessory?
    var activeNavigation: NavigationSheet? = nil
    
    init() {
        self.presentationGate = KantaPresentationGate()
        super.init()
    }
    
    func presentAccessory(_ accessory: WorkspaceAccessory) {
        if activeAccessory != accessory {
            activeAccessory = accessory
        }
    }
    
    func dismissAccessory() {
        activeAccessory = nil
    }
}
