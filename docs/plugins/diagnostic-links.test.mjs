import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

import {diagnostics} from './diagnostics.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const docsDirectory = path.resolve(here, '../docs');

test('every diagnostic name outside its own page or a code block has a check-page link', () => {
  const failures = [];

  for (const file of markdownFiles(docsDirectory)) {
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    let fence = undefined;

    for (const [index, line] of lines.entries()) {
      const fenceStart = line.match(/^\s*(`{3,}|~{3,})/);
      if (fenceStart) {
        if (fence === undefined) fence = fenceStart[1][0];
        else if (fence === fenceStart[1][0]) fence = undefined;
        continue;
      }
      if (fence !== undefined || /^\s*\[\/\/\]:/.test(line)) continue;
      const lineLinks = links(line);

      for (const diagnostic of diagnostics.values()) {
        for (const occurrence of occurrences(line, diagnostic.name)) {
          const link = lineLinks.find((candidate) =>
            candidate.labelStart <= occurrence && occurrence < candidate.labelEnd,
          );
          const location = `${path.relative(docsDirectory, file)}:${index + 1}`;
          const expectedPage = path.resolve(docsDirectory, `${diagnostic.docs}.md`);

          if (file === expectedPage) {
            if (link !== undefined) failures.push(`${location}: ${diagnostic.name} links to its own page`);
            continue;
          }

          if (link === undefined) {
            const checkPageAlreadyLinked = /^\s*\|/.test(line) && lineLinks.some((candidate) => {
              const target = candidate.target.split('#', 1)[0];
              return path.resolve(path.dirname(file), target) === expectedPage;
            });
            if (checkPageAlreadyLinked) continue;

            failures.push(`${location}: ${diagnostic.name} is not a link`);
            continue;
          }

          const target = link.target.split('#', 1)[0];
          const actualPage = path.resolve(path.dirname(file), target);
          if (actualPage !== expectedPage) {
            failures.push(`${location}: ${diagnostic.name} links to ${link.target}, expected ${diagnostic.docs}.md`);
          }
        }
      }
    }
  }

  assert.deepEqual(failures, []);
});

function markdownFiles(directory) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
    const child = path.join(directory, entry.name);
    if (entry.isDirectory()) return markdownFiles(child);
    return /\.mdx?$/.test(entry.name) ? [child] : [];
  });
}

function occurrences(line, query) {
  const result = [];
  let index = line.indexOf(query);
  while (index !== -1) {
    result.push(index);
    index = line.indexOf(query, index + query.length);
  }
  return result;
}

function links(line) {
  return [...line.matchAll(/\[([^\]]*)\]\(([^)]+)\)/g)].map((match) => ({
    labelStart: match.index + 1,
    labelEnd: match.index + 1 + match[1].length,
    target: match[2],
  }));
}
