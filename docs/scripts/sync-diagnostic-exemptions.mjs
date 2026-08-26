import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repositoryDirectory = path.resolve(here, '../..');
const docsDirectory = path.join(repositoryDirectory, 'docs/docs');
const diagnosticsFile = path.join(repositoryDirectory, 'diagnostics.json');
const checkOnly = process.argv.includes('--check');

const original = fs.readFileSync(diagnosticsFile, 'utf8');
const registry = JSON.parse(original);
const diagnostics = new Map(registry.diagnostics.map((diagnostic) => [diagnostic.name, diagnostic]));
const parsingFailures = [];
const exemptions = readExemptions();
const failures = validateExemptions();

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  const changed = [];
  for (const [name, exemption] of exemptions) {
    const diagnostic = diagnostics.get(name);
    const separator = diagnostic.message.lastIndexOf('');
    const currentWording = diagnostic.message.slice(separator + 1).join(' ');
    if (currentWording !== exemption.wording) {
      changed.push(name);
      diagnostic.message = [
        ...diagnostic.message.slice(0, separator + 1),
        ...wrapMessageParagraph(exemption.wording),
      ];
    }
  }

  const synchronized = `${JSON.stringify(registry, null, 2)}\n`;
  if (checkOnly && changed.length > 0) {
    console.error(
      `Diagnostic exemption wording is stale for: ${changed.join(', ')}.\n` +
        'Run `npm run sync:diagnostic-exemptions` from docs/.',
    );
    process.exitCode = 1;
  } else if (checkOnly) {
    console.log(`Diagnostic exemption wording is synchronized (${exemptions.size} diagnostics).`);
  } else {
    fs.writeFileSync(diagnosticsFile, synchronized);
    console.log(`Synchronized diagnostic exemption wording (${exemptions.size} diagnostics).`);
  }
}

function wrapMessageParagraph(paragraph) {
  const lines = [];
  for (const word of paragraph.split(' ')) {
    if (lines.length === 0 || `${lines.at(-1)} ${word}`.length > 100) {
      lines.push(word);
    } else {
      lines[lines.length - 1] += ` ${word}`;
    }
  }
  return lines;
}

function readExemptions() {
  const result = new Map();
  const marker = /^<!-- diagnostic-exemption: ([A-Z][A-Z0-9_]*) -->$/;
  const substitutionMarker =
    /^<!-- diagnostic-exemption-substitution: (.+) => (\{[0-9]+\}) -->$/;
  const tableMarker = '<!-- diagnostic-exemption-table -->';

  for (const file of markdownFiles(docsDirectory)) {
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
    let section;
    for (const [index, line] of lines.entries()) {
      if (/^## /.test(line)) section = line;
      if (line === tableMarker) {
        readExemptionTable(result, lines, index, relativeMarkdownFile(file), section);
        continue;
      }
      const match = line.match(marker);
      if (match === null) continue;

      const name = match[1];
      const paragraph = [];
      const substitutions = [];
      for (let paragraphIndex = index + 1; paragraphIndex < lines.length; paragraphIndex += 1) {
        const paragraphLine = lines[paragraphIndex].trim();
        if (paragraphLine === '') break;

        const substitution = paragraphLine.match(substitutionMarker);
        if (substitution !== null) {
          substitutions.push({source: substitution[1], argument: substitution[2]});
          continue;
        }
        if (paragraphLine.startsWith('<!-- diagnostic-exemption-substitution:')) {
          parsingFailures.push(
            `${relativeMarkdownFile(file)}:${paragraphIndex + 1}: malformed exemption substitution.`,
          );
          continue;
        }
        paragraph.push(paragraphLine);
      }

      const documentationWording = paragraph.join(' ');
      let diagnosticWording = documentationWording;
      for (const substitution of substitutions) {
        const occurrences = diagnosticWording.split(substitution.source).length - 1;
        if (occurrences !== 1) {
          parsingFailures.push(
            `${relativeMarkdownFile(file)}:${index + 1}: exemption substitution source ` +
              `\`${substitution.source}\` must occur exactly once.`,
          );
          continue;
        }
        diagnosticWording = diagnosticWording.replace(substitution.source, substitution.argument);
      }

      addExemption(result, name, {
        file: relativeMarkdownFile(file),
        line: index + 1,
        section,
        wording: diagnosticWording,
      });
    }
  }

  return result;
}

function validateExemptions() {
  const failures = [...parsingFailures];
  const synchronizedDiagnostics = [...diagnostics.values()].filter((diagnostic) =>
    diagnostic.message.some((line) => line.includes('Intentionally')),
  );

  for (const diagnostic of synchronizedDiagnostics) {
    if (!exemptions.has(diagnostic.name)) {
      failures.push(`${diagnostic.name}: missing diagnostic-exemption marker in its check page.`);
    }
  }

  for (const [name, exemption] of exemptions) {
    const location = `${exemption.file}:${exemption.line}`;
    const diagnostic = diagnostics.get(name);
    if (diagnostic === undefined) {
      failures.push(`${location}: unknown diagnostic ${name}.`);
      continue;
    }
    if (exemption.duplicateFile !== undefined) {
      failures.push(`${name}: duplicate markers in ${exemption.file} and ${exemption.duplicateFile}.`);
    }

    const expectedFile = `${diagnostic.docs}.md`;
    if (exemption.file !== expectedFile) {
      failures.push(`${location}: marker belongs in ${expectedFile}.`);
    }
    if (!/^## Exemptions?$/.test(exemption.section ?? '')) {
      failures.push(`${location}: marker must be in the Exemption section.`);
    }
    if (
      !exemption.wording.startsWith('If this API shape is intentional, apply `@') ||
      !exemption.wording.includes('Intentionally')
    ) {
      failures.push(`${location}: exemption wording must use the standard opening.`);
    }
    if (!diagnostic.message.includes('')) {
      failures.push(`${name}: diagnostic message needs a separate exemption paragraph.`);
    }
  }

  return failures;
}

function readExemptionTable(result, lines, markerIndex, file, section) {
  const location = `${file}:${markerIndex + 1}`;
  const header = '| Missing behavior | Individual exemption | Combined exemption |';
  const normalizedHeader = lines[markerIndex + 1]?.replace(/\s+/g, ' ').trim();
  if (normalizedHeader !== header) {
    parsingFailures.push(`${location}: exemption table has an unexpected header.`);
    return;
  }

  const rowPattern =
    /^\| `(equals|hashCode|toString)` \| `(@Intentionally[A-Za-z0-9]+)` \| `(@Intentionally[A-Za-z0-9]+)` \|$/;
  const diagnosticByBehavior = {
    equals: 'STATEFUL_CLASS_WITHOUT_EQUALS',
    hashCode: 'STATEFUL_CLASS_WITHOUT_HASH_CODE',
    toString: 'STATEFUL_CLASS_WITHOUT_TO_STRING',
  };
  let rowCount = 0;
  for (let index = markerIndex + 3; index < lines.length && lines[index].trim() !== ''; index += 1) {
    const normalizedRow = lines[index].replace(/\s+/g, ' ').trim();
    const row = normalizedRow.match(rowPattern);
    if (row === null) {
      parsingFailures.push(`${file}:${index + 1}: malformed diagnostic exemption table row.`);
      continue;
    }

    const [, behavior, individual, combined] = row;
    const name = diagnosticByBehavior[behavior];
    addExemption(result, name, {
      file,
      line: index + 1,
      section,
      wording:
        `If this API shape is intentional, apply \`${individual}\` to the class, or apply ` +
        `\`${combined}\` to acknowledge all three inherited implementations.`,
    });
    rowCount += 1;
  }
  if (rowCount === 0) parsingFailures.push(`${location}: exemption table has no diagnostic rows.`);
}

function addExemption(result, name, exemption) {
  if (result.has(name)) {
    result.set(name, {...result.get(name), duplicateFile: exemption.file});
  } else {
    result.set(name, exemption);
  }
}

function relativeMarkdownFile(file) {
  return path.relative(docsDirectory, file).split(path.sep).join('/');
}

function markdownFiles(directory) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
    const child = path.join(directory, entry.name);
    if (entry.isDirectory()) return markdownFiles(child);
    return /\.mdx?$/.test(entry.name) ? [child] : [];
  });
}
