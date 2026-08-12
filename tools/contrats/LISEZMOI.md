# E-Commerce Contract Rendering

This folder contains a pixel-faithful rendering pipeline for the LANA CASH e-commerce contract.

Files:

- `../../src/main/resources/contrats/contrat_affiliation_ecommerce.html`
  Exact HTML contract template with dynamic placeholders.
- `ecommerce-contract.mapping.mjs`
  Mapping and validation layer from affiliation JSON to contract placeholders.
- `generate-ecommerce-contract.mjs`
  Preview + PDF generation CLI. It prefers Puppeteer when available and falls back to Chrome/Edge headless printing.
- `example-affiliation.json`
  Example JSON input.

Example commands:

```bash
node demo/tools/contrats/generate-ecommerce-contract.mjs \
  --input demo/tools/contrats/example-affiliation.json \
  --preview \
  --html-out demo/tools/contrats/example-affiliation.preview.html \
  --mapping-out demo/tools/contrats/example-affiliation.mapping.json
```

```bash
node demo/tools/contrats/generate-ecommerce-contract.mjs \
  --input demo/tools/contrats/example-affiliation.json \
  --pdf-out demo/tools/contrats/example-affiliation.pdf \
  --mapping-out demo/tools/contrats/example-affiliation.mapping.json
```

Optional flags:

- `--template <path>` override the HTML template path
- `--browser <path>` force a specific Chrome/Edge executable
- `--strict` fail when required contract fields are missing
- `--preview` always emit rendered HTML preview even when generating a PDF
