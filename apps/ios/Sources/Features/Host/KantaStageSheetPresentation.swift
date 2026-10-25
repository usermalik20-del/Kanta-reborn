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
    private var lastBlockedPresentationState: String?
    private var lastPresentationEvent: String?

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
        recordPresentationEvent("configure")
        updatePresentation()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        recordPresentationEvent("view-did-appear")
        updatePresentation()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if !isTransitioning, let ownedSheet, ownedSheet.presentingViewController == nil {
            recordPresentationEvent("orphaned-owned-sheet")
            self.ownedSheet = nil
            finishDismissal()
            return
        }
        updatePresentation()
    }

    private func updatePresentation() {
        guard !isTransitioning else {
            if requestedSheet != nil { recordPresentationEvent("deferred-transition") }
            return
        }

        guard requestedSheet != nil else {
            guard let presented = ownedSheet else {
                recordPresentationEvent("idle-no-request")
                return
            }
            guard presented.presentingViewController != nil else {
                ownedSheet = nil
                finishDismissal()
                return
            }
            isTransitioning = true
            recordPresentationEvent("dismiss-start")
            presented.dismiss(animated: false) { [weak self] in
                guard let self else { return }
                self.ownedSheet = nil
                self.isTransitioning = false
                self.recordPresentationEvent("dismiss-complete")
                self.finishDismissal()
            }
            return
        }

        guard ownedSheet == nil else {
            recordBlockedPresentation("owned-sheet-still-referenced")
            return
        }
        guard viewIfLoaded?.window != nil else {
            recordBlockedPresentation("presenter-not-in-window")
            return
        }
        guard presentedViewController == nil else {
            recordBlockedPresentation("presenter-already-presenting")
            return
        }
        guard let requestedSheet, let makeSheet else { return }
        lastBlockedPresentationState = nil

        let presented = makeSheet(requestedSheet)
        ownedSheet = presented
        dismissalCallbackPending = true
        isTransitioning = true
        recordPresentationEvent("present-start")
        presented.presentationController?.delegate = self
        present(presented, animated: false) { [weak self, weak presented] in
            guard let self else { return }
            self.isTransitioning = false
            self.recordPresentationEvent("present-complete")
            presented?.presentationController?.delegate = self
            self.updatePresentation()
        }
    }

    private func recordBlockedPresentation(_ reason: String) {
        let state = "sheet=\(requestedSheet?.rawValue ?? "none"),reason=\(reason),window=\(viewIfLoaded?.window != nil),owned=\(ownedSheet != nil),ownedPresenting=\(ownedSheet?.presentingViewController != nil),presented=\(presentedViewController != nil),transitioning=\(isTransitioning),gate=\(String(describing: presentationGate?.owner)),dismissing=\(presentationGate?.dismissing ?? false)"
        guard state != lastBlockedPresentationState else { return }
        lastBlockedPresentationState = state
        NSLog("[KANTA_STAGE_PRESENTATION] blocked:%@", state)
    }

    private func recordPresentationEvent(_ event: String) {
        let state = "\(event),sheet=\(requestedSheet?.rawValue ?? "none"),window=\(viewIfLoaded?.window != nil),owned=\(ownedSheet != nil),ownedPresenting=\(ownedSheet?.presentingViewController != nil),presented=\(presentedViewController != nil),transitioning=\(isTransitioning),gate=\(String(describing: presentationGate?.owner)),dismissing=\(presentationGate?.dismissing ?? false)"
        guard state != lastPresentationEvent else { return }
        lastPresentationEvent = state
        NSLog("[KANTA_STAGE_PRESENTATION] %@", state)
    }

    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        guard let ownedSheet,
              presentationController.presentedViewController === ownedSheet else { return }
        recordPresentationEvent("delegate-dismiss")
        self.ownedSheet = nil
        finishDismissal()
    }

    private func finishDismissal() {
        guard dismissalCallbackPending else { return }
        recordPresentationEvent("finish-dismissal")
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
                        KantaEmptyState(
                            icon: "list.bullet",
                            title: "No reservations yet",
                            description: "Use Search to choose a song.",
                            actionTitle: nil,
                            action: nil,
                            style: .compact
                        )
                    }
                    ForEach(model.snapshot.queue) { item in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.song.title)
                                    .font(KantaTypography.songTitle)
                                    .foregroundStyle(KantaColors.textPrimary)
                                if let singer = item.requestedBy {
                                    Text(singer)
                                        .font(KantaTypography.caption)
                                        .foregroundStyle(KantaColors.textSecondary)
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
