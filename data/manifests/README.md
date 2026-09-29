# Financial evaluation assets

This directory stores metadata and reviewed answer sheets for evaluation inputs.
Original annual-report PDFs are not committed here. Store authorized originals outside
the repository and record their SHA-256, source, report period, license/usage condition,
and reviewer status in the manifest.

The `pending` entries are placeholders only. They must not be used as a test baseline
until the official PDF has been obtained and C/E have reviewed the answer sheet.

Required review sequence:

1. A records the official source and computes the PDF SHA-256.
2. A creates field-level labels with value, unit, period, scope, page and source quote.
3. C reviews accounting definitions, units, periods and arithmetic answers.
4. E reviews DCF assumptions and valuation outputs; D reviews risk interpretation.
5. A freezes the manifest and answer-sheet version used by automated tests.
