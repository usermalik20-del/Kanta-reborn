import Foundation
import Security
import Testing
@_spi(Testing) @testable import LocalNetworkControl

struct BrowserRemoteProtocolTests {
    @Test func boundedCommandsPreserveIdentityAndUnicode() throws {
        let id = UUID()
        let bytes = try CompanionProtocol.request(id: id, action: "search", argument: "{Tala} 🎵", singer: "José")
        let decoded = try BrowserRemoteProtocol.decode(bytes)
        #expect(decoded.id == id)
        #expect(decoded.action == "search")
        #expect(decoded.argument == "{Tala} 🎵")
        #expect(decoded.singer == "José")
        #expect(throws: (any Error).self) { try BrowserRemoteProtocol.decode(bytes + Data([0])) }
        #expect(throws: (any Error).self) { try BrowserRemoteProtocol.decode(Data(repeating: 0, count: 1025)) }
    }
    @Test func httpFramingRejectsSmugglingAndWrongAuthority() throws {
        let prefix = "POST /pair HTTP/1.1\r\nHost: 192.168.1.20:45732\r\nContent-Type: application/octet-stream\r\nContent-Length: 4\r\n\r\n"
        let authority = "192.168.1.20:45732"
        #expect(try BrowserRemoteProtocol.parseHTTP(Data(prefix.utf8), authority: authority) == nil)
        let complete = try BrowserRemoteProtocol.parseHTTP(Data((prefix + "abcd").utf8), authority: authority)
        let parsed = try #require(complete)
        #expect(parsed.body == Data("abcd".utf8))
        #expect(parsed.path == "/pair")
        for text in [prefix + "abcde", prefix.replacingOccurrences(of: "Content-Length: 4", with: "Content-Length: 4\r\nContent-Length: 4") + "abcd", prefix.replacingOccurrences(of: "Content-Length: 4", with: "Transfer-Encoding: chunked"), prefix.replacingOccurrences(of: authority, with: "attacker.example"), String(repeating: "x", count: 8192)] {
            #expect(throws: (any Error).self) { try BrowserRemoteProtocol.parseHTTP(Data(text.utf8), authority: authority) }
        }
    }
    @Test func sessionIdentityCanBeRetrievedAndRemoved() throws {
        let material = try BrowserRemoteIdentity.create(address: "192.168.1.20")
        defer { BrowserRemoteIdentity.remove() }
        let identity: SecIdentity
        do {
            identity = try BrowserRemoteIdentity.install(material)
        } catch let error as NSError {
            #if os(macOS)
            // The unsigned macOS SwiftPM runner has no Keychain entitlement. The iOS app-hosted
            // XCTest exercises installation and removal under the actual simulator security context.
            guard error.domain == NSOSStatusErrorDomain,
                  error.code == Int(errSecMissingEntitlement) else { throw error }
            return
            #else
            throw error
            #endif
        }
        var certificate: SecCertificate?
        #expect(SecIdentityCopyCertificate(identity, &certificate) == errSecSuccess)
        let installed = try #require(certificate)
        #expect(SecCertificateCopyData(installed) as Data == material.certificate)
        var key: SecKey?
        #expect(SecIdentityCopyPrivateKey(identity, &key) == errSecSuccess)
        #expect(key != nil)
    }
    @Test func sessionCertificateContainsMatchingKeyAndExpires() throws {
        let material = try BrowserRemoteIdentity.create(address: "192.168.1.20")
        #expect(!material.privateKey.isEmpty)
        let certificate = try #require(SecCertificateCreateWithData(nil, material.certificate as CFData))
        var trust: SecTrust?
        #expect(SecTrustCreateWithCertificates(certificate, SecPolicyCreateSSL(true, "192.168.1.20" as CFString), &trust) == errSecSuccess)
        let evaluated = try #require(trust)
        #expect(SecTrustSetAnchorCertificates(evaluated, [certificate] as CFArray) == errSecSuccess)
        #expect(SecTrustSetAnchorCertificatesOnly(evaluated, true) == errSecSuccess)
        #expect(SecTrustEvaluateWithError(evaluated, nil))
        let key = try #require(SecKeyCreateWithData(material.privateKey as CFData,
            [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPrivate] as CFDictionary, nil))
        let publicKey = try #require(SecCertificateCopyKey(certificate))
        let message = Data("ephemeral-key-match".utf8)
        let signature = try #require(SecKeyCreateSignature(key, .rsaSignatureMessagePKCS1v15SHA256, message as CFData, nil))
        #expect(SecKeyVerifySignature(publicKey, .rsaSignatureMessagePKCS1v15SHA256, message as CFData, signature, nil))
        #expect(material.certificate != (try BrowserRemoteIdentity.create(address: "192.168.1.20")).certificate)
        #expect(throws: (any Error).self) { try BrowserRemoteIdentity.create(address: "8.8.8.8") }
    }
}
