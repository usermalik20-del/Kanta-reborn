"""Run the framework-independent SoundFont installer against a local HTTP fixture."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2] / "apps/android/app/src"
BANK = bytes((1, 2, 3, 4))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path in ("/redirect", "/loop"):
            self.send_response(307)
            self.send_header("Location", "/direct" if self.path == "/redirect" else "/loop")
            self.end_headers()
            return
        if self.path == "/missing":
            self.send_error(404)
            return
        data = BANK
        if self.path in ("/zip", "/wrong-zip"):
            buf = BytesIO()
            with ZipFile(buf, "w") as archive:
                archive.writestr("unrelated.sf2", b"not this member")
                if self.path == "/zip":
                    archive.writestr("FluidR3 GM2-2.SF2", BANK)
            data = buf.getvalue()
        self.send_response(200)
        self.send_header("Content-Length", str(len(data) + (10 if self.path == "/short" else 0)))
        self.end_headers()
        self.wfile.write(data)


class SoundFontDownloadTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("java") and shutil.which("javac"), "requires host JDK")
    def test_install_and_cleanup(self):
        with tempfile.TemporaryDirectory() as temp, ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            base = Path(temp)
            media = base / "media"
            media.mkdir()
            subprocess.run(["javac", "--release", "8", "-d", temp,
                            ROOT / "main/java/ph/kanta/reborn/SoundFontDownload.java",
                            ROOT / "test/java/ph/kanta/reborn/SoundFontDownloadRegressionTests.java"],
                           check=True, timeout=45)
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                subprocess.run(["java", "-cp", temp, "ph.kanta.reborn.SoundFontDownloadRegressionTests",
                                media, f"http://127.0.0.1:{server.server_port}"], check=True, timeout=45)
            finally:
                server.shutdown()
                worker.join()


if __name__ == "__main__":
    unittest.main()
