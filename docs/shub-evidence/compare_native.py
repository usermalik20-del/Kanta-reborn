"""Read-only native key comparison; never prints or persists key/IV bytes."""
import hashlib, json, re, zipfile
from pathlib import Path
import lief
import argparse
import subprocess
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-ref', default='ffc780fe297f100cf351ef25e9402e650cf2f25b',
                    help='Historical Git revision containing the pre-runtime-key importer')
parser.add_argument('apk', type=Path)
parser.add_argument('--repo', type=Path, required=True)
args = parser.parse_args()
apk, repo = args.apk, args.repo
with zipfile.ZipFile(apk) as z:
    native = z.read('lib/arm64-v8a/libMewtwo.so')
if hashlib.sha256(native).hexdigest() != 'db9d2fbe0b0260486b66aec60853794147eb3959af6f843e32f3eee404024495':
    raise SystemExit('Unrecognized native library; re-trace addresses before comparison.')
elf = lief.ELF.parse(list(native))
def read_va(va, size):
    return bytes(elf.get_content_from_virtual_address(va, size))
# ARM64 processMidiData 0x3044..0x3070 loads q0/q1 as key and q2 as IV.
key = read_va(0x1200, 16) + read_va(0x1220, 16)
iv = read_va(0x11e0, 16)
source_path = 'apps/android/app/src/main/java/ph/kanta/reborn/core/legacy/LegacyFileContainerImporter.java'
source = subprocess.run(['git', '-C', str(repo), 'show', args.source_ref + ':' + source_path],
                        check=True, capture_output=True, text=True).stdout
existing_key = bytes.fromhex(re.search(r'KEY = hex\("([0-9a-f]+)"\)', source)[1])
existing_iv = re.search(r'IV = "([^"]+)"', source)[1].encode('ascii')
report = dict(apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), native_sha256=hashlib.sha256(native).hexdigest(), abi='arm64-v8a', decoder_va='0x2ef8', aes_init_call_va='0x3070', key_source_vas=['0x1200','0x1220'], iv_source_va='0x11e0', compared_key_bytes=len(key), key_matches_existing_importer=(key == existing_key), iv_matches_existing_importer=(iv == existing_iv), method='static ARM64 argument tracing and ELF virtual-address reads', runtime_tested=False)
report['source_ref'] = args.source_ref
out = repo/'docs/shub-evidence'
out.mkdir(exist_ok=True)
(out/'native-comparison.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report, indent=2))
