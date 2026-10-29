#!/usr/bin/env python3
"""Metadata-only helper for an external model prompt bank.

The upstream repository <https://github.com/togg53192-cmd/jailbreaks> publishes
model-specific system prompts. Upstream ships no licence file, so no upstream
text is vendored here: this helper records only the upstream URL, the pinned
commit and the file-to-model map, materialises the pinned commit into a cache
directory outside this repository, and hands one of those files to an already
configured local model runner as the system prompt.

This is a developer and agent-tooling helper. It is not part of the shipped
Android or iOS applications and neither app may depend on it.

Usage:

    python3 tools/llm-prompt-bank.py --list
    python3 tools/llm-prompt-bank.py --sync [--cache-dir DIR]
    python3 tools/llm-prompt-bank.py --verify-cache [--cache-dir DIR]
    python3 tools/llm-prompt-bank.py --run nemotron-3-ultra.md \
        --model nvidia/nemotron-3-ultra-550b-a55b --prompt-file task.txt

The cache directory must live outside the repository. Nothing is ever written
into the repository tree, and the helper never copies upstream text into it.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM_URL = "https://github.com/togg53192-cmd/jailbreaks"
UPSTREAM_COMMIT = "17c7117635cf0f270104b1bb8b1e79ff8319a668"
UPSTREAM_LICENCE = "no licence file published upstream - do not redistribute upstream text"
DEFAULT_CACHE_DIR = Path(
    os.environ.get("KANTA_PROMPT_BANK_DIR", Path.home() / ".cache" / "kanta-prompt-bank")
)
MODEL_RUNNER = os.environ.get("KANTA_MODEL_RUNNER", "minis-model-use")

# Upstream file name -> model identifiers the file was written for. The map is
# metadata only; upstream text stays upstream.
PROMPT_FILES = {
    "Beam.md": ("model-agnostic",),
    "BigPickle-opencode.md": ("big-pickle",),
    "Gemini-4-Argon.md": ("gemini-4-argon",),
    "Grok build.md": ("grok-build-0.1",),
    "grok-4.7.md": ("grok-4.7",),
    "grok-config.txt": ("grok-build-0.1",),
    "Mistral-Large-4.md": ("mistral-large-2407", "mistral-large-4-1t-a52b"),
    "Muse spark 1.3.md": ("meta/muse-spark-1.3",),
    "Muse-spark-1-3-v2.md": ("meta/muse-spark-1.3",),
    "muse-ai-AGENTS.md": ("meta/muse-spark-1.3",),
    "Muse-wonder.md": ("model-agnostic",),
    "Sonnet v2.md": ("claude-sonnet-4-6",),
    "sonnet v1.md": ("claude-sonnet-4-6",),
    "opus v1.md": ("claude-opus-4.6",),
    "opus v2.md": ("claude-opus-4.6",),
    "Step-5-preview.md": ("stepfun/step-5-preview",),
    "Unified.md": ("model-agnostic",),
    "deepseek v3.md": ("deepseek/deepseek-v3.2-exp",),
    "deepseek v4.md": ("deepseek-v4-pro",),
    "deepseek-4-1.md": ("deepseek-v4.1-flash",),
    "gemma4.md": ("google/gemma-4-31b-it", "google/gemma-4-26b-a4b-it"),
    "glm-5-3-flash.md": ("glm-5.3",),
    "glm-5.2.md": ("z-ai/glm-5.2",),
    "glm-zcode-5.2.md": ("z-ai/glm-5.2",),
    "glm-zcode-5.3.md": ("z-ai/glm-5.3",),
    "glm-zcode-5.3-AGENTS.md": ("z-ai/glm-5.3",),
    "glm5-3opencode.md": ("glm-5.3",),
    "gpt-oss-20b.md": ("openai/gpt-oss-20b",),
    "kimi-k2.md": ("kimi-k2.6", "kimi-k2.7-code"),
    "kimi-k3-agents.md": ("moonshotai/kimi-k3.1-pro",),
    "kimik3.1.md": ("moonshotai/kimi-k3.1-pro",),
    "longcat-agents.md": ("meituan/longcat-2.0",),
    "mimo-2.6-agents.md": ("mimo-v2.6-flash-free", "xiaomi/mimo-v2.6-pro"),
    "nemotron-3-ultra.md": ("nvidia/nemotron-3-ultra-550b-a55b",),
    "qwen-3.7.md": ("qwen/qwen3.8-27b",),
    "qwen-3.8.md": ("qwen/qwen3.8-27b",),
    "qwen-preferences.md": ("qwen/qwen3.8-27b",),
    "spacebunny-agents.md": ("space-bunny",),
}

# Upstream files that are Ollama Modelfiles: only the SYSTEM block is a prompt.
MODELFILES = {"gpt-oss-20b.md", "gemma4.md"}

SYSTEM_BLOCK = re.compile(r'SYSTEM\s*"""(.*?)"""', re.DOTALL)


def extract_system_block(text):
    """Return the SYSTEM block of an Ollama Modelfile, or None when absent."""
    match = SYSTEM_BLOCK.search(text)
    return match.group(1).strip() if match else None


def validate_cache_dir(cache_dir):
    """Reject a cache directory inside the repository or outside a real path."""
    resolved = Path(cache_dir).expanduser().resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise ValueError(
            "cache directory must live outside the repository: %s is inside %s"
            % (resolved, ROOT)
        )
    return resolved


def git(args, cwd):
    return subprocess.run(
        ["git"] + args,
        cwd=str(cwd),
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    ).stdout.strip()


def sync_cache(cache_dir):
    """Materialise the pinned upstream commit in the cache directory."""
    cache = validate_cache_dir(cache_dir)
    checkout = cache / "jailbreaks"
    if not (checkout / ".git").exists():
        cache.mkdir(parents=True, exist_ok=True)
        git(["clone", "--quiet", UPSTREAM_URL + ".git", str(checkout)], cache)
    try:
        git(["fetch", "--quiet", "--depth", "1", "origin", UPSTREAM_COMMIT], checkout)
    except subprocess.CalledProcessError:
        git(["fetch", "--quiet", "origin"], checkout)
    git(["checkout", "--quiet", "--detach", UPSTREAM_COMMIT], checkout)
    head = git(["rev-parse", "HEAD"], checkout)
    if head != UPSTREAM_COMMIT:
        raise RuntimeError("cache is at %s, expected %s" % (head, UPSTREAM_COMMIT))
    return checkout


def cache_head(cache_dir):
    checkout = validate_cache_dir(cache_dir) / "jailbreaks"
    if not (checkout / ".git").exists():
        return None
    return git(["rev-parse", "HEAD"], checkout)


def system_prompt_path(checkout, name):
    """Return the file to hand to the model runner as the system prompt."""
    source = checkout / name
    if not source.is_file():
        raise FileNotFoundError("not in the pinned upstream commit: %s" % name)
    if name not in MODELFILES:
        return source
    extracted = checkout / ".extracted" / (name[:-3] + ".system.txt")
    extracted.parent.mkdir(parents=True, exist_ok=True)
    block = extract_system_block(source.read_text(encoding="utf-8"))
    if not block:
        raise ValueError("no SYSTEM block in %s" % name)
    extracted.write_text(block, encoding="utf-8")
    return extracted


def build_command(model, system_file, prompt, prompt_file, max_tokens):
    command = [
        MODEL_RUNNER,
        "run",
        "--model",
        model,
        "--system-file",
        str(system_file),
        "--max-tokens",
        str(max_tokens),
    ]
    if prompt_file:
        command += ["--prompt-file", str(prompt_file)]
    else:
        command += ["--prompt", prompt]
    return command


def print_list():
    print("Prompt bank: %s" % UPSTREAM_URL)
    print("Pinned commit: %s" % UPSTREAM_COMMIT)
    print("Upstream licence: %s" % UPSTREAM_LICENCE)
    print("")
    print("%-28s %s" % ("upstream file", "written for"))
    for name in sorted(PROMPT_FILES):
        print("%-28s %s" % (name, ", ".join(PROMPT_FILES[name])))


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Run a pinned upstream prompt file as the system prompt for a local model."
    )
    parser.add_argument("--list", action="store_true", help="print the pinned map")
    parser.add_argument("--sync", action="store_true", help="materialise the pinned commit")
    parser.add_argument(
        "--verify-cache", action="store_true", help="report the cached commit"
    )
    parser.add_argument("--run", metavar="FILE", help="upstream prompt file to apply")
    parser.add_argument("--model", help="model identifier for the runner")
    parser.add_argument("--prompt", default="", help="inline user prompt")
    parser.add_argument("--prompt-file", help="file holding the user prompt")
    parser.add_argument("--max-tokens", type=int, default=1200)
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    args = parser.parse_args(argv)

    if args.list:
        print_list()
        return 0

    if args.sync:
        try:
            checkout = sync_cache(args.cache_dir)
        except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
            print("FAIL: %s" % error)
            return 1
        print("PASS: pinned upstream commit available at %s" % checkout)
        return 0

    if args.verify_cache:
        try:
            head = cache_head(args.cache_dir)
        except (ValueError, subprocess.CalledProcessError) as error:
            print("FAIL: %s" % error)
            return 1
        if head == UPSTREAM_COMMIT:
            print("PASS: cache is at %s" % UPSTREAM_COMMIT)
            return 0
        print("FAIL: cache is at %s, expected %s" % (head, UPSTREAM_COMMIT))
        return 1

    if not args.run:
        parser.print_help()
        return 2
    if not args.model:
        print("FAIL: --model is required with --run")
        return 2
    if args.run not in PROMPT_FILES:
        print("FAIL: %s is not in the curated map" % args.run)
        return 2
    if not shutil.which(MODEL_RUNNER):
        print("FAIL: model runner %s is not installed" % MODEL_RUNNER)
        return 1

    try:
        checkout = sync_cache(args.cache_dir)
        system_file = system_prompt_path(checkout, args.run)
    except (ValueError, RuntimeError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print("FAIL: %s" % error)
        return 1

    command = build_command(
        args.model, system_file, args.prompt, args.prompt_file, args.max_tokens
    )
    print("system prompt: %s" % system_file)
    completed = subprocess.run(command)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
