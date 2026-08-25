import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const overview = fs.readFileSync(path.resolve(here, '../docs/overview.md'), 'utf8');
const readme = fs.readFileSync(path.resolve(here, '../../README.md'), 'utf8');

const sectionPairs = [
  ['API surface checks', 'API surface'],
  ['Java interop checks', 'Java interop'],
  ['DSL marker checks', 'DSL markers'],
  ['Meaningful exemptions', 'Exemption hygiene'],
];

test('README check order and descriptions match the docs overview', () => {
  for (const [overviewHeading, readmeHeading] of sectionPairs) {
    const expected = entries(overview, overviewHeading, ':');
    const actual = entries(readme, readmeHeading, ' -');

    assert.deepEqual(
      actual,
      expected,
      `README section "${readmeHeading}" must match docs/docs/overview.md`,
    );
  }
});

function entries(markdown, heading, descriptionSeparator) {
  const items = listItems(section(markdown, heading));
  assert.notEqual(items.length, 0, `Missing check entries in section "${heading}"`);

  return items.map((item) => {
    const link = item.match(/^\[.*\]\(([^)]+)\)/s);
    assert.ok(link, `Expected a linked list item in section "${heading}": ${item}`);

    const separator = item.indexOf(descriptionSeparator, link[0].length);
    assert.notEqual(separator, -1, `Expected a description in section "${heading}": ${item}`);

    return {
      check: checkPath(link[1]),
      description: normalize(item.slice(separator + descriptionSeparator.length)),
    };
  });
}

function section(markdown, heading) {
  const lines = markdown.split('\n');
  const headingLine = `### ${heading}`;
  const start = lines.indexOf(headingLine);
  assert.notEqual(start, -1, `Missing section "${heading}"`);

  const end = lines.findIndex((line, index) => index > start && /^#{1,3} /.test(line));
  return lines.slice(start + 1, end === -1 ? undefined : end);
}

function listItems(lines) {
  const items = [];
  let current;

  for (const line of lines) {
    if (line.startsWith('- ')) {
      if (current !== undefined) items.push(current);
      current = line.slice(2);
    } else if (current !== undefined && /^\s+\S/.test(line)) {
      current += `\n${line.trim()}`;
    } else if (current !== undefined) {
      items.push(current);
      current = undefined;
    }
  }

  if (current !== undefined) items.push(current);
  return items;
}

function checkPath(target) {
  return target
    .replace(/^\.\//, '')
    .replace(/^https:\/\/mr3zee\.github\.io\/Library-API-Watchdog\//, '')
    .replace(/\.md$/, '');
}

function normalize(value) {
  return value.replace(/\s+/g, ' ').trim();
}
