import CryptoKit
import Foundation

public enum CompanionError: Error, Sendable {
    case invalidInput, authentication, invalidRecord, closed
}

/// KCP1 matches the Android protocol. There is no negotiation or plaintext fallback.
public enum CompanionProtocol {
    public static let maximumRecord = 32_768
    static let magic = Data([0x4b, 0x43, 0x50, 0x31])

    public static func privateAddress(_ text: String) throws -> String {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { throw CompanionError.invalidInput }
        var values: [Int] = []
        for part in parts {
            guard !part.isEmpty, part.utf8.allSatisfy({ (48...57).contains($0) }),
                  part.count == 1 || part.first != "0",
                  let value = Int(part), value <= 255 else { throw CompanionError.invalidInput }
            values.append(value)
        }
        guard values[0] == 10 || (values[0] == 172 && (16...31).contains(values[1])) ||
                (values[0] == 192 && values[1] == 168) else { throw CompanionError.invalidInput }
        return text
    }

    public static func isValidPairingCode(_ code: String) -> Bool {
        code.utf8.count == 32 && code.utf8.allSatisfy {
            (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0)
        }
    }

    public static func isValidPairingInput(address: String, code: String) -> Bool {
        guard (try? privateAddress(address)) != nil else { return false }
        return isValidPairingCode(code)
    }

    static func secret(_ code: String) throws -> SymmetricKey {
        guard isValidPairingCode(code) else { throw CompanionError.invalidInput }
        let characters = Array(code)
        var bytes = Data()
        for index in stride(from: 0, to: 32, by: 2) {
            guard let byte = UInt8(String(characters[index...index + 1]), radix: 16)
            else { throw CompanionError.invalidInput }
            bytes.append(byte)
        }
        return SymmetricKey(data: bytes)
    }

    static func mac(_ key: SymmetricKey, _ bytes: Data) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: bytes, using: key))
    }

    public struct Handshake: Sendable {
        private let secret: SymmetricKey
        let transcript: Data
        public let clientHello: Data
        public init(code: String, hostHello: Data, clientNonce: Data) throws {
            guard hostHello.count == 36, hostHello.prefix(4) == CompanionProtocol.magic,
                  clientNonce.count == 32 else { throw CompanionError.authentication }
            secret = try CompanionProtocol.secret(code)
            transcript = hostHello + clientNonce
            clientHello = clientNonce + CompanionProtocol.mac(secret, transcript + Data("client-proof".utf8))
        }
        public func authenticate(_ hostProof: Data) throws -> Channel {
            guard HMAC<SHA256>.isValidAuthenticationCode(hostProof,
                authenticating: transcript + Data("host-proof".utf8), using: secret)
            else { throw CompanionError.authentication }
            let prk = CompanionProtocol.mac(SymmetricKey(data: SHA256.hash(data: transcript)),
                secret.withUnsafeBytes { Data($0) })
            let key = SymmetricKey(data: prk)
            let send = CompanionProtocol.mac(key, transcript + Data("client-to-host".utf8) + Data([1]))
            let receive = CompanionProtocol.mac(key, transcript + Data("host-to-client".utf8) + Data([1]))
            return Channel(send: send, receive: receive)
        }
    }

    public struct Channel: Sendable {
        private let sendKey: SymmetricKey
        private let receiveKey: SymmetricKey
        private var sent: UInt64 = 0
        private var received: UInt64 = 0
        private var closed = false
        init(send: Data, receive: Data) {
            sendKey = SymmetricKey(data: send); receiveKey = SymmetricKey(data: receive)
        }
        public mutating func seal(_ plain: Data) throws -> Data {
            guard !closed, plain.count <= maximumRecord - 16, sent < UInt64(Int64.max)
            else { closed = true; throw CompanionError.invalidRecord }
            let nonce = CompanionProtocol.integer(UInt32(1)) + CompanionProtocol.integer(sent)
            let aad = CompanionProtocol.magic + nonce
            let box = try AES.GCM.seal(plain, using: sendKey,
                nonce: AES.GCM.Nonce(data: nonce), authenticating: aad)
            let encrypted = box.ciphertext + box.tag
            let result = CompanionProtocol.integer(UInt32(encrypted.count)) + CompanionProtocol.integer(sent) + encrypted
            sent += 1
            return result
        }
        public mutating func open(_ record: Data) throws -> Data {
            do {
                guard !closed, received < UInt64(Int64.max), record.count >= 28 else {
                    throw CompanionError.invalidRecord
                }
                let size = try CompanionProtocol.recordLength(Data(record.prefix(12)))
                guard record.count == size + 12,
                      CompanionProtocol.number(record.subdata(in: 4..<12)) == received else { throw CompanionError.invalidRecord }
                let nonce = CompanionProtocol.integer(UInt32(2)) + CompanionProtocol.integer(received)
                let ciphertext = record.subdata(in: 12..<(record.count - 16))
                let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce),
                    ciphertext: ciphertext, tag: record.suffix(16))
                let plain = try AES.GCM.open(box, using: receiveKey, authenticating: CompanionProtocol.magic + nonce)
                received += 1
                return plain
            } catch { closed = true; throw CompanionError.invalidRecord }
        }
    }

    public static func recordLength(_ header: Data) throws -> Int {
        guard header.count == 12 else { throw CompanionError.invalidRecord }
        let size = Int(number(Data(header.prefix(4))))
        guard (16...maximumRecord).contains(size) else { throw CompanionError.invalidRecord }
        return size
    }
    static func integer<T: FixedWidthInteger>(_ value: T) -> Data {
        var big = value.bigEndian
        return withUnsafeBytes(of: &big) { Data($0) }
    }
    static func number(_ bytes: Data) -> UInt64 {
        bytes.reduce(0) { ($0 << 8) | UInt64($1) }
    }

    public static func request(id: UUID, action: String, argument: String = "", singer: String = "") throws -> Data {
        guard ["snapshot", "search", "reserve", "cancel", "play", "pause", "next"].contains(action)
        else { throw CompanionError.invalidInput }
        guard action != "search" || !argument.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw CompanionError.invalidInput
        }
        var uuid = id.uuid
        var bytes = withUnsafeBytes(of: &uuid) { Data($0) }
        for (value, bound) in [(action, 16), (argument, 128), (singer, 80)] {
            let encoded = Data(value.utf8)
            guard value.utf16.count <= bound, encoded.count <= bound * 4 else { throw CompanionError.invalidInput }
            bytes += integer(UInt16(encoded.count)); bytes += encoded
        }
        return bytes
    }

    public static func response(_ bytes: Data, request: UUID) throws -> CompanionSnapshot {
        guard bytes.count <= maximumRecord - 16, String(data: bytes, encoding: .utf8) != nil
        else { throw CompanionError.invalidRecord }
        var depth = 0, quoted = false, escaped = false
        for byte in bytes {
            if quoted {
                if escaped { escaped = false }
                else if byte == 92 { escaped = true }
                else if byte == 34 { quoted = false }
            } else if byte == 34 { quoted = true }
            else if byte == 123 || byte == 91 {
                depth += 1
                guard depth <= 8 else { throw CompanionError.invalidRecord }
            } else if byte == 125 || byte == 93 {
                depth -= 1
                guard depth >= 0 else { throw CompanionError.invalidRecord }
            }
        }
        guard !quoted, depth == 0 else { throw CompanionError.invalidRecord }
        let result = try JSONDecoder().decode(CompanionSnapshot.self, from: bytes)
        guard UUID(uuidString: result.request) == request,
              result.current.utf16.count <= 128, result.songs.count <= 10, result.queue.count <= 10,
              result.songs.allSatisfy({ !$0.id.isEmpty && $0.id.utf16.count <= 128 &&
                  $0.title.utf16.count <= 128 && $0.artist.utf16.count <= 80 && $0.code.utf16.count <= 32 }),
              result.queue.allSatisfy({ UUID(uuidString: $0.id) != nil &&
                  $0.title.utf16.count <= 128 && $0.singer.utf16.count <= 80 }),
              Set(result.songs.map(\.id)).count == result.songs.count,
              Set(result.queue.map(\.id)).count == result.queue.count
        else { throw CompanionError.invalidRecord }
        return result
    }
}

public struct CompanionSong: Decodable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let artist: String
    public let code: String
}
public struct CompanionReservation: Decodable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let singer: String
}
public struct CompanionSnapshot: Decodable, Sendable {
    public let request: String
    public let playing: Bool
    public let current: String
    public var songs: [CompanionSong]
    public let queue: [CompanionReservation]
}
