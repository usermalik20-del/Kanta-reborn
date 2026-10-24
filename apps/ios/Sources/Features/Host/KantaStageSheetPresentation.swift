import Combine
import GeloKaraokeCore
import SwiftUI
import UIKit

enum KantaStageSheet: String, Identifiable {
    case search
    case queue

    var id: String { rawValue }
}


/// Owns the embedded Host Search/Queue modal in UIKit. The iPad walkthrough
/// showed that the nested SwiftUI sheet never entered the accessibility tree
/// even after consolidating SwiftUI sheet modifiers. Keeping this presentation
/// under a stable controller avoids another competing SwiftUI sheet identity.
@MainActor
struct KantaEmbeddedStageSheetPresentation: UIViewControllerRepresentable {
    @Binding var sheet: KantaStageSheet?
    @ObservedObject var model: HostViewModel
    @Binding var singerName: String
    let microphoneBusy: Bool
    @ObservedObject var presentationGate: KantaPresentationGate

    func makeUIViewController(context: Context) -> KantaStageSheetModalPresenter {
        KantaStageSheetModalPresenter()
    }

    func updateUIViewController(
        _ presenter: KantaStageSheetModalPresenter,
        context: Context
    ) {
        let sheetBinding = $sheet
        let singerNameBinding = $singerName
        let gate = presentationGate
        let hostModel = model
        let busy = microphoneBusy

        presenter.configure(
            sheet: sheet,
            presentationGate: gate,
            makeSheet: { requestedSheet in
                let content = KantaStageSheetContent(
                    sheet: requestedSheet,
                    model: hostModel,
                    singerName: singerNameBinding,
                    microphoneBusy: busy,
                    onClose: {
                        gate.beginDismissal(.stage)
                        sheetBinding.wrappedValue = nil
                    }
                )
                let controller = UIHostingController(rootView: content)
                controller.modalPresentationStyle = .pageSheet
                if let presentation = controller.sheetPresentationController {
                    presentation.detents = [.large()]
                    presentation.prefersGrabberVisible = true
                }
                return controller
            },
            onDismiss: {
                sheetBinding.wrappedValue = nil
                gate.completeDismissal(.stage)
            }
        )
    }
}

@MainActor
final class KantaStageSheetModalPresenter: UIViewController,
    UIAdaptivePresentationControllerDelegate {
    private var requestedSheet: KantaStageSheet?
    private var makeSheet: (@MainActor (KantaStageSheet) -> UIViewController)?
    private var onDismiss: (@MainActor () -> Void)?
    private weak var presentationGate: KantaPresentationGate?
    private var gateObservation: AnyCancellable?
    private weak var ownedSheet: UIViewController?
    private var isTransitioning = false
    private var dismissalCallbackPending = false

    override func loadView() {
        view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
    }

    func configure(
        sheet: KantaStageSheet?,
        presentationGate: KantaPresentationGate,
        makeSheet: @escaping @MainActor (KantaStageSheet) -> UIViewController,
        onDismiss: @escaping @MainActor () -> Void
    ) {
        requestedSheet = sheet
        if self.presentationGate !== presentationGate {
            gateObservation = presentationGate.objectWillChange.sink { [weak self] in
                Task { @MainActor in
                    self?.updatePresentation()
                }
            }
        }
        self.presentationGate = presentationGate
        self.makeSheet = makeSheet
        self.onDismiss = onDismiss
        updatePresentation()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        updatePresentation()
    }

    private func updatePresentation() {
        guard !isTransitioning else { return }

        guard requestedSheet != nil else {
            guard let presented = ownedSheet else { return }
            guard presented.presentingViewController != nil else {
                ownedSheet = nil
                finishDismissal()
                return
            }
            isTransitioning = true
            presented.dismiss(animated: false) { [weak self] in
                guard let self else { return }
                self.ownedSheet = nil
                self.isTransitioning = false
                self.finishDismissal()
            }
            return
        }

        guard ownedSheet == nil,
              viewIfLoaded?.window != nil,
              presentedViewController == nil,
              let requestedSheet,
              let makeSheet else { return }

        let presented = makeSheet(requestedSheet)
        ownedSheet = presented
        dismissalCallbackPending = true
        isTransitioning = true
        presented.presentationController?.delegate = self
        present(presented, animated: false) { [weak self, weak presented] in
            guard let self else { return }
            self.isTransitioning = false
            presented?.presentationController?.delegate = self
            self.updatePresentation()
        }
    }

    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        guard let ownedSheet,
              presentationController.presentedViewController === ownedSheet else { return }
        self.ownedSheet = nil
        finishDismissal()
    }

    private func finishDismissal() {
        guard dismissalCallbackPending else { return }
        dismissalCallbackPending = false
        presentationGate?.beginDismissal(.stage)
        onDismiss?()
    }
}

/// Shared content for the embedded UIKit presenter and fullscreen child sheet.
@MainActor
struct KantaStageSheetContent: View {
    let sheet: KantaStageSheet
    @ObservedObject var model: HostViewModel
    @Binding var singerName: String
    let microphoneBusy: Bool
    let onClose: () -> Void

    @ViewBuilder
    var body: some View {
        switch sheet {
        case .search:
            KantaStageSearchView(
                model: model,
                singerName: $singerName,
                microphoneBusy: microphoneBusy,
                onClose: onClose
            )
            .padding(12)
            .background(GeloVisualStyle.pageBackground)
            .preferredColorScheme(.dark)
            .presentationDetents([.large])
        case .queue:
            NavigationStack {
                List {
                    if model.snapshot.queue.isEmpty {
                        Text("No reservations yet. Use Search to choose a song.")
                    }
                    ForEach(model.snapshot.queue) { item in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.song.title)
                                if let singer = item.requestedBy {
                                    Text(singer).font(.caption)
                                }
                            }
                            Spacer()
                            Button(role: .destructive) {
                                Task { await model.removeQueueItem(item.id) }
                            } label: {
                                Image(systemName: "trash").frame(width: 44, height: 44)
                            }
                            .accessibilityLabel("Remove " + item.song.title)
                        }
                    }
                }
                .navigationTitle("Reservations")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done", action: onClose)
                    }
                }
            }
            .preferredColorScheme(.dark)
        }
    }
}
