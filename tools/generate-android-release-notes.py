#!/usr/bin/env python3
"""Create detailed, evidence-backed notes for a verified Android preview."""
from __future__ import annotations
import argparse, hashlib, json, os, re, sys, urllib.error, urllib.parse, urllib.request
from pathlib import Path
TAG_RE = re.compile(r"^v[0-9]+(?:\.[0-9]+){1,3}-preview-([0-9a-f]{7,40})$")
MAX_BODY, MAX_NOTES = 2400, 30000
class NotesError(ValueError): pass

def api_json(repo, path, token):
    req=urllib.request.Request(f"https://api.github.com/repos/{repo}/{path.lstrip('/')}",headers={"Authorization":"Bearer "+token,"Accept":"application/vnd.github+json","User-Agent":"KANTA-release-notes","X-GitHub-Api-Version":"2022-11-28"})
    try:
        with urllib.request.urlopen(req,timeout=30) as response: data=response.read(5*1024*1024+1)
        if len(data)>5*1024*1024: raise NotesError("GitHub release-note response exceeded bound")
        return json.loads(data)
    except (OSError,urllib.error.URLError,json.JSONDecodeError) as exc: raise NotesError("GitHub release-note evidence request failed") from exc

def previous_android_release(releases,current_sha):
    valid=[]
    for rel in releases:
        tag=rel.get("tag_name",""); match=TAG_RE.fullmatch(tag) if isinstance(tag,str) else None
        if not match or current_sha.startswith(match.group(1)) or rel.get("draft"): continue
        if any(isinstance(a.get("name"),str) and a["name"].startswith("KANTA-Android-preview-") and a["name"].endswith(".apk") for a in rel.get("assets",[])): valid.append(rel)
    return max(valid,key=lambda r:r.get("created_at",""),default=None)

def resolve_release_commit(repo, tag, token):
    """Resolve the actual Git tag object; do not trust a release name alone."""
    match=TAG_RE.fullmatch(tag)
    if not match: raise NotesError("previous Android release tag has malformed source suffix")
    ref=api_json(repo,"git/ref/tags/"+urllib.parse.quote(tag,safe=""),token)
    obj=ref.get("object",{})
    for _ in range(5):
        kind,sha=obj.get("type"),obj.get("sha")
        if not isinstance(sha,str) or not re.fullmatch(r"[0-9a-f]{40}",sha):
            raise NotesError("previous Android release tag object is malformed")
        if kind=="commit":
            if not sha.startswith(match.group(1)):
                raise NotesError("previous release tag source suffix does not match its commit")
            return sha
        if kind!="tag": raise NotesError("previous release tag does not resolve to a commit")
        obj=api_json(repo,"git/tags/"+sha,token).get("object",{})
    raise NotesError("previous Android release tag is nested too deeply")

def summarize_pr_body(body,limit=MAX_BODY):
    if not isinstance(body,str): return ""
    text=re.sub(r"<!--.*?-->","",body,flags=re.S)
    # Current exact-source validation is recorded below; omit stale PR-branch claims.
    kept,skip=[],False
    paragraph_skip=False
    for line in text.splitlines():
        if not line.strip(): paragraph_skip=False
        if line.startswith("#"):
            heading=line.lstrip("# ").lower()
            skip=bool(re.search(r"\b(validation|checks?|evidence|ci results?)\b",heading))
        if re.match(r"(?i)^\s*(?:\*\*)?(validation|checks?|ci results?|evidence)\s*:",line):
            paragraph_skip=True
        if not skip and not paragraph_skip:kept.append(line)
    text="\n".join(kept)
    text=re.sub(r"!\[([^\]]*)\]\([^)]*\)",r"\1",text)
    text=re.sub(r"<[^>]*>","",text)
    secret=re.compile(r"(?i)(api[_ -]?key|access[_ -]?token|token|password|secret|private key|BEGIN [A-Z ]*PRIVATE KEY)\s*[:=]|\bgh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}")
    text="\n".join("[credential-bearing line omitted]" if secret.search(line) else line.rstrip() for line in text.splitlines()).strip()
    if len(text)<=limit:return text
    cut=text.rfind("\n",0,limit)
    return text[:cut if cut>=limit//2 else limit].rstrip()+"\n\n_(Description shortened; see linked PR for full detail.)_"

def safe_inline(value):
    text=summarize_pr_body(value, 300)
    return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]").replace("|", "\\|")

def change_summary(repo,head,previous,token):
    if previous is None:return None,None,[],[]
    prior=resolve_release_commit(repo,previous["tag_name"],token)
    comp=api_json(repo,f"compare/{prior}...{head}?per_page=100",token)
    if comp.get("status") not in ("ahead","identical") or not isinstance(comp.get("commits"),list):raise NotesError("source is not a valid forward comparison from previous preview")
    if int(comp.get("total_commits",len(comp["commits"])))>len(comp["commits"]):raise NotesError("source range has more commits than the bounded release-note query returned")
    prs,direct={},[]
    for c in comp["commits"]:
        sha=c.get("sha")
        if not isinstance(sha,str):continue
        linked=api_json(repo,f"commits/{sha}/pulls?per_page=100",token)
        if not isinstance(linked,list):raise NotesError("commit-to-pull-request response is malformed")
        merged=[pr for pr in linked if isinstance(pr.get("number"),int) and pr.get("merged_at")]
        for pr in merged:prs[pr["number"]]=pr
        if not merged:
            msg=c.get("commit",{}).get("message","").splitlines()
            subject=msg[0].strip() if msg else sha[:8]
            if not re.match(r"(?i)^chore\(downloads\): publish verified ",subject):
                direct.append(safe_inline(subject) or "[commit subject omitted]")
    return prior,previous,sorted(prs.values(),key=lambda p:(p.get("merged_at",""),p["number"])),direct

def render_notes(repo,source,source_run,publisher_run,meta,apk_name,previous,previous_sha,prs,commits):
    version,code,min_sdk=meta.get("versionName"),meta.get("versionCode"),meta.get("minSdk")
    digest,size,signer=meta.get("apkSha256"),meta.get("apkBytes"),meta.get("signerSha256")
    if (not isinstance(version,str) or not isinstance(code,int) or isinstance(code,bool) or not isinstance(min_sdk,int) or not isinstance(size,int) or size<1 or not isinstance(digest,str) or not re.fullmatch(r"[0-9a-f]{64}",digest) or not isinstance(signer,str) or not re.fullmatch(r"[0-9a-f]{64}",signer)):
        raise NotesError("verified APK metadata is incomplete")
    tag="v"+version.split("-")[0]+"-preview-"+source[:8]
    prev=(f"`{previous['tag_name']}` (`{previous_sha[:8]}`)" if previous and previous_sha else "No earlier Android preview release with a valid source tag was found.")
    out=[f"# KANTA Android preview {version}","","Verified owner-signed Android preview from the exact merged `main` source. This is not a Google Play release.","","## Build details","","| Field | Value |","| --- | --- |",f"| Release tag | `{tag}` |",f"| Source | [`{source}`](https://github.com/{repo}/commit/{source}) |",f"| Previous Android preview | {prev} |",f"| Android validation | [Run #{source_run['run_number']}](https://github.com/{repo}/actions/runs/{source_run['id']}) — success |",f"| Publisher | [Run #{publisher_run}](https://github.com/{repo}/actions/runs/{publisher_run}) |",f"| Version code / min SDK | `{code}` / `{min_sdk}` |",f"| APK | `{apk_name}` — {size:,} bytes |",f"| APK SHA-256 | `{digest}` |",f"| Signing certificate SHA-256 | `{signer}` |","","## What changed since the previous Android preview",""]
    if prs:
        for pr in prs:
            n=pr["number"]; title=safe_inline(pr.get("title") or f"Pull request #{n}") or "[title omitted]"
            url=f"https://github.com/{repo}/pull/{n}"
            out.extend([f"### [#{n} — {title}]({url})","",summarize_pr_body(pr.get("body")) or "Merged into this exact source range.",""])
    if commits:
        out.extend(["### Additional direct commits","",*["- "+x for x in commits[:30]]])
        if len(commits)>30:out.append(f"- …and {len(commits)-30} more commits in this source range.")
        out.append("")
    if not prs and not commits:out.extend(["No source changes beyond the previous preview were found in the exact comparison range.",""])
    out.extend(["## Installation and compatibility",""])
    if meta.get("signerTransition") is True:out.append("This build starts a **new signing identity**. Earlier APKs cannot update in place; uninstall the old preview and install this build. Uninstalling may erase app-private data, so back up or export anything important first.")
    else:out.append("This build uses the same signing identity as the preceding preview; Android can accept it as an in-place update when package/version rules permit.")
    out.extend(["","## Validation and limits","","- The exact-source Android validation workflow completed successfully; hosted emulator checks are not physical-device acceptance.","- Real-device installation, audio quality and upgrade behavior remain separate acceptance checks.","- This Android-only preview does not change or republish iOS.","","## Download","",f"[Download the Android APK](https://github.com/{repo}/releases/download/{tag}/{apk_name})",""])
    notes="\n".join(out).strip()+"\n"
    if len(notes)>MAX_NOTES:raise NotesError("generated notes exceed maximum body length")
    return notes

def main():
    p=argparse.ArgumentParser();p.add_argument("--metadata",type=Path,required=True);p.add_argument("--apk",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args()
    repo=os.environ.get("GITHUB_REPOSITORY","");token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN");source=os.environ.get("SOURCE_SHA","")
    source_run_id=os.environ.get("SOURCE_RUN_ID","");source_run_number=os.environ.get("SOURCE_RUN_NUMBER","");pub=os.environ.get("GITHUB_RUN_ID","")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+",repo):raise NotesError("GITHUB_REPOSITORY is missing or malformed")
    if not token or not re.fullmatch(r"[0-9a-f]{40}",source) or not source_run_id.isdigit() or not source_run_number.isdigit() or not pub.isdigit():raise NotesError("release-note API credentials or exact source workflow-run identity are unavailable")
    meta=json.loads(a.metadata.read_text(encoding="utf-8"))
    if meta.get("source")!=source:raise NotesError("APK metadata source does not match the exact release source")
    meta["apkBytes"]=a.apk.stat().st_size;meta["apkSha256"]=meta.get("apkSha256") or hashlib.sha256(a.apk.read_bytes()).hexdigest()
    runs=api_json(repo,"actions/runs?head_sha="+source+"&per_page=100",token).get("workflow_runs",[])
    sr=next((r for r in runs if str(r.get("id"))==source_run_id),None)
    if (not sr or sr.get("name")!="KANTA Android validation" or sr.get("head_sha")!=source
        or sr.get("head_branch")!="main" or sr.get("conclusion")!="success"
        or str(sr.get("run_number"))!=source_run_number):raise NotesError("source workflow run is not the successful exact-main-SHA Android validation")
    releases=api_json(repo,"releases?per_page=100",token)
    if not isinstance(releases,list):raise NotesError("GitHub release list response is malformed")
    prior=previous_android_release(releases,source); prior_sha,prior,prs,commits=change_summary(repo,source,prior,token)
    notes=render_notes(repo,source,sr,pub,meta,a.apk.name,prior,prior_sha,prs,commits)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(notes,encoding="utf-8")
    print("Generated detailed release notes for "+source[:8])

if __name__=="__main__":
    try:main()
    except (NotesError,OSError,json.JSONDecodeError) as exc:
        print("release notes refused: "+str(exc),file=sys.stderr);raise SystemExit(1)
