import Combine
import Darwin
import CryptoKit
import Foundation
import Network
import Security

/// Foreground-only HTTPS server; callback execution is serialized and never owns a karaoke queue.
@MainActor
public final class BrowserRemoteHost: ObservableObject {
    @Published public private(set) var status = "Browser companion is off"
    @Published public private(set) var address = ""
    @Published public private(set) var code = ""
    @Published public private(set) var fingerprint = ""
    @Published public private(set) var guestCount = 0
    public var active: Bool { listener != nil }
    public var preparing: Bool { preparation != nil }
    public typealias Handler = @MainActor @Sendable (BrowserRemoteProtocol.Request) async throws -> Data
    private let handler: Handler
    private var listener: NWListener?
    private var generation = UUID()
    private var preparation: Task<Void, Never>?
    private var expiry: Task<Void, Never>?
    private var connections: [UUID: NWConnection] = [:]
    private var deadlines: [UUID: Task<Void, Never>] = [:]
    private var guestTasks: [UUID: Task<Void, Never>] = [:]
    private let queue = DispatchQueue(label: "ph.kanta.browser.https")
    private var started = ContinuousClock.now
    private var guests: [String: Guest] = [:]
    private var failures = 0
    private var executing: UUID?
    private var assets: [String: Data] = [:]
    private struct Cached { let input: Data; let output: Data }
    private struct Guest {
        let created = ContinuousClock.now
        var replies: [UUID: Cached] = [:]
        var bytes = 0
        var valid: Bool { created.duration(to: .now) < .seconds(600) }
    }
    public nonisolated static func localAddresses() -> [String] {
        var list: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&list) == 0, let first = list else { return [] }
        defer { freeifaddrs(list) }
        var result = Set<String>()
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let entry = cursor {
            defer { cursor = entry.pointee.ifa_next }
            guard let address = entry.pointee.ifa_addr, address.pointee.sa_family == UInt8(AF_INET),
                  entry.pointee.ifa_flags & UInt32(IFF_UP) != 0, entry.pointee.ifa_flags & UInt32(IFF_LOOPBACK) == 0 else { continue }
            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            guard getnameinfo(address, socklen_t(MemoryLayout<sockaddr_in>.size), &buffer, socklen_t(buffer.count), nil, 0, NI_NUMERICHOST) == 0 else { continue }
            let value = String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self)
            if (try? CompanionProtocol.privateAddress(value)) != nil { result.insert(value) }
        }
        return result.sorted()
    }
    public init(handler: @escaping Handler) { self.handler = handler; BrowserRemoteIdentity.remove() }
    public func stop() {
        generation = UUID(); preparation?.cancel(); preparation = nil; expiry?.cancel(); expiry = nil
        listener?.newConnectionHandler = nil; listener?.cancel(); listener = nil
        for task in deadlines.values { task.cancel() }; deadlines.removeAll()
        for task in guestTasks.values { task.cancel() }; guestTasks.removeAll()
        for connection in connections.values { connection.cancel() }; connections.removeAll()
        executing = nil; guests.removeAll(); guestCount = 0; code = ""; address = ""; fingerprint = ""; assets.removeAll()
        status = "Browser companion is off"; BrowserRemoteIdentity.remove()
    }
    public func start(address localAddress: String, assets: [String: Data]) {
        guard !preparing, !active else { return }
        stop()
        guard (try? CompanionProtocol.privateAddress(localAddress)) != nil,
              Set(assets.keys) == Set(["/", "/remote.js", "/remote.css"]), assets.values.allSatisfy({ $0.count <= 131072 }) else {
            status = "Select this device's private Wi-Fi IPv4 address."; return
        }
        let run = generation; self.assets = assets; status = "Preparing private HTTPS host…"
        preparation = Task { [weak self] in
            do {
                let material = try await Task.detached(priority: .userInitiated) { try BrowserRemoteIdentity.create(address: localAddress) }.value
                guard let self, self.generation == run, !Task.isCancelled else { return }
                let identity = try BrowserRemoteIdentity.install(material)
                guard let localIdentity = sec_identity_create(identity) else { throw CompanionError.invalidInput }
                let tls = NWProtocolTLS.Options()
                sec_protocol_options_set_min_tls_protocol_version(tls.securityProtocolOptions, .TLSv12)
                sec_protocol_options_set_local_identity(tls.securityProtocolOptions, localIdentity)
                let parameters = NWParameters(tls: tls, tcp: NWProtocolTCP.Options())
                parameters.requiredLocalEndpoint = .hostPort(host: NWEndpoint.Host(localAddress), port: 45732)
                let listener = try NWListener(using: parameters)
                self.preparation = nil
                self.listener = listener; self.started = .now; self.failures = 0
                self.code = SymmetricKey(size: .bits128).withUnsafeBytes { Data($0).map { String(format: "%02x", $0) }.joined() }
                self.fingerprint = SHA256.hash(data: material.certificate).map { String(format: "%02x", $0) }.joined()
                self.address = "https://\(localAddress):45732"
                listener.stateUpdateHandler = { [weak self] state in
                    Task { @MainActor in
                        guard let self, self.generation == run else { return }
                        switch state {
                        case .ready: self.status = "HTTPS host ready · up to four paired guests"
                        case .failed: self.stop(); self.status = "Could not bind HTTPS hosting. Check this device's Wi-Fi address."
                        default: break
                        }
                    }
                }
                listener.newConnectionHandler = { [weak self] connection in
                    Task { @MainActor in self?.accept(connection, run: run) }
                }
                listener.start(queue: self.queue)
                self.expiry = Task { [weak self] in
                    do { try await Task.sleep(for: .seconds(1800)) } catch { return }
                    guard let self, self.generation == run else { return }
                    self.stop(); self.status = "Host expired. Start again for a new certificate and code."
                }
            } catch {
                guard let self, self.generation == run else { return }
                self.stop(); self.status = "Could not prepare the local HTTPS identity."
            }
        }
    }
    private func accept(_ connection: NWConnection, run: UUID) {
        guard generation == run, active, connections.count < 4,
              case let .hostPort(host, _) = connection.endpoint,
              (try? CompanionProtocol.privateAddress(String(describing: host))) != nil else { connection.cancel(); return }
        let id = UUID(); connections[id] = connection
        deadlines[id] = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(8)) } catch { return }
            self?.finish(id)
        }
        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self, self.generation == run else { return }
                switch state {
                case .ready: self.receive(connection, id: id, bytes: Data(), run: run)
                case .failed, .cancelled: self.finish(id)
                default: break
                }
            }
        }
        connection.start(queue: queue)
    }
    private func finish(_ id: UUID) {
        deadlines.removeValue(forKey: id)?.cancel()
        guestTasks.removeValue(forKey: id)?.cancel()
        connections.removeValue(forKey: id)?.cancel()
    }
    private func receive(_ connection: NWConnection, id: UUID, bytes: Data, run: UUID) {
        guard generation == run, connections[id] != nil else { return }
        connection.receive(minimumIncompleteLength: 1, maximumLength: 9217 - bytes.count) { [weak self] next, _, done, error in
            Task { @MainActor in
                guard let self, self.generation == run, self.connections[id] != nil else { return }
                guard error == nil, let next, !next.isEmpty else { self.finish(id); return }
                let accumulated = bytes + next
                do {
                    guard let request = try BrowserRemoteProtocol.parseHTTP(accumulated, authority: String(self.address.dropFirst(8))) else {
                        if done { self.finish(id) } else { self.receive(connection, id: id, bytes: accumulated, run: run) }; return
                    }
                    self.guestTasks[id] = Task { [weak self] in
                        guard let self else { return }
                        await self.respond(request, connection: connection, id: id, run: run)
                    }
                } catch { self.finish(id) }
            }
        }
    }
    private func respond(_ request: BrowserRemoteProtocol.HTTPRequest, connection: NWConnection, id: UUID, run: UUID) async {
        guard generation == run, active, connections[id] != nil else { return }
        let origin = request.headers["origin"]
        if let origin, origin != address { reply(403, Data(), "text/plain", connection, id); return }
        if let site = request.headers["sec-fetch-site"], !["same-origin", "none"].contains(site) { reply(403, Data(), "text/plain", connection, id); return }
        if request.method == "GET", request.body.isEmpty {
            guard let asset = assets[request.path] else { reply(404, Data(), "text/plain", connection, id); return }
            let type = request.path == "/" ? "text/html; charset=utf-8" : (request.path == "/remote.js" ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8")
            reply(200, asset, type, connection, id); return
        }
        guard request.method == "POST", request.headers["content-type"] == "application/octet-stream", origin == address else { reply(403, Data(), "text/plain", connection, id); return }
        guests = guests.filter { $0.value.valid }; guestCount = guests.count
        if request.path == "/pair" {
            let expected = Data(code.utf8), supplied = request.body
            let matches = expected.count == supplied.count && zip(expected, supplied).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
            guard failures < 3, started.duration(to: .now) < .seconds(300), guests.count < 4, matches else {
                failures += 1; reply(403, Data(), "text/plain", connection, id); return
            }
            let token = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0).map { String(format: "%02x", $0) }.joined() }
            guests[token] = Guest(); guestCount = guests.count
            reply(200, Data(token.utf8), "text/plain", connection, id); return
        }
        let authorization = request.headers["authorization"] ?? ""
        let token = authorization.hasPrefix("Bearer ") ? String(authorization.dropFirst(7)) : ""
        guard let guest = guests[token], guest.valid else { reply(401, Data(), "text/plain", connection, id); return }
        if request.path == "/leave" { guests[token] = nil; guestCount = guests.count; reply(200, Data(), "text/plain", connection, id); return }
        guard request.path == "/command", let command = try? BrowserRemoteProtocol.decode(request.body) else { reply(400, Data(), "text/plain", connection, id); return }
        if let old = guest.replies[command.id] {
            if old.input != request.body { reply(409, Data(), "text/plain", connection, id) }
            else { reply(200, old.output, "application/json", connection, id) }; return
        }
        guard guest.replies.count < 512, guest.bytes <= 1024 * 1024 - 16384 else { guests[token] = nil; guestCount = guests.count; reply(401, Data(), "text/plain", connection, id); return }
        guard executing == nil else { reply(409, Data(), "text/plain", connection, id); return }
        let transaction = UUID(); executing = transaction
        var response: Data
        do { response = try await handler(command) }
        catch { response = Data("{\"error\":\"Host could not complete the command. Refresh the queue before trying again.\"}".utf8) }
        if response.count > 16384 { response = Data("{\"error\":\"Host response exceeded the metadata limit. Refresh the queue before trying again.\"}".utf8) }
        if executing == transaction { executing = nil }
        guard generation == run, response.count <= 16384, var current = guests[token], current.valid else { finish(id); return }
        current.replies[command.id] = Cached(input: request.body, output: response); current.bytes += request.body.count + response.count
        guests[token] = current
        guard connections[id] != nil else { return }
        reply(200, response, "application/json", connection, id)
    }
    private func reply(_ status: Int, _ body: Data, _ type: String, _ connection: NWConnection, _ id: UUID) {
        let header = "HTTP/1.1 \(status) \(status == 200 ? "OK" : "Rejected")\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nPermissions-Policy: camera=(), microphone=(), geolocation=()\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n\r\n"
        connection.send(content: Data(header.utf8) + body, completion: .contentProcessed { [weak self] _ in Task { @MainActor in self?.finish(id) } })
    }
}
