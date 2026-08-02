import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** The shared diagnostics source of truth, also read by the compiler plugin build. */
export const diagnosticsFile = path.resolve(here, '../../diagnostics.json');

const source = JSON.parse(fs.readFileSync(diagnosticsFile, 'utf8'));

/** Every diagnostic, keyed by name. */
export const diagnostics = new Map(source.diagnostics.map((it) => [it.name, it]));
