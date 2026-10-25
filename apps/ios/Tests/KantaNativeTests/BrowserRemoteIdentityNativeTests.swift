import Foundation
@_spi(Testing) import LocalNetworkControl
import Security
import XCTest

/// Exercises the app-hosted Keychain entitlement path, which unsigned macOS SwiftPM cannot use.
final class BrowserRemoteIdentityNativeTests: XCTestCase {
    func testSessionIdentityInstallationAndRemoval() throws {
        let material = try BrowserRemoteIdentity.create(address: "192.168.1.20")
        defer { BrowserRemoteIdentity.remove() }
        let identity = try BrowserRemoteIdentity.install(material)

        var certificate: SecCertificate?
        XCTAssertEqual(SecIdentityCopyCertificate(identity, &certificate), errSecSuccess)
        let installed = try XCTUnwrap(certificate)
        XCTAssertEqual(SecCertificateCopyData(installed) as Data, material.certificate)

        var privateKey: SecKey?
        XCTAssertEqual(SecIdentityCopyPrivateKey(identity, &privateKey), errSecSuccess)
        XCTAssertNotNil(privateKey)

        BrowserRemoteIdentity.remove()
        var result: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass: kSecClassCertificate,
            kSecAttrLabel: BrowserRemoteIdentity.label,
            kSecReturnRef: true
        ] as CFDictionary, &result)
        XCTAssertEqual(status, errSecItemNotFound)
    }
}
