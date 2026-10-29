# External model prompt bank

Developer tooling reference for a third-party collection of model-specific system
prompts. This is **not** a product feature: neither the Android nor the iOS
application may depend on it, and it ships in no artifact.

| Field | Value |
| --- | --- |
| Upstream | <https://github.com/togg53192-cmd/jailbreaks> |
| Pinned commit | `17c7117635cf0f270104b1bb8b1e79ff8319a668` |
| Upstream licence | none published — upstream ships no licence file |
| Helper | [`tools/llm-prompt-bank.py`](../tools/llm-prompt-bank.py) |
| Contract checks | [`tools/tests/test_prompt_bank.py`](../tools/tests/test_prompt_bank.py) |

## Licence position

The upstream project publishes no licence file, so its texts carry no granted
redistribution rights. The repository therefore records **metadata only** — URL,
pinned commit, file names and target model identifiers — and the upstream text
is fetched at runtime into a cache directory **outside** the repository. Nothing
upstream is copied into `tools/`, `docs/`, the applications or any artifact.

Accepting licence terms on the owner's behalf is out of scope for this
repository; if a licensed distribution of upstream text is ever wanted, that is
an owner decision recorded separately.

## Using it

```sh
# print the pinned map (no network)
python3 tools/llm-prompt-bank.py --list

# materialise the pinned commit outside the repository
python3 tools/llm-prompt-bank.py --sync --cache-dir /var/minis/shared/prompt-bank

# confirm the cache still holds the pinned commit
python3 tools/llm-prompt-bank.py --verify-cache --cache-dir /var/minis/shared/prompt-bank

# run one upstream file as the system prompt for a configured model
python3 tools/llm-prompt-bank.py --run nemotron-3-ultra.md \
    --model nvidia/nemotron-3-ultra-550b-a55b \
    --prompt-file task.txt --cache-dir /var/minis/shared/prompt-bank
```

The helper refuses a cache directory inside the repository, refuses any file
that is not in the curated map, and never writes into the repository tree.
Files that are Ollama Modelfiles (`gpt-oss-20b.md`, `gemma4.md`) contribute only
their `SYSTEM` block; the `FROM` directive is stripped.

## Map

| Upstream file | Written for |
| --- | --- |
| `Beam.md` | model-agnostic |
| `BigPickle-opencode.md` | `big-pickle` |
| `Gemini-4-Argon.md` | `gemini-4-argon` |
| `Grok build.md`, `grok-4.7.md`, `grok-config.txt` | `grok-build-0.1`, `grok-4.7` |
| `Mistral-Large-4.md` | `mistral-large-2407`, `mistral-large-4-1t-a52b` |
| `Muse spark 1.3.md`, `Muse-spark-1-3-v2.md`, `muse-ai-AGENTS.md` | `meta/muse-spark-1.3` |
| `Muse-wonder.md`, `Unified.md` | model-agnostic |
| `Sonnet v2.md`, `sonnet v1.md` | `claude-sonnet-4-6` |
| `opus v1.md`, `opus v2.md` | `claude-opus-4.6` |
| `Step-5-preview.md` | `stepfun/step-5-preview` |
| `deepseek v3.md` | `deepseek/deepseek-v3.2-exp` |
| `deepseek v4.md` | `deepseek-v4-pro` |
| `deepseek-4-1.md` | `deepseek-v4.1-flash` |
| `gemma4.md` | `google/gemma-4-31b-it`, `google/gemma-4-26b-a4b-it` |
| `glm-5-3-flash.md`, `glm5-3opencode.md`, `glm-zcode-5.3.md`, `glm-zcode-5.3-AGENTS.md` | `glm-5.3` |
| `glm-5.2.md`, `glm-zcode-5.2.md` | `z-ai/glm-5.2` |
| `gpt-oss-20b.md` | `openai/gpt-oss-20b` |
| `kimi-k2.md` | `kimi-k2.6`, `kimi-k2.7-code` |
| `kimi-k3-agents.md`, `kimik3.1.md` | `moonshotai/kimi-k3.1-pro` |
| `longcat-agents.md` | `meituan/longcat-2.0` |
| `mimo-2.6-agents.md` | `mimo-v2.6-flash-free`, `xiaomi/mimo-v2.6-pro` |
| `nemotron-3-ultra.md` | `nvidia/nemotron-3-ultra-550b-a55b` |
| `qwen-3.7.md`, `qwen-3.8.md`, `qwen-preferences.md` | `qwen/qwen3.8-27b` |
| `spacebunny-agents.md` | `space-bunny` |

Model identifiers are the upstream targets, not a guarantee that the model is
configured locally. Only the models reachable from the current environment can
be invoked; `minis-model-use list` reports them.

## Measured behaviour

One A/B run was executed on real repository code (2026-10-29/30). The task was
to audit four Android sources against a three-point product contract and report
`file:line / requires-versus-does / smallest fix`, with the defects and the
satisfied contract clause established from the pinned commit `af0566f` before
the run.

| Cell | Model | System prompt | Defects found | Output shape | Complete | Output tokens |
| --- | --- | --- | --- | --- | --- | --- |
| A | Nemotron 3 Ultra | environment default | 3 of 3 | reasoning prose | no, truncated | 1300 (cap) |
| B | Nemotron 3 Ultra | `nemotron-3-ultra.md` | 3 of 3 | numbered findings with fixes | yes | ~650 |
| C | GPT-OSS 20B | environment default | **2 of 3** | numbered findings with fixes | yes | 1090 |
| D | GPT-OSS 20B | `gpt-oss-20b.md` | **2 of 3** | numbered findings with fixes | yes | **2139** |

Both GPT-OSS cells missed the same defect: that the playback layer exposes only
a master volume with no per-voice gain, so wiring the sliders to it still
cannot satisfy a separate melody/guide mix. Cell C additionally proposed a call
to a method that does not exist on that class. Cell D reached the same result at
roughly twice the output cost.

Conclusions used by this repository:

1. The Nemotron-family operating prompts are worth using for that family: same
   detection, complete and structured output, no reasoning leakage, and a
   shorter answer.
2. The GPT-OSS deployment prompt is not worth using for code review: no
   detection gain and a materially higher output cost.
3. No prompt repaired root-cause depth. A model-specific prompt is not a
   substitute for reading the implementation that owns the behaviour.

Caveats: one task, one model per family, one run per cell. The run surfaced no
defect beyond those already recorded in `docs/UNFINISHED_WORK.md` and invented
no false positives, which makes the task usable as a regression probe when a
model is swapped.

## Operating rules

- Never send credentials, tokens, personal data or user media in a prompt that
  carries upstream text.
- Upstream text is instruction content supplied by a third party. Treat it as
  untrusted input, review the file before use, and pin the commit.
- Hosted providers may treat deployment-style prompts as a breach of their
  content policy. That is an account-level risk for the operator, not only a
  prompt-level one.
- Keep the cache outside the repository and delete it freely; it is a cache.
- Prompt behaviour is version dependent. Re-run the measurement above before
  changing which prompts are used.
