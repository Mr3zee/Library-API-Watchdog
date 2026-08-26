import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** The shared diagnostics source of truth, also read by the compiler plugin build. */
export const diagnosticsFile = path.resolve(here, '../../diagnostics.json');

const source = JSON.parse(fs.readFileSync(diagnosticsFile, 'utf8'));

/** Every diagnostic, keyed by name. */
export const diagnostics = new Map(
  source.diagnostics.map((diagnostic) => [
    diagnostic.name,
    {
      ...diagnostic,
      message: joinTextLines(diagnostic.message),
      ...(diagnostic.parameterValues === undefined
        ? {}
        : {
            parameterValues: Object.fromEntries(
              Object.entries(diagnostic.parameterValues).map(([name, lines]) => [name, joinTextLines(lines)]),
            ),
          }),
    },
  ]),
);

/** Joins source-wrapped lines while keeping empty lines as paragraph separators. */
function joinTextLines(lines) {
  return lines.reduce((message, line, index) => {
    if (index === 0) return line;
    return `${message}${line === '' || lines[index - 1] === '' ? '\n' : ' '}${line}`;
  }, '');
}
