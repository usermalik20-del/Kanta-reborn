import Foundation
import Testing
@testable import LocalNetworkControl

private func hex(_ text: String) -> Data {
    let characters = Array(text)
    return Data(stride(from: 0, to: characters.count, by: 2).map {
        UInt8(String(characters[$0...$0 + 1]), radix: 16)!
    })
}

// Fixed vectors generated independently using Python cryptography; Java/JCA live exchange is tested below.
private enum Vector {
    static let hello = hex("4b435031000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
    static let nonce = hex("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f")
    static let clientHello = hex("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3fd7dbaa3f04aa359d881b197b97d80b7a052369a116b803588351f25e0e8ff791")
    static let hostProof = hex("cf85da6695ff68ac62fbe05d9f3733c81446feaf4252af73d26b1ecffd6feac9")
    static let request = hex("00112233445566778899aabbccddeeff0006736561726368000b42697475696e20f09f8ea40000")
    static let clientRecord = hex("000000370000000000000000e4cff07386c3d762d37dd0daabefba82feedd01523d22ad5bdcb6390a02247bf6477d32a041deada57fdfa6fa3844e30c0985f7516e8c4")
    static let hostRecord = hex("000000840000000000000000998fa7c95733746c9d1ce1cd165bb98e5db8bcdcd1727216fe159c9c36edd1eac9c24aa2fbc250d5ea0d2ff1b864992898a9f08e3b9966fded4ca9aab66ac1efb08a810236be7141cc03e36e19ccd5d7e670b87eb04db530d29e23faf773d312f34ca165d45a354b84dd783b237cba55042ac37e4243692c85374522d553f08e70a788d1")
    static let code = "000102030405060708090a0b0c0d0e0f"
    static let id = UUID(uuidString: "00112233-4455-6677-8899-aabbccddeeff")!
    static func handshake() throws -> CompanionProtocol.Handshake {
        try CompanionProtocol.Handshake(code: code, hostHello: hello, clientNonce: nonce)
    }
}

@Test func companionMatchesIndependentHandshakeAndRecords() throws {
    let handshake = try Vector.handshake()
    #expect(handshake.clientHello == Vector.clientHello)
    var channel = try handshake.authenticate(Vector.hostProof)
    let request = try CompanionProtocol.request(id: Vector.id, action: "search", argument: "Bituin 🎤")
    #expect(request == Vector.request)
    #expect(try channel.seal(request) == Vector.clientRecord)
    let plain = try channel.open(Vector.hostRecord)
    let response = try CompanionProtocol.response(plain, request: Vector.id)
    #expect(response.current == "No song playing")
    #expect(response.songs.isEmpty)
}

@Test func companionRejectsWrongCodeAndAlteredProof() throws {
    let wrong = try CompanionProtocol.Handshake(code: String(repeating: "f", count: 32),
        hostHello: Vector.hello, clientNonce: Vector.nonce)
    #expect(throws: (any Error).self) { try wrong.authenticate(Vector.hostProof) }
    var proof = Vector.hostProof; proof[0] ^= 1
    #expect(throws: (any Error).self) { try Vector.handshake().authenticate(proof) }
    #expect(throws: (any Error).self) {
        try CompanionProtocol.Handshake(code: "123456", hostHello: Vector.hello, clientNonce: Vector.nonce)
    }
}

@Test func companionRejectsTamperReplayReflectionAndClosesChannel() throws {
    var tamper = try Vector.handshake().authenticate(Vector.hostProof)
    var record = Vector.hostRecord; record[record.count - 1] ^= 1
    #expect(throws: (any Error).self) { try tamper.open(record) }
    #expect(throws: (any Error).self) { try tamper.open(Vector.hostRecord) }
    #expect(throws: (any Error).self) { try tamper.seal(Data()) }
    var replay = try Vector.handshake().authenticate(Vector.hostProof)
    _ = try replay.open(Vector.hostRecord)
    #expect(throws: (any Error).self) { try replay.open(Vector.hostRecord) }
    var reflection = try Vector.handshake().authenticate(Vector.hostProof)
    #expect(throws: (any Error).self) { try reflection.open(Vector.clientRecord) }
}

@Test func companionRejectsRecordAndCommandBounds() throws {
    for size: UInt32 in [0, 15, 32769, UInt32.max] {
        let header = CompanionProtocol.integer(size) + CompanionProtocol.integer(UInt64(0))
        #expect(throws: (any Error).self) { try CompanionProtocol.recordLength(header) }
    }
    #expect(throws: (any Error).self) {
        try CompanionProtocol.request(id: Vector.id, action: "camera")
    }
    for blank in ["", " ", "\t\n"] {
        #expect(throws: (any Error).self) {
            try CompanionProtocol.request(id: Vector.id, action: "search", argument: blank)
        }
    }
    #expect(try !CompanionProtocol.request(id: Vector.id, action: "snapshot").isEmpty)
    #expect(throws: (any Error).self) {
        try CompanionProtocol.request(id: Vector.id, action: "search", argument: String(repeating: "🎤", count: 65))
    }
    #expect(throws: (any Error).self) {
        try CompanionProtocol.request(id: Vector.id, action: "reserve", singer: String(repeating: "a", count: 81))
    }
    var channel = try Vector.handshake().authenticate(Vector.hostProof)
    #expect(throws: (any Error).self) { try channel.seal(Data(repeating: 0, count: 32753)) }
}

@Test func companionAcceptsOnlyExplicitPrivateIPv4() throws {
    for address in ["10.0.0.1", "172.16.1.2", "172.31.255.255", "192.168.1.1"] {
        #expect(try CompanionProtocol.privateAddress(address) == address)
    }
    for address in ["localhost", "127.0.0.1", "0.0.0.0", "8.8.8.8", "172.32.0.1", "10.01.0.1", "10.0.0.256", "::1", "10.0.0.1.", "10.0.0.1\n"] {
        #expect(throws: (any Error).self) { try CompanionProtocol.privateAddress(address) }
    }
}

@Test func companionPairingInputRequiresPrivateAddressAndFullHexSecret() {
    let validCode = Vector.code
    #expect(CompanionProtocol.isValidPairingCode(validCode))
    #expect(CompanionProtocol.isValidPairingInput(address: "10.0.0.1", code: validCode))
    #expect(CompanionProtocol.isValidPairingInput(address: "172.31.255.255", code: validCode))
    #expect(!CompanionProtocol.isValidPairingInput(address: "8.8.8.8", code: validCode))
    #expect(!CompanionProtocol.isValidPairingInput(address: "127.0.0.1", code: validCode))
    #expect(!CompanionProtocol.isValidPairingInput(address: "10.0.0.1", code: ""))
    #expect(!CompanionProtocol.isValidPairingCode(String(repeating: "a", count: 31)))
    #expect(!CompanionProtocol.isValidPairingCode(String(repeating: "a", count: 33)))
    #expect(!CompanionProtocol.isValidPairingCode(String(repeating: "g", count: 32)))
    #expect(CompanionProtocol.isValidPairingCode(String(repeating: "A", count: 32)))
}

@Test func companionBoundsPeerJSONAndChecksRequestIdentity() throws {
    var channel = try Vector.handshake().authenticate(Vector.hostProof)
    let plain = try channel.open(Vector.hostRecord)
    #expect(throws: (any Error).self) { try CompanionProtocol.response(plain, request: UUID()) }
    for data in [Data((String(repeating: "[", count: 9) + String(repeating: "]", count: 9)).utf8),
                 Data([0xff]), Data("{\"x\":\"unterminated".utf8), Data(repeating: 32, count: 32753)] {
        #expect(throws: (any Error).self) { try CompanionProtocol.response(data, request: Vector.id) }
    }
}

@MainActor @Test func companionDisconnectRevokesPendingConnection() async throws {
    let client = EncryptedCompanionClient()
    client.connect(address: "10.255.255.254", code: Vector.code)
    #expect(client.busy)
    client.disconnect()
    try await Task.sleep(for: .milliseconds(100))
    #expect(!client.busy && !client.paired && client.snapshot == nil)
    #expect(client.status == "Not connected")
    client.send(action: "play")
    #expect(!client.busy)
}

#if os(macOS)
import Network

@MainActor @Test func companionClientExchangesWithProductionJavaProtocol() async throws {
    let classes = try #require(ProcessInfo.processInfo.environment["KANTA_KCP_TEST_CLASSES"])
    let host = Process()
    host.executableURL = URL(fileURLWithPath: "/usr/bin/env")
    host.arguments = ["java", "-cp", classes, "CompanionInteropHost"]
    let output = Pipe(); host.standardOutput = output
    try host.run()
    defer { if host.isRunning { host.terminate() } }
    var portText = Data()
    while portText.count < 6 {
        let byte = output.fileHandleForReading.readData(ofLength: 1)
        guard !byte.isEmpty else { throw CompanionError.closed }
        if byte == Data([10]) { break }
        portText += byte
    }
    let text = try #require(String(data: portText, encoding: .utf8))
    let number = try #require(UInt16(text))
    let port = try #require(NWEndpoint.Port(rawValue: number))
    let client = EncryptedCompanionClient()
    defer { client.disconnect() }
    client.connectToEndpoint(host: "127.0.0.1", port: port, code: Vector.code)
    try await settled(client)
    #expect(client.paired)
    #expect(client.snapshot?.current == "Synthetic song")
    client.send(action: "search", argument: "Bituin 🎤")
    try await settled(client)
    #expect(client.snapshot?.songs.first?.title == "Bituin 🎤")
    client.send(action: "reserve", argument: "synthetic-song", singer: "Test")
    // A second action while busy cannot race the first request/response exchange.
    client.send(action: "next")
    try await settled(client)
    #expect(client.snapshot?.queue.count == 1)
    client.send(action: "reserve", argument: "synthetic-song", singer: "Test")
    try await settled(client)
    #expect(client.snapshot?.queue.count == 2)
    let first = try #require(client.snapshot?.queue.first?.id)
    let last = try #require(client.snapshot?.queue.last?.id)
    #expect(first != last)
    client.send(action: "cancel", argument: first)
    try await settled(client)
    #expect(client.snapshot?.queue.map(\.id) == [last])
    client.send(action: "play")
    try await settled(client)
    #expect(client.snapshot?.playing == true)
    client.send(action: "pause")
    try await settled(client)
    #expect(client.snapshot?.playing == false)
    client.disconnect()
    try await Task.sleep(for: .milliseconds(100))
    #expect(client.snapshot == nil && !client.paired && !client.busy)
}

@MainActor private func settled(_ client: EncryptedCompanionClient) async throws {
    let deadline = ContinuousClock.now + .seconds(8)
    while client.busy && ContinuousClock.now < deadline {
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect(!client.busy)
    #expect(client.paired)
}
#endif
