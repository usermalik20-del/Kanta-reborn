import Foundation

/// HTTPS browser commands reuse bounded KCP1 field framing, with an explicit versioned capability set.
public enum BrowserRemoteProtocol {
    public struct Request: Sendable {
        public let id: UUID
        public let action: String
        public let argument: String
        public let singer: String
    }
    public static func decode(_ bytes: Data) throws -> Request {
        guard (22...1024).contains(bytes.count) else { throw CompanionError.invalidInput }
        let raw = Array(bytes)
        let id = UUID(uuid: (raw[0],raw[1],raw[2],raw[3],raw[4],raw[5],raw[6],raw[7],raw[8],raw[9],raw[10],raw[11],raw[12],raw[13],raw[14],raw[15]))
        var offset = 16
        func field(_ bound: Int) throws -> String {
            guard offset + 2 <= raw.count else { throw CompanionError.invalidInput }
            let count = Int(raw[offset]) * 256 + Int(raw[offset + 1]); offset += 2
            guard count <= bound * 4, offset + count <= raw.count,
                  let value = String(bytes: raw[offset..<(offset + count)], encoding: .utf8),
                  value.utf16.count <= bound else { throw CompanionError.invalidInput }
            offset += count
            return value
        }
        let action = try field(16), argument = try field(128), singer = try field(80)
        guard offset == raw.count,
              ["snapshot","search","reserve","cancel","play","pause","next","stop"].contains(action),
              action != "search" || !argument.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              action != "cancel" || UUID(uuidString: argument) != nil else { throw CompanionError.invalidInput }
        return Request(id: id, action: action, argument: argument, singer: singer)
    }

    public struct HTTPRequest: Sendable {
        public let method: String
        public let path: String
        public let headers: [String: String]
        public let body: Data
    }
    /// Nil means more bytes are needed. No chunked encoding, pipelining, duplicate headers or authority aliases.
    public static func parseHTTP(_ bytes: Data, authority: String) throws -> HTTPRequest? {
        guard bytes.count <= 9216 else { throw CompanionError.invalidInput }
        guard let end = bytes.range(of: Data([13,10,13,10])) else {
            guard bytes.count < 8192 else { throw CompanionError.invalidInput }; return nil
        }
        guard end.upperBound <= 8192, let text = String(data: bytes[..<end.lowerBound], encoding: .ascii) else { throw CompanionError.invalidInput }
        let lines = text.components(separatedBy: "\r\n")
        let first = lines[0].components(separatedBy: " ")
        guard first.count == 3, first[2] == "HTTP/1.1" else { throw CompanionError.invalidInput }
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            guard let colon = line.firstIndex(of: ":") else { throw CompanionError.invalidInput }
            let key = line[..<colon].lowercased()
            guard !key.isEmpty, key.utf8.allSatisfy({ (97...122).contains($0) || (48...57).contains($0) || $0 == 45 }), headers[key] == nil else { throw CompanionError.invalidInput }
            headers[key] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        guard headers["host"] == authority, headers["transfer-encoding"] == nil else { throw CompanionError.invalidInput }
        let length: Int
        if let raw = headers["content-length"] {
            guard !raw.isEmpty, raw.count <= 4, raw.utf8.allSatisfy({ (48...57).contains($0) }), let value = Int(raw), value <= 1024 else { throw CompanionError.invalidInput }
            length = value
        } else { length = 0 }
        guard bytes.count <= end.upperBound + length else { throw CompanionError.invalidInput }
        guard bytes.count == end.upperBound + length else { return nil }
        return HTTPRequest(method: first[0], path: first[1], headers: headers, body: Data(bytes[end.upperBound...]))
    }
}
