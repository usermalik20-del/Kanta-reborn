"""Guard the older-Android compatibility anchor without relaxing TLS."""
import hashlib
from pathlib import Path
import ssl
import unittest
import xml.etree.ElementTree as ET

MAIN = Path(__file__).resolve().parents[2] / 'apps/android/app/src/main'
ANDROID = '{http://schemas.android.com/apk/res/android}'


class SoundFontTlsTests(unittest.TestCase):
    def test_only_osuosl_gets_the_additional_public_root(self):
        app = ET.parse(MAIN / 'AndroidManifest.xml').getroot().find('application')
        self.assertEqual(app.get(ANDROID + 'networkSecurityConfig'), '@xml/network_security_config')
        config = ET.parse(MAIN / 'res/xml/network_security_config.xml').getroot()
        self.assertEqual([child.tag for child in config], ['base-config', 'domain-config'])
        base, domain_config = config
        self.assertEqual(base.attrib, {'cleartextTrafficPermitted': 'false'})
        self.assertEqual(domain_config.attrib, {'cleartextTrafficPermitted': 'false'})
        domains = domain_config.findall('domain')
        self.assertEqual([(d.text, d.attrib) for d in domains],
                         [('ftp.osuosl.org', {'includeSubdomains': 'false'})])
        self.assertEqual([c.attrib for c in domain_config.findall('trust-anchors/certificates')],
                         [{'src': 'system'}, {'src': '@raw/osuosl_tls_rsa_root_2022'}])
        pem = (MAIN / 'res/raw/osuosl_tls_rsa_root_2022.pem').read_text()
        self.assertEqual(hashlib.sha256(ssl.PEM_cert_to_DER_cert(pem)).hexdigest(),
                         '8faf7d2e2cb4709bb8e0b33666bf75a5dd45b5de480f8ea8d4bfe6bebc17f2ed')


if __name__ == '__main__':
    unittest.main()
