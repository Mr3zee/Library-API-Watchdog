import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** The shared diagnostics source of truth, also read by the compiler plugin build. */
export const diagnosticsFile = path.resolve(here, '../../diagnostics.json');

const source = JSON.parse(fs.readFileSync(diagnosticsFile, 'utf8'));

/** Parameter values every diagnostic can use, keyed by name. */
export const sharedParameterValues = joinParameterValues(source.sharedParameterValues ?? {});

/**
 * Every diagnostic, keyed by name. Its `parameterValues` include the shared values, so a
 * consumer resolves a `$name` reference against one map.
 */
export const diagnostics = new Map(
  source.diagnostics.map((diagnostic) => [
    diagnostic.name,
    {
      ...diagnostic,
      message: joinTextLines(diagnostic.message),
      parameterValues: {...sharedParameterValues, ...joinParameterValues(diagnostic.parameterValues ?? {})},
    },
  ]),
);

function joinParameterValues(values) {
  return Object.fromEntries(Object.entries(values).map(([name, lines]) => [name, joinTextLines(lines)]));
}

/**
 * Joins source-wrapped lines with a space. An empty line is a paragraph break, and a code line,
 * which starts with four spaces, keeps the line breaks around it.
 */
function joinTextLines(lines) {
  return lines.reduce((message, line, index) => {
    if (index === 0) return line;
    const previous = lines[index - 1];
    const hardBreak = line === '' || previous === '' || isCodeLine(line) || isCodeLine(previous);
    return `${message}${hardBreak ? '\n' : ' '}${line}`;
  }, '');
}

function isCodeLine(line) {
  return line.startsWith('    ');
}
