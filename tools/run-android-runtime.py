#!/usr/bin/env python3
"""Run clean-install Android acceptance with bounded startup and retained diagnostics."""
from __future__ import annotations
import os
import ipaddress
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]

def run(*args, **kwargs):
    print("+", " ".join(map(str, args)), flush=True)
    return subprocess.run(list(map(str, args)), check=True, **kwargs)

def main() -> int:
    sdk = Path(os.environ["ANDROID_HOME"])
    adb = sdk / "platform-tools/adb"
    env = dict(os.environ)
    env["ANDROID_AVD_HOME"] = tempfile.mkdtemp(prefix="kanta-avd-")
    env["QT_QPA_PLATFORM"] = "offscreen"
    emulator = None
    try:
        run(sdk / "cmdline-tools/latest/bin/sdkmanager", "platform-tools", "emulator",
            "system-images;android-35;google_apis;x86_64", timeout=300)
        run(sdk / "cmdline-tools/latest/bin/avdmanager", "create", "avd", "--force", "-n", "kanta-test",
            "-k", "system-images;android-35;google_apis;x86_64", "--device", "pixel_6",
            env=env, input="no\n", text=True, timeout=60)
        run("sudo", "chmod", "666", "/dev/kvm", timeout=10)
        # Ensure clean adb state
        run(adb, "kill-server", timeout=10)
        run(adb, "start-server", timeout=10)
        with (ROOT / "emulator.log").open("wb") as log:
            emulator = subprocess.Popen([str(sdk / "emulator/emulator"), "-avd", "kanta-test", "-no-window",
                "-no-audio", "-no-snapshot", "-no-boot-anim", "-gpu", "swiftshader", "-accel", "on",
                "-cores", "2", "-memory", "2048", "-camera-front", "emulated"], env=env, stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 600  # 10 minutes for emulator boot
            booted = False
            while time.monotonic() < deadline:
                if emulator.poll() is not None: raise RuntimeError(f"Emulator exited before boot ({emulator.returncode})")
                try:
                    state = subprocess.run([str(adb), "shell", "getprop", "sys.boot_completed"],
                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=8)
                    if state.returncode == 0 and state.stdout.strip() == "1": booted = True; break
                except subprocess.TimeoutExpired: pass
                time.sleep(2)
            if not booted: raise RuntimeError("Emulator did not boot within ten minutes")
        # Wait a bit more for system to settle
        time.sleep(10)
        run(adb, "shell", "svc", "wifi", "enable", timeout=15)

        private_ranges = tuple(map(ipaddress.ip_network, ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")))
        network_ok = False
        for attempt in range(3):
            network_deadline = time.monotonic() + 180
            while time.monotonic() < network_deadline:
                try:
                    addresses = subprocess.run([str(adb), "shell", "ip", "-4", "-o", "addr", "show"],
                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=8)
                except subprocess.TimeoutExpired:
                    time.sleep(2)
                    continue
                if any(ipaddress.ip_interface(token).ip in private_ranges[0]
                       or any(ipaddress.ip_interface(token).ip in network for network in private_ranges[1:])
                       for token in addresses.stdout.split()
                       if "/" in token):
                    network_ok = True
                    break
                time.sleep(2)
            if network_ok:
                break
            print(f"Network not ready, attempt {attempt+1}/3", flush=True)
            run(adb, "shell", "svc", "wifi", "disable", timeout=10)
            time.sleep(2)
            run(adb, "shell", "svc", "wifi", "enable", timeout=15)
        if not network_ok:
            raise RuntimeError(f"Emulator has no private IPv4 after Wi-Fi enable after retries")
        run(adb, "shell", "input", "keyevent", "82", timeout=15)
        run(adb, "install", ROOT / "apps/android/app/build/outputs/apk/debug/app-debug.apk", timeout=60)
        run(adb, "install", ROOT / "apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk", timeout=60)
        with (ROOT / "android-runtime.log").open("wb") as output:
            run(adb, "shell", "am", "instrument", "-w", "ph.kanta.reborn.test/ph.kanta.reborn.RuntimeChecks",
                stdout=output, stderr=subprocess.STDOUT, timeout=180)
        result = (ROOT / "android-runtime.log").read_text(errors="replace")
        print(result, flush=True)
        if "KANTA runtime:" not in result or "checks passed" not in result:
            raise RuntimeError("Runtime instrumentation did not report acceptance")
        # Live UI screenshot capture via adb screencap (captures emulator display)
        try:
            result_capture = subprocess.run(
                [str(adb), "shell", "screencap", "-p"],
                capture_output=True, timeout=30
            )
            if result_capture.returncode == 0 and result_capture.stdout:
                with (ROOT / "android-runtime-stage.png").open("wb") as f:
                    f.write(result_capture.stdout)
                print("Stage screenshot captured via screencap", flush=True)
            else:
                raise RuntimeError("screencap failed")
        except Exception:
            # Fallback: read from app private files (original behavior)
            print("screencap failed, falling back to file-read", flush=True)
            with (ROOT / "android-runtime-stage.png").open("wb") as output:
                run(adb, "exec-out", "run-as", "ph.kanta.reborn", "cat", "files/runtime-stage.png", stdout=output, timeout=20)
        if (ROOT / "android-runtime-stage.png").stat().st_size == 0: raise RuntimeError("Missing real stage capture")
        # Camera capture: fallback to app private file (screencap captures emulator display only)
        # Read new UI walkthrough screenshots captured by RuntimeChecks.java
        screenshots = ROOT / "files"
        screenshots.mkdir(exist_ok=True)
        for screenshot_name in [
            "library_ready.png",
            "fixture_loaded.png",
            "ui_rendered.png",
            "youtube_loaded.png",
            "stage_ready.png",
            "camera_optin.png",
            "voice_settings.png",
            "final_ui.png",
        ]:
            screenshot_path = screenshots / screenshot_name
            print(f"Reading required UI capture {screenshot_name} from app files", flush=True)
            with screenshot_path.open("wb") as output:
                run(adb, "exec-out", "run-as", "ph.kanta.reborn", "cat", f"files/{screenshot_name}", stdout=output, timeout=20)
            if screenshot_path.stat().st_size == 0:
                raise RuntimeError(f"Missing UI walkthrough screenshot: {screenshot_name}")
        
        # Camera capture remains a separate app-private runtime artifact.
        with (ROOT / "android-runtime-camera.png").open("wb") as output:
            run(adb, "exec-out", "run-as", "ph.kanta.reborn", "cat", "files/runtime-camera.png", stdout=output, timeout=20)
        if (ROOT / "android-runtime-camera.png").stat().st_size == 0: raise RuntimeError("Missing real camera capture")
        return 0
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Android runtime acceptance failed: {error}", flush=True)
        for name in ("emulator.log", "android-runtime.log"):
            path = ROOT / name
            if path.exists(): print(name + ":\n" + "\n".join(path.read_text(errors="replace").splitlines()[-50:]), flush=True)
        return 1
    finally:
        if adb.exists():
            try:
                with (ROOT / "android-logcat.log").open("wb") as output:
                    subprocess.run([str(adb), "logcat", "-d"], stdout=output, stderr=subprocess.STDOUT, timeout=20)
            except (OSError, subprocess.TimeoutExpired): pass
        if emulator is not None and emulator.poll() is None:
            emulator.terminate()
            try: emulator.wait(timeout=15)
            except subprocess.TimeoutExpired: emulator.kill(); emulator.wait()

if __name__ == "__main__": raise SystemExit(main())
