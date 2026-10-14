import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {joinTextLines} from '../src/utils/diagnostic-messages.mjs';

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
