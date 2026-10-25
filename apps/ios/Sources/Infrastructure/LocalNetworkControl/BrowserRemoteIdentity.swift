import CryptoKit
import Foundation
import Security

/// Session-only local certificate. Private material never leaves the device or enters app backups.
@_spi(Testing) public enum BrowserRemoteIdentity {
    @_spi(Testing) public struct Material: Sendable {
        let privateKey: Data
        @_spi(Testing) public let certificate: Data
    }
    @_spi(Testing) public static let label = "ph.kanta.browser.session.identity"
    static let tag = Data(label.utf8)
    @_spi(Testing) public static func remove() {
        SecItemDelete([kSecClass: kSecClassCertificate, kSecAttrLabel: label] as CFDictionary)
        SecItemDelete([kSecClass: kSecClassKey, kSecAttrApplicationTag: tag] as CFDictionary)
    }
    @_spi(Testing) public static func install(_ material: Material) throws -> SecIdentity {
        remove()
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(material.privateKey as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPrivate] as CFDictionary, &error),
              let certificate = SecCertificateCreateWithData(nil, material.certificate as CFData) else { throw CompanionError.invalidInput }
        var keyAttributes: [CFString: Any] = [
            kSecClass: kSecClassKey, kSecValueRef: key, kSecAttrApplicationTag: tag
        ]
        var certificateAttributes: [CFString: Any] = [
            kSecClass: kSecClassCertificate, kSecValueRef: certificate, kSecAttrLabel: label
        ]
        #if !os(macOS)
        keyAttributes[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        certificateAttributes[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        #endif
        let keyStatus = SecItemAdd(keyAttributes as CFDictionary, nil)
        guard keyStatus == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(keyStatus),
                          userInfo: [NSLocalizedDescriptionKey: "Could not install the session key"])
        }
        let certificateStatus = SecItemAdd(certificateAttributes as CFDictionary, nil)
        guard certificateStatus == errSecSuccess else {
            remove()
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(certificateStatus),
                          userInfo: [NSLocalizedDescriptionKey: "Could not install the session certificate"])
        }
        var matches: CFTypeRef?
        let lookupStatus = SecItemCopyMatching([
            kSecClass: kSecClassIdentity, kSecMatchLimit: kSecMatchLimitAll, kSecReturnRef: true
        ] as CFDictionary, &matches)
        if lookupStatus == errSecSuccess, let identities = matches as? [SecIdentity] {
            for identity in identities {
                var installedCertificate: SecCertificate?
                guard SecIdentityCopyCertificate(identity, &installedCertificate) == errSecSuccess,
                      let installedCertificate else { continue }
                if SecCertificateCopyData(installedCertificate) as Data == material.certificate {
                    return identity
                }
            }
        }
        remove()
        guard lookupStatus == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(lookupStatus),
                          userInfo: [NSLocalizedDescriptionKey: "Could not locate the session identity"])
        }
        throw CompanionError.invalidInput
    }
    @_spi(Testing) public static func create(address: String) throws -> Material {
        _ = try CompanionProtocol.privateAddress(address)
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateRandomKey([kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeySizeInBits: 2048] as CFDictionary, &error),
              let publicKey = SecKeyCopyPublicKey(key),
              let publicBytes = SecKeyCopyExternalRepresentation(publicKey, &error) as Data?,
              let privateBytes = SecKeyCopyExternalRepresentation(key, &error) as Data? else { throw CompanionError.invalidInput }
        let algorithm = hex("300d06092a864886f70d01010b0500")
        let name = der(0x30, der(0x31, der(0x30, hex("0603550403"), der(0x0c, Data("KANTA LAN Remote".utf8)))))
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.timeZone = TimeZone(secondsFromGMT: 0); formatter.dateFormat = "yyMMddHHmmss'Z'"
        let validity = der(0x30, der(0x17, Data(formatter.string(from: Date().addingTimeInterval(-300)).utf8)), der(0x17, Data(formatter.string(from: Date().addingTimeInterval(86400)).utf8)))
        var serial = Array(SymmetricKey(size: .bits128).withUnsafeBytes { Data($0) }); serial[0] = (serial[0] & 0x7f) | 1
        let ip = Data(address.split(separator: ".").compactMap { UInt8($0) })
        let san = der(0x30, hex("0603551d11"), der(0x04, der(0x30, der(0x87, ip))))
        let extended = der(0x30, hex("0603551d25"), der(0x04, hex("300a06082b06010505070301")))
        let spki = der(0x30, hex("300d06092a864886f70d0101010500"), der(0x03, Data([0]), publicBytes))
        let tbs = der(0x30, hex("a003020102"), der(0x02, Data(serial)), algorithm, name, validity, name, spki, der(0xa3, der(0x30, san, extended)))
        guard let signature = SecKeyCreateSignature(key, .rsaSignatureMessagePKCS1v15SHA256, tbs as CFData, &error) as Data? else { throw CompanionError.invalidInput }
        return Material(privateKey: privateBytes, certificate: der(0x30, tbs, algorithm, der(0x03, Data([0]), signature)))
    }
    private static func hex(_ text: String) -> Data {
        let bytes = Array(text.utf8); return Data(stride(from: 0, to: bytes.count, by: 2).map { UInt8(String(bytes: bytes[$0..<$0+2], encoding: .utf8)!, radix: 16)! })
    }
    private static func der(_ tag: UInt8, _ parts: Data...) -> Data {
        let body = parts.reduce(Data(), +)
        var result = Data([tag])
        if body.count < 128 { result.append(UInt8(body.count)) }
        else if body.count <= 255 { result += Data([0x81, UInt8(body.count)]) }
        else { result += Data([0x82, UInt8(body.count >> 8), UInt8(body.count & 255)]) }
        return result + body
    }
}
