# Jev MCP payload limits: findings

**Date:** 2026-10-02. Measured on the deployment this project runs
(`@jkudish/jev-mcp` 0.13.0 via `~/.local/bin/jev-mcp-commandcode`: provider
`compatible`, `JEV_API_BASE_URL=https://api.commandcode.ai/provider/v1/systemone`,
model `typesafe/jev`). The numbers are properties of **that gateway**, not of
Jev or of the MCP server: the server's own caps are looser, so a batch it
accepts can still be rejected upstream.

## Why this needed measuring

The MCP server deliberately discards the provider's error body — provider.js
throws the fixed string `` `Jev-compatible endpoint ${response.status}` `` so a
reflecting proxy has nothing to echo. A rejection therefore reaches the agent
as a bare `400` with no reason, indistinguishable from a malformed request or a
bad key. The cause was found by speaking the wire contract directly:
`POST {model, state, questions}` to `JEV_API_BASE_URL`, with payloads generated
at controlled sizes, recording only the status code and the returned error body.

## Finding 1 — the binding limit is 20 questions per call, not payload size

The gateway counts *questions* (the keys of the `questions` object) and rejects
the 21st with `{"error":{"message":"at most 20 questions per call", ...}}`.
Body size is irrelevant below the ceiling: a 64 KB body carrying 2 questions
passes in 1s.

| probe | questions | body | result |
|---|---|---|---|
| verify 10 claims, 1 evidence | 20 | 6,219 B | 200 |
| verify 11 claims, 1 evidence | 22 | 6,809 B | 400 |
| verify 6 claims, 2 evidence items | 18 | 5,687 B | 200 |
| verify 7 claims, 2 evidence items | 21 | 6,534 B | 400 |
| noul 20 propositions | 20 | 4,396 B | 200 |
| noul 21 propositions | 21 | 4,614 B | 400 |
| rerank 20 candidates | 20 | 4,057 B | 200 |
| rerank 21 candidates | 21 | 4,257 B | 400 |
| audit 5 records | 20 | 6,139 B | 200 |
| audit 6 records | 24 | 7,317 B | 400 |

The observed failure that started this — `jev_verify` with 10 claims and
**five** evidence items — is 30 questions, because `jev_verify` emits a third
`source_` question per claim as soon as the evidence list holds more than one
item.

## Finding 2 — a second, higher ceiling on the request body

With 2 questions (question count far from binding), a large body fails with a
different, *nested* error carrying the upstream reason
(`{"error_type":"max_tokens_exceeded"}`):

| evidence | body | result |
|---|---|---|
| 144 KB | 148,227 B | 200 |
| 160 KB | 164,611 B | 400 `max_tokens_exceeded` |
| 192 KB | 197,379 B | 400 `max_tokens_exceeded` |
| 256 KB | 262,907 B | 400 `max_tokens_exceeded` |

Caveat: the probe text is a repeated English sentence, which tokenizes
compactly. Real evidence (code, diffs, JSON) buys fewer tokens per byte, so
treat ~148 KB as an upper bound for favourable text and keep real payloads well
under it. The question budget binds long before this in every tool except the
candidate-shaped ones.

## Finding 3 — per-tool question budgets, and the traps

Question counts derived from the server source (`dist/server.js`), with the
effective per-call cap on this gateway:

| tool | questions per unit | server cap | effective here |
|---|---|---|---|
| `jev_verify` | 2 per claim, +1 per claim when evidence holds >1 item | none | **10 claims** (1 evidence) / **6 claims** (≥2) |
| `jev_noul` | 1 per proposition | 64 | **20 propositions** |
| `jev_classify` | 1 per item | 64 items | **20 items** |
| `jev_rerank` | 1 per candidate | 250 | **20 candidates** |
| `jev_find` | 2 total (`best` + `exists`), independent of candidate count | 250 candidates | question budget never binds; the ~148 KB body ceiling does |
| `jev_audit` | 4 per non-empty record, 5 when a value is empty | 32 records | **5 records** (4 if any value is empty) |
| `jev_review` | 5 per diff; in per-file mode 5 per file | 16 files | **4 files** per-file mode |
| `jev_gate` | 5 rubrics + 1 per completion claim | 16 claims | **15 claims** (single-diff mode) |
| `jev_compare` | 1 per aspect | 10 aspects | 10 aspects (fits) |
| `jev_extract` | 1 per field that matched | 32 fields | **20 fields** |
| `jev_screen` | 2, or 3 with a purpose | — | fits |
| `jev_decide` | candidates × requirements | 6 × 3 | 18 (fits) |

The traps are the rows where the server advertises far more than the gateway
accepts: an audit of 6 records, a per-file review of 5 files, a rerank of 25
candidates and a verify of 12 claims all pass the server's own validation and
fail at the endpoint.

## Operating rules

1. **Budget by questions, not by size.** Split so each call stays at or under
   the effective cap in the table above; the question count is knowable before
   sending, the token count is not.
2. **One evidence item per `jev_verify` call doubles the claim budget**
   (10 instead of 6). Merge evidence into one document when it can be done
   without losing the per-item attribution, or accept 6 claims.
3. **A bare `Jev-compatible endpoint 400` means split and retry**, never a
   failed claim and never a reason to abandon the check. The reason is not
   recoverable from the client, so the fix is always "smaller batch".
4. **Nothing here invalidates the tools' semantics.** The caps are transport
   limits: the verdicts, distributions and fail-closed behaviour are unchanged.
