#!/usr/bin/env node

import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import { existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import {
  ECOMMERCE_CONTRACT_FIELD_MAP,
  buildEcommerceContractArtifacts,
  defaultTemplatePath,
  renderTemplate
} from './ecommerce-contract.mapping.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_LOGO_PATH = path.resolve(
  __dirname,
  '../../src/main/resources/contrats/logo.png'
);

async function main() {
  const options = parseArgs(process.argv.slice(2));

  if (!options.input) {
    throw new Error('Usage: node generate-ecommerce-contract.mjs --input <json> [--html-out <file>] [--pdf-out <file>] [--mapping-out <file>] [--strict]');
  }

  const templatePath = options.template ?? defaultTemplatePath(__filename);
  const input = JSON.parse(await fs.readFile(options.input, 'utf8'));
  const templateHtml = await fs.readFile(templatePath, 'utf8');

  const artifacts = buildEcommerceContractArtifacts(input);
  artifacts.templateValues.logoUrl = await resolveLogoDataUri(options.logo ?? DEFAULT_LOGO_PATH);
  const renderedHtml = renderTemplate(templateHtml, artifacts.templateValues);

  if (options.strict && artifacts.warnings.missingRequiredFields.length > 0) {
    throw new Error(
      `Required contract fields are missing: ${artifacts.warnings.missingRequiredFields.join(', ')}`
    );
  }

  const defaultBaseName = path.basename(options.input, path.extname(options.input));
  const htmlOut = options.htmlOut ?? path.resolve(__dirname, `${defaultBaseName}.preview.html`);
  const pdfOut = options.pdfOut;
  const mappingOut =
    options.mappingOut ?? path.resolve(__dirname, `${defaultBaseName}.mapping.json`);

  if (options.preview || !pdfOut) {
    await fs.writeFile(htmlOut, renderedHtml, 'utf8');
  }

  await fs.writeFile(
    mappingOut,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        warnings: artifacts.warnings,
        fields: ECOMMERCE_CONTRACT_FIELD_MAP,
        mappingReport: artifacts.mappingReport
      },
      null,
      2
    ),
    'utf8'
  );

  if (pdfOut) {
    await renderPdf(renderedHtml, pdfOut, options.browser);
  }

  const outputs = [];
  if (options.preview || !pdfOut) {
    outputs.push(`HTML preview: ${htmlOut}`);
  }
  if (pdfOut) {
    outputs.push(`PDF: ${pdfOut}`);
  }
  outputs.push(`Mapping report: ${mappingOut}`);

  process.stdout.write(`${outputs.join('\n')}\n`);

  if (artifacts.warnings.missingRequiredFields.length > 0) {
    process.stdout.write(
      `Warnings: missing ${artifacts.warnings.missingRequiredFields.join(', ')}\n`
    );
  }
}

async function renderPdf(renderedHtml, pdfOut, browserOption) {
  const browserExecutable = resolveBrowserExecutable(browserOption);
  if (!browserExecutable) {
    throw new Error('No Chromium browser found. Install Chrome/Edge or pass --browser <path>.');
  }

  const tempHtml = path.join(os.tmpdir(), `lana-contract-${Date.now()}.html`);
  await fs.writeFile(tempHtml, renderedHtml, 'utf8');

  try {
    const renderedWithPuppeteer = await tryRenderWithPuppeteer(
      renderedHtml,
      pdfOut,
      browserExecutable
    );
    if (!renderedWithPuppeteer) {
      await renderWithBrowserCli(browserExecutable, tempHtml, pdfOut);
    }
  } finally {
    await fs.rm(tempHtml, { force: true });
  }
}

async function tryRenderWithPuppeteer(renderedHtml, pdfOut, browserExecutable) {
  const puppeteer = await importOptional('puppeteer-core') ?? await importOptional('puppeteer');
  if (!puppeteer) {
    return false;
  }

  const browser = await puppeteer.launch({
    executablePath: browserExecutable,
    headless: true
  });

  try {
    const page = await browser.newPage();
    await page.setContent(renderedHtml, { waitUntil: 'networkidle0' });
    await page.emulateMediaType('screen');
    await page.pdf({
      path: pdfOut,
      format: 'A4',
      printBackground: true,
      preferCSSPageSize: true,
      margin: {
        top: '0',
        right: '0',
        bottom: '0',
        left: '0'
      }
    });
    return true;
  } finally {
    await browser.close();
  }
}

async function renderWithBrowserCli(browserExecutable, htmlPath, pdfOut) {
  const command = [
    '--headless=new',
    '--disable-gpu',
    '--run-all-compositor-stages-before-draw',
    '--virtual-time-budget=1500',
    '--print-to-pdf-no-header',
    `--print-to-pdf=${path.resolve(pdfOut)}`,
    pathToFileURL(htmlPath).href
  ];

  const output = await runProcess(browserExecutable, command);
  try {
    await fs.access(pdfOut);
  } catch {
    throw new Error(output || 'Chrome/Edge did not generate the expected PDF file.');
  }
}

function resolveBrowserExecutable(browserOption) {
  if (browserOption) {
    return path.resolve(browserOption);
  }

  const candidates = [
    process.env.CHROME_EXECUTABLE,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ].filter(Boolean);

  return candidates.find((candidate) => candidate && existsSyncSafe(candidate)) ?? '';
}

function existsSyncSafe(filePath) {
  try {
    return existsSync(filePath);
  } catch {
    return false;
  }
}

async function importOptional(moduleName) {
  try {
    return await import(moduleName);
  } catch {
    return null;
  }
}

function runProcess(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve(stdout.trim());
      } else {
        reject(new Error([stdout, stderr].filter(Boolean).join('\n')));
      }
    });
  });
}

function parseArgs(argv) {
  const options = {
    preview: false,
    strict: false
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    switch (arg) {
      case '--input':
        options.input = path.resolve(argv[++index]);
        break;
      case '--template':
        options.template = path.resolve(argv[++index]);
        break;
      case '--html-out':
        options.htmlOut = path.resolve(argv[++index]);
        break;
      case '--pdf-out':
        options.pdfOut = path.resolve(argv[++index]);
        break;
      case '--mapping-out':
        options.mappingOut = path.resolve(argv[++index]);
        break;
      case '--browser':
        options.browser = argv[++index];
        break;
      case '--logo':
        options.logo = path.resolve(argv[++index]);
        break;
      case '--preview':
        options.preview = true;
        break;
      case '--strict':
        options.strict = true;
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return options;
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});

async function resolveLogoDataUri(logoPath) {
  try {
    const extension = path.extname(logoPath).toLowerCase();
    const mimeType = extension === '.png' ? 'image/png' : extension === '.jpg' || extension === '.jpeg'
      ? 'image/jpeg'
      : 'application/octet-stream';
    const buffer = await fs.readFile(logoPath);
    return `data:${mimeType};base64,${buffer.toString('base64')}`;
  } catch {
    return '';
  }
}
