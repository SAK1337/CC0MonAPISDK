# Questions & Recommendations

This document lists open questions deferred from `spec.md`. Each has a **stated default** that the v0.1 implementation uses unless the question is answered otherwise. Override any default by editing this file and re-running the affected components — the defaults are conservative and easy to change.

**How to use this doc:**

1. Read each question.
2. If the default works for you, do nothing.
3. If you want the alternative, leave a comment under the question and ping the SDK maintainer.

---

## Section A — Logging & filesystem

### Q1. Concurrent log writes — what happens if two scripts run the same action in parallel?

- **Default:** append to a shared file; on Windows, occasional `EACCES` retries; lines may interleave but never split mid-line on POSIX.
- **Why default:** simplest model; matches user-facing log file naming requirement (`cc0mon-api-<action>.log`).
- **Alternative:** suffix the file name with PID (`cc0mon-api-<action>.<pid>.log`). Avoids contention, but means you have N files to grep instead of one.
- **Recommendation:** keep default unless ops automation routinely runs the same action in parallel. Revisit if you see file-lock errors in the wild.

### Q2. Log rotation — how do we keep the log file from growing forever?

- **Default:** no rotation. The file grows.
- **Why default:** keeps the implementation tiny. Each line is ~150–200 bytes; even a million calls is ~200 MB.
- **Alternative:** roll at 10 MB with up to 5 historic files (`*.log.1`, `*.log.2`, …). Python: `RotatingFileHandler`; Java: `java.util.logging.FileHandler` with limit + count; PowerShell: manual size check.
- **Recommendation:** keep default for v0.1. Add rotation when first user complains about disk usage.

### Q3. Append vs truncate per run.

- **Default:** append.
- **Why default:** preserves the cross-run audit trail ops users expect.
- **Alternative:** truncate at start of each run for a clean per-run log.
- **Recommendation:** keep default. If you want per-run logs, use a wrapper that renames the file before each run.

---

## Section B — HTTP behavior

### Q4. Retry on 5xx, not just 429.

- **Default:** retry on 429 **and** 5xx (500/502/503/504), 3 attempts, exponential backoff with full jitter, max 30s, honoring `Retry-After`.
- **Why default:** the public API has no SLA; transient 5xx during deploys are likely. Cheap to be tolerant.
- **Alternative:** retry only on 429. Treat 5xx as a hard failure.
- **Recommendation:** keep default. If you need bisect-friendly failures during dev, pass `retries=0`.

### Q5. Honoring `Retry-After`.

- **Default:** if `Retry-After` is present (in seconds or HTTP-date form), use that value for the next sleep, ignoring the computed backoff.
- **Why default:** RFC 7231 expects clients to honor it; the cc0mon docs imply per-IP rate limiting that may emit this header.
- **Alternative:** ignore it (use only exponential backoff).
- **Recommendation:** keep default.

### Q6. Connection / read timeouts.

- **Default:** 30 seconds total per request (connect + read combined).
- **Why default:** generous for a global API; not so high that hangs lock up scripts indefinitely.
- **Alternative:** split (connect=5s, read=25s).
- **Recommendation:** keep default. Override per-client via constructor.

---

## Section C — Image handling

### Q7. Image overwrite policy.

- **Default:** silently overwrite the output file if it exists.
- **Why default:** matches Unix `wget`/`curl -o` behavior; one-flag scripts shouldn't need user prompts.
- **Alternative A:** refuse to overwrite (exit code 5) unless `--force` is passed.
- **Alternative B:** auto-suffix (`cc0mon-1 (2).png`).
- **Recommendation:** keep default. Add `--force` semantics only if a user reports data loss.

### Q8. Default output filename.

- **Default:** `./cc0mon-<id>.svg` or `./cc0mon-<id>.png` in CWD.
- **Why default:** matches the prefix family already used by log files; predictable.
- **Alternative:** name from a path the API may include in the response.
- **Recommendation:** keep default. Use `--out` to redirect.

---

## Section D — Address & input handling

### Q9. Collector address — ENS names?

- **Default:** accept only 0x-prefixed addresses (42 chars, hex).
- **Why default:** the API docs only document hex addresses; ENS resolution would add an Ethereum-RPC dependency.
- **Alternative:** detect a `.eth` suffix and resolve client-side via public RPC.
- **Recommendation:** keep default. Document this in `python/README.md`. Open a follow-up issue if a user requests ENS.

### Q10. Address checksum vs lowercase.

- **Default:** SDK lowercases the address before sending.
- **Why default:** the API appears case-insensitive; lowercase is canonical for hashing.
- **Alternative:** preserve the user's case; validate against EIP-55 checksum.
- **Recommendation:** keep default. Validation only checks length, prefix, and hex.

---

## Section E — Output & exit codes

### Q11. JSON output formatting on stdout.

- **Default:** pretty-printed JSON (2-space indent) to stdout for JSON endpoints.
- **Why default:** easy to read interactively.
- **Alternative:** raw compact JSON.
- **Recommendation:** keep default. Add `--raw` flag in v0.2 if pipe-to-jq users complain.

### Q12. Exit code table (from spec §8.2).

- **Default:** 0 ok / 2 network / 3 4xx / 4 5xx / 5 validation / 1 unexpected.
- **Why default:** distinguishes failure categories without overloading `1`.
- **Alternative:** collapse to 0/1 only.
- **Recommendation:** keep default. CI consumers will thank you.

---

## Section F — Compatibility

### Q13. PowerShell 5.1 (Windows PowerShell).

- **Default:** PowerShell 7.x only. Scripts use PS 7 syntax (`??`, `??=`, `&&`, parallel `ForEach-Object`).
- **Why default:** PS 5.1 lacks several modern syntax features and HTTPS defaults to TLS 1.0 unless overridden; PS 7 is cross-platform and current.
- **Alternative:** rewrite scripts to a PS 5.1 / PS 7 common subset and add a `[Net.ServicePointManager]::SecurityProtocol = "Tls12"` shim.
- **Recommendation:** keep default. If a Windows admin must use 5.1, the rewrite cost is low (~30 lines).

### Q14. Python 3.9 vs 3.10+.

- **Default:** 3.10+ (`X | Y` union types, `match` statements).
- **Why default:** 3.9 reached EOL Oct 2025; modern type hints are clearer.
- **Alternative:** 3.9 with `Optional`/`Union` everywhere.
- **Recommendation:** keep default.

### Q15. JDK baseline.

- **Default:** JDK 17 LTS.
- **Why default:** records, sealed types, text blocks all available; current LTS.
- **Alternative:** JDK 11 (no records, no text blocks).
- **Recommendation:** keep default.

---

## Section G — Distribution

### Q16. Publish to PyPI / Maven Central?

- **Default:** **no.** Reference repo only; install via `pip install -e ./python` and `mvn install`.
- **Why default:** keeps maintenance to "merge a PR" rather than "cut a release, sign artifacts, push to a registry."
- **Alternative:** publish under `cc0mon-sdk` (PyPI) and `com.cc0mon:sdk` (Maven Central).
- **Recommendation:** revisit after the SDK stabilizes and has external users.

### Q17. Versioning scheme.

- **Default:** semver starting at `0.1.0`.
- **Why default:** API is stable but the SDK shape may change.
- **Alternative:** pin to `1.0.0` and use the patch field aggressively.
- **Recommendation:** keep default.

### Q18. License headers in every source file.

- **Default:** no per-file headers; the repo-root `LICENSE` (MIT) covers everything.
- **Why default:** less boilerplate.
- **Alternative:** SPDX `// SPDX-License-Identifier: MIT` at the top of every file.
- **Recommendation:** keep default unless your distribution policy requires SPDX.

---

## Section H — Build & CI

### Q19. CI matrix.

- **Default:** no CI in v0.1. Manual smoke tests via the verification block in `README.md`.
- **Why default:** the integration tests hit a live API and would consume rate budget on every PR.
- **Alternative:** GitHub Actions running unit/syntax checks (linters, `mvn compile`, `pytest --collect-only`) without hitting the API; integration tests only on `main`.
- **Recommendation:** add the "syntax-only" CI as soon as there are >2 contributors.

### Q20. Pre-commit hooks.

- **Default:** none.
- **Alternative:** `black` / `ruff` for Python; `google-java-format` for Java; `PSScriptAnalyzer` for PowerShell.
- **Recommendation:** add when contributor count >1.

---

## Section I — README scope

### Q21. README organization.

- **Default:** root `README.md` with quick-start per language, plus one `README.md` per language subdirectory with detail.
- **Why default:** new visitors get oriented quickly; deep readers find their language's full guide.
- **Alternative:** single mega-README at root.
- **Recommendation:** keep default.

---

## Section I-bis — Search & filtering (v0.2 update)

### Q22. Filter values — fixed enum or open string?

- **Decided in v0.2:** **Fixed enum**. The 16 energies and 4 rarities are hard-coded in each language; unknown values raise `ValidationError`/`ValidationException` with a friendly list. Match is case-insensitive; storage is canonical case.
- **Future trigger:** if cc0mon adds a 17th energy, the SDK must add it to the constant set in a patch release. The `raw` field still carries any new attribute even before the SDK is updated.

### Q23. Token-level search across all 9,999 tokens?

- **Out of scope in v0.2.** Iterating `/cc0mon/{id}/traits` for 10,000 ids at 60 req/min would take ~167 minutes. Document as a recipe if a user requests.

### Q24. Multi-value OR filters (e.g. `--energy Fire,Water`)?

- **Deferred.** Single-value AND-only is enough for the demonstrated use cases. Will revisit if a user reports a real need.

### Q25. Sorting filter output?

- **Out of scope.** Shell tools (`jq`, `Sort-Object`) handle this cleanly; baking it into the SDK would add CLI surface area for little gain.

---

## Section J — Future work flags

The following items are explicitly **not implemented in v0.1** but flagged for v0.2+:

- **Async clients** — Python `httpx.AsyncClient`; Java `HttpClient.sendAsync` returning `CompletableFuture`.
- **ETag / `If-None-Match` caching** — would cut bandwidth for the static `/registry` endpoint.
- **CLI tool** — one binary (`cc0mon-cli get-token --id 1`) replacing the 10 per-action scripts.
- **Streaming `/registry`** — currently returns 260 items in one shot; a paginated variant would let huge collections stream.
- **ENS resolution** for the collector endpoint (see Q9).
- **WebSocket** support — none exists in the API today.
- **Type generation** if cc0mon publishes an OpenAPI spec.

---

## Sign-off

Default policy: **all defaults stand for v0.1 unless explicitly overridden by edits to this file or follow-up PRs.**

If you need a change, edit the corresponding Q section and add a `**Override:** …` line below the default — the implementation will pick it up on the next revision.
