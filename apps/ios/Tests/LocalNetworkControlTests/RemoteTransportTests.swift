import Foundation
import GeloKaraokeCore
import Testing
@testable import LocalNetworkControl

@Test func remoteHostAdmissionCapsSessionsAndReusesReleasedSlots() {
    var admission = RemoteHostConnectionAdmission(maximumConnections: 2)
    let first = UUID()
    let second = UUID()
    let third = UUID()

    #expect(admission.admit(first))
    #expect(admission.admit(second))
    #expect(!admission.admit(third))
    #expect(!admission.admit(first))
    #expect(admission.count == 2)

    admission.release(first)
    #expect(admission.admit(third))
    #expect(admission.count == 2)
    admission.reset()
    #expect(admission.count == 0)
}

@Test func remoteFrameCodecRoundTripsFragmentedFrame() throws {
    let envelope = RemoteEnvelope(
        id: UUID(uuidString: "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE")!,
        body: .ping
    )
    let frame = try RemoteFrameCodec.encode(envelope)

    var decoder = RemoteFrameStreamDecoder()
    let split = frame.count / 2

    let firstBatch = try decoder.append(Data(frame.prefix(split)))
    #expect(firstBatch.isEmpty)
    let secondBatch = try decoder.append(Data(frame.dropFirst(split)))
    #expect(secondBatch == [envelope])
}

@Test func remoteFrameCodecDecodesBackToBackFrames() throws {
    let first = RemoteEnvelope(body: .ping)
    let second = RemoteEnvelope(body: .pong)
    var bytes = try RemoteFrameCodec.encode(first)
    bytes.append(try RemoteFrameCodec.encode(second))

    var decoder = RemoteFrameStreamDecoder()
    let batch = try decoder.append(bytes)
    #expect(batch == [first, second])
}

@Test func remoteFrameCodecRejectsOversizedLengthPrefix() throws {
    var decoder = RemoteFrameStreamDecoder()
    let oversizedPrefix = Data([0x00, 0x10, 0x00, 0x01])

    #expect(throws: RemoteFrameCodecError.frameTooLarge(1_048_577)) {
        try decoder.append(oversizedPrefix)
    }
}

@Test func remoteFrameCodecRejectsUnsupportedProtocolVersion() throws {
    let envelope = RemoteEnvelope(
        protocolVersion: GeloRemoteProtocol.currentVersion + 1,
        body: .ping
    )
    let frame = try RemoteFrameCodec.encode(envelope)
    var decoder = RemoteFrameStreamDecoder()

    #expect(
        throws: RemoteFrameCodecError.unsupportedProtocolVersion(
            GeloRemoteProtocol.currentVersion + 1
        )
    ) {
        try decoder.append(frame)
    }
}

@Test func remoteSessionGateRequiresControllerRole() {
    let code = PairingCode("123456")!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code
    )

    let action = gate.handle(
        .hello(
            RemoteHello(
                appVersion: "2",
                role: .guest
            )
        )
    )

    #expect(
        action == .close(
            RemoteFailure(
                code: "controller_hello_required",
                message:
                    "A controller hello is required before pairing."
            )
        )
    )
    #expect(gate.state == .closed)
}

@Test func remoteSessionGatePairsThenExecutesValidatedCommand() {
    let code = PairingCode("654321")!
    let sessionID = UUID(
        uuidString: "11111111-2222-3333-4444-555555555555"
    )!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code,
        sessionID: sessionID
    )

    #expect(
        gate.handle(
            .hello(
                RemoteHello(
                    appVersion: "2",
                    role: .controller
                )
            )
        )
        == .reply(
            .hello(
                RemoteHello(
                    appVersion: "2",
                    role: .host
                )
            )
        )
    )
    #expect(gate.state == .awaitingPair)

    #expect(
        gate.handle(
            .pair(
                RemotePairingRequest(code: code.value)
            )
        )
        == .reply(
            .paired(
                RemotePairingAccepted(
                    sessionID: sessionID
                )
            )
        )
    )
    #expect(gate.state == .paired(sessionID))

    #expect(
        gate.handle(
            .command(
                RemoteCommand(action: .play)
            )
        )
        == .execute(.play)
    )
}

@Test func remoteSessionGateRejectsInvalidPinAndCloses() {
    let code = PairingCode("123456")!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code
    )

    _ = gate.handle(
        .hello(
            RemoteHello(
                appVersion: "2",
                role: .controller
            )
        )
    )

    #expect(
        gate.handle(
            .pair(
                RemotePairingRequest(code: "000000")
            )
        )
        == .close(
            RemoteFailure(
                code: "invalid_pairing_code",
                message: "The pairing PIN is incorrect."
            )
        )
    )
    #expect(gate.state == .closed)
}

@Test func remoteSessionGateRejectsCommandBeforePairing() {
    let code = PairingCode("123456")!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code
    )

    #expect(
        gate.handle(
            .command(
                RemoteCommand(action: .pause)
            )
        )
        == .close(
            RemoteFailure(
                code: "controller_hello_required",
                message:
                    "A controller hello is required before pairing."
            )
        )
    )
    #expect(gate.state == .closed)
}


@Test func remoteSessionGateAllowsBoundedSearchOnlyAfterPairing() {
    let code = PairingCode("246810")!
    let requestID = UUID(
        uuidString: "22222222-3333-4444-5555-666666666666"
    )!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code
    )

    _ = gate.handle(
        .hello(
            RemoteHello(
                appVersion: "2",
                role: .controller
            )
        )
    )
    _ = gate.handle(
        .pair(
            RemotePairingRequest(code: code.value)
        )
    )

    let action = gate.handle(
        .search(
            RemoteSongSearchRequest(
                requestID: requestID,
                query: "  hello  ",
                limit: 50
            )
        )
    )

    #expect(
        action == .search(
            RemoteSongSearchRequest(
                requestID: requestID,
                query: "hello",
                limit: 50
            )
        )
    )
}

@Test func remoteSessionGateRejectsOversizedSearchWithoutClosingPair() {
    let code = PairingCode("135790")!
    let sessionID = UUID(
        uuidString: "33333333-4444-5555-6666-777777777777"
    )!
    var gate = RemoteHostSessionGate(
        expectedPairingCode: code,
        sessionID: sessionID
    )

    _ = gate.handle(
        .hello(
            RemoteHello(
                appVersion: "2",
                role: .controller
            )
        )
    )
    _ = gate.handle(
        .pair(
            RemotePairingRequest(code: code.value)
        )
    )

    let action = gate.handle(
        .search(
            RemoteSongSearchRequest(
                query: String(repeating: "a", count: 201),
                limit: 100
            )
        )
    )

    guard case let .reply(.failure(failure)) = action else {
        Issue.record("Expected a bounded search failure")
        return
    }

    #expect(failure.code == "invalid_search")
    #expect(gate.state == .paired(sessionID))
}

@Test func remoteCommandReplayCacheExecutesAnEnvelopeOnce() {
    var cache = RemoteCommandReplayCache(capacity: 2)
    let first = UUID()
    let second = UUID()
    let third = UUID()

    // Swift Testing's macro captures the call receiver immutably. Evaluate
    // mutating replay-cache actions first, then assert their returned values.
    let beganFirst = cache.begin(first)
    #expect(beganFirst)
    let repeatedDuringFlight = cache.begin(first)
    #expect(!repeatedDuringFlight) // Retry while executing.
    #expect(cache.result(for: first) == .inFlight)
    cache.finish(first, result: .succeeded)
    let repeatedAfterSuccess = cache.begin(first)
    #expect(!repeatedAfterSuccess) // Retry after success.
    #expect(cache.result(for: first) == .succeeded)

    let beganSecond = cache.begin(second)
    #expect(beganSecond) // Deliberate new command.
    let failure = RemoteFailure(code: "command_failed", message: "Unavailable")
    cache.finish(second, result: .failed(failure))
    let repeatedAfterFailure = cache.begin(second)
    #expect(!repeatedAfterFailure)
    #expect(cache.result(for: second) == .failed(failure))

    let beganThird = cache.begin(third)
    #expect(beganThird)
    #expect(cache.result(for: first) == nil) // Evict the oldest ID.
    let beganEvicted = cache.begin(first)
    #expect(beganEvicted) // Replay protection is intentionally bounded.
}


@Test func remoteReplayCapacityDoesNotEvictExecutingCommands() {
    var cache = RemoteCommandReplayCache(capacity: 1)
    let active = UUID()
    let next = UUID()
    let began = cache.begin(active)
    #expect(began)
    let overflow = cache.begin(next)
    #expect(!overflow)
    #expect(cache.result(for: active) == .inFlight)
    cache.finish(UUID(), result: .succeeded)
    #expect(cache.result(for: active) == .inFlight)
    cache.finish(active, result: .succeeded)
    let accepted = cache.begin(next)
    #expect(accepted)
    #expect(cache.result(for: active) == nil)
    #expect(cache.result(for: next) == .inFlight)
}
