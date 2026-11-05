#!/usr/bin/env python3
"""Fail-closed OTA APK metadata and signer continuity gate."""
from __future__ import annotations
import argparse, hashlib, json, re, subprocess, sys, tempfile, urllib.request
from pathlib import Path
PACKAGE="ph.kanta.reborn"; HEX64=re.compile(r"[0-9a-f]{64}")
APPROVED_RELEASE_SIGNER_SHA256="04488bab3deaf6c9702605e27b7e5469e5f1b6bcf2d440b0bd2b507202d385fc"
class OtaError(ValueError): pass
def run(cmd):
 try: r=subprocess.run(cmd,capture_output=True,text=True,check=True,timeout=60)
 except (OSError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as e: raise OtaError("APK metadata command failed") from e
 return r.stdout+r.stderr
def parse_badging(text):
 line=next((x for x in text.splitlines() if x.startswith("package:")),"")
 v=dict(re.findall(r"(?<![A-Za-z])(versionCode|versionName|name|sdkVersion)(?:=|:)'([^']*)'",line))
 if v.get("name")!=PACKAGE: raise OtaError("APK package identity is "+str(v.get("name"))+" not "+PACKAGE)
 # Modern aapt puts sdkVersion on its own line. Never substitute
 # platformBuildVersionCode here: that is compile SDK metadata, not minSdk.
 sdk_match=re.search(r"(?m)^sdkVersion(?:=|:)'([0-9]+)'\s*$",text)
 sdk_raw=sdk_match.group(1) if sdk_match else v.get("sdkVersion")
 try: code=int(v["versionCode"]); sdk=int(sdk_raw)
 except (KeyError,TypeError,ValueError) as e: raise OtaError("aapt badging is missing numeric versionCode or sdkVersion") from e
 if code<1 or code>2100000000 or sdk<26 or not re.fullmatch(r"[A-Za-z0-9.+-]{1,64}",v.get("versionName","")): raise OtaError("APK version metadata is invalid")
 return {"versionCode":code,"versionName":v["versionName"],"minSdk":sdk}

def parse_signer(text):
 m=re.findall(r"certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})",text)
 if len(m)!=1: raise OtaError("APK must have exactly one signer certificate")
 return m[0].lower()
def inspect_apk(apk,aapt="aapt",apksigner="apksigner"):
 if not apk.is_file() or apk.stat().st_size<1: raise OtaError("missing or empty APK")
 d=parse_badging(run([aapt,"dump","badging",str(apk)])); d["signerSha256"]=parse_signer(run([apksigner,"verify","--print-certs",str(apk)])); d["apkSha256"]=hashlib.sha256(apk.read_bytes()).hexdigest(); d["apkBytes"]=apk.stat().st_size; return d
def previous_signer(feed,aapt,apksigner):
 a=feed.get("android")
 if not isinstance(a,dict) or not isinstance(a.get("apkUrl"),str) or not a["apkUrl"].startswith("https://") or not isinstance(a.get("apkSha256"),str) or not HEX64.fullmatch(a["apkSha256"]): raise OtaError("previous feed lacks pinned HTTPS APK and SHA-256")
 with tempfile.TemporaryDirectory() as d:
  path=Path(d)/"previous.apk"
  try:
   with urllib.request.urlopen(a["apkUrl"],timeout=60) as r, path.open("wb") as out:
    while chunk:=r.read(1024*1024): out.write(chunk)
  except Exception as e: raise OtaError("cannot retrieve pinned previous APK") from e
  if hashlib.sha256(path.read_bytes()).hexdigest()!=a["apkSha256"]: raise OtaError("previous APK digest mismatch")
  return inspect_apk(path,aapt,apksigner)["signerSha256"]
def verify(candidate, old_feed, aapt="aapt", apksigner="apksigner", approved_signer_sha256=None):
 if old_feed is None: raise OtaError("no previous APK; persistent owner signing key required before bootstrap")
 signer=previous_signer(old_feed,aapt,apksigner)
 actual=candidate.get("signerSha256")
 if approved_signer_sha256 is not None:
  if not isinstance(approved_signer_sha256,str) or not HEX64.fullmatch(approved_signer_sha256): raise OtaError("approved signer fingerprint is invalid")
  if actual!=approved_signer_sha256: raise OtaError("APK signer differs from the explicitly pinned new release identity")
  return {**candidate,"previousSignerSha256":signer,"signerTransition":actual!=signer}
 if actual!=signer: raise OtaError("APK signer changed; refusing silent key bootstrap or rotation")
 return {**candidate,"previousSignerSha256":signer,"signerTransition":False}
def main():
 p=argparse.ArgumentParser(); p.add_argument("apk",type=Path); p.add_argument("--previous-feed",type=Path,required=True); p.add_argument("--output",type=Path,required=True); p.add_argument("--expected-version-code",type=int,required=True); p.add_argument("--source",required=True); p.add_argument("--aapt",default="aapt"); p.add_argument("--apksigner",default="apksigner"); a=p.parse_args()
 d=inspect_apk(a.apk,a.aapt,a.apksigner)
 if d["versionCode"]!=a.expected_version_code: raise OtaError("APK versionCode does not equal CI allocation")
 if not a.previous_feed.is_file(): raise OtaError("no previous APK; persistent owner signing key required before bootstrap")
 old=json.loads(a.previous_feed.read_text()); d=verify(d,old,a.aapt,a.apksigner,APPROVED_RELEASE_SIGNER_SHA256)
 d.update(source=a.source); a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(d,indent=2)+"\n"); print(json.dumps(d,sort_keys=True))
if __name__=="__main__":
 try: main()
 except (OtaError,OSError,json.JSONDecodeError) as e: print("OTA publication refused: "+str(e),file=sys.stderr); raise SystemExit(1)
