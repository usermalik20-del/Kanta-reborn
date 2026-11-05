#!/usr/bin/env python3
"""Check the built preview's permission and offline-notice boundaries."""
import os
from pathlib import Path
import re
import subprocess
import zipfile

apk = Path('apps/android/app/build/outputs/apk/release/app-release.apk')
aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/aapt'
report = subprocess.check_output([str(aapt), 'dump', 'permissions', str(apk)], text=True, timeout=30)
permissions = set(re.findall(r"uses-permission: name='([^']+)'", report))
platform_permissions = {name for name in permissions if name.startswith('android.permission.')}
assert platform_permissions == {'android.permission.CAMERA', 'android.permission.RECORD_AUDIO', 'android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES'}, platform_permissions
with zipfile.ZipFile(apk) as package:
    notices = package.read('assets/runtime-notices.txt').decode()
    for module in ('camera-core', 'camera-camera2', 'camera-lifecycle', 'camera-view'):
        assert 'androidx.camera:' + module + ':1.5.3' in notices, module
    assert 'Apache License' in notices and 'Version 2.0' in notices
    assert 'TERMS AND CONDITIONS' in notices
    privacy = package.read('assets/privacy.txt').decode()
    assert 'Leaving the foreground turns it off' in privacy
    assert 'Microphone input is optional' in privacy
    assert 'no microphone audio is saved or uploaded' in privacy
print('APK: CAMERA/RECORD_AUDIO/INTERNET/REQUEST_INSTALL_PACKAGES platform permissions and exact upstream offline runtime notices passed')
