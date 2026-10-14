import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export const diagnosticsFile = path.resolve(here, '../../diagnostics.json');

const source = JSON.parse(fs.readFileSync(diagnosticsFile, 'utf8'));

export const sharedParameterValues = joinParameterValues(source.sharedParameterValues ?? {});

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
 * Joins the wrapped lines of a message with a space. An empty line and a code line (four leading
 * spaces) keep the line breaks around them.
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
