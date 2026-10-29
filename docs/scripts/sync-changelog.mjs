import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repositoryDirectory = fileURLToPath(new URL('../../', import.meta.url));
const source = path.join(repositoryDirectory, 'CHANGELOG.md');
const destination = path.join(repositoryDirectory, 'docs/pages/changelog.md');
const args = process.argv.slice(2);
const websiteUrl = 'https://mr3zee.github.io/Library-API-Watchdog/';

// A link to the website becomes a link to the page file, so the docs build checks that the page exists.
function websiteLinks(markdown) {
  return markdown.replaceAll(
    new RegExp(`\\]\\(${websiteUrl.replaceAll('.', '\\.')}([^)#]+)(#[^)]*)?\\)`, 'g'),
    (_, page, anchor = '') => `](${page}.md${anchor})`,
  );
}

if (args.length > 1 || args.some((arg) => arg !== '--check')) {
  console.error('Usage: node docs/scripts/sync-changelog.mjs [--check]');
  process.exitCode = 1;
} else {
  const expected = [
    '---',
    'title: Changelog',
    'slug: /changelog',
    'custom_edit_url: https://github.com/Mr3zee/Library-API-Watchdog/edit/main/CHANGELOG.md',
    '---',
    '',
    '<!-- Generated from CHANGELOG.md. Run npm run sync:changelog from docs/. -->',
    '',
    websiteLinks(fs.readFileSync(source, 'utf8')),
  ].join('\n');

  if (args.includes('--check')) {
    const actual = fs.existsSync(destination) ? fs.readFileSync(destination, 'utf8') : null;
    if (actual !== expected) {
      console.error('The website changelog is stale. Run `npm run sync:changelog` from docs/.');
      process.exitCode = 1;
    } else {
      console.log('The website changelog matches CHANGELOG.md.');
    }
  } else {
    fs.writeFileSync(destination, expected);
    console.log('Updated the website changelog from CHANGELOG.md.');
  }
}
