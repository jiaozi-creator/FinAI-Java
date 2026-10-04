# Financial evaluation assets

This directory stores metadata, provisional answer sheets, and engineering pipeline
run logs for evaluation inputs.

Original annual-report PDFs are **not** committed. Store authorized originals under
`data/raw/` (gitignored) using the names in `config/companies.yaml`, and record
SHA-256, source, report period, usage condition, and reviewer status in
`financial-eval-manifest.yaml`.

## Current samples (2026-09-29)

| sample_id | status | answer sheet |
|---|---|---|
| `cn_600519_2023_ar` | PDF obtained, engineering pipeline COMPLETED, **待 C/E 复核** | `answers/cn_600519_2023_ar.pending.yaml` |
| `cn_000858_2023_ar` | PDF obtained, engineering pipeline COMPLETED, **待 C/E 复核** | `answers/cn_000858_2023_ar.pending.yaml` |
| `synthetic_demo_statement` | Synthetic DEMO01 only | none (must not be used as financial baseline) |

Official PDF local paths (not in git):

- `data/raw/600519_2023_annual.pdf`
- `data/raw/000858_2023_annual.pdf`

Source citations and hashes: see `financial-eval-manifest.yaml`.
Pipeline smoke record: `pipeline-run-log.yaml`.

## Isolation rules

1. DEMO01 / `demo-statement.txt` is for orchestration and API smoke only.
2. Pending answer sheets are **engineering provisional**. They must not be frozen as
   accuracy baselines until C (accounting), E (valuation), and D (risk) review.
3. Automated tests may assert extraction stability when PDFs exist, but must not claim
   financial accuracy percentages before C/E sign-off.
4. Missing fields use `missing_reason`; never substitute `0` for unavailable values.

## Review sequence

1. A records the official source and computes the PDF SHA-256.
2. A creates provisional field-level labels from pipeline output (value, unit, period, scope, page, quote).
3. C reviews accounting definitions, units, periods and arithmetic answers.
4. E reviews DCF assumptions and valuation outputs; D reviews risk interpretation.
5. A freezes the manifest and answer-sheet version used by automated accuracy tests.
