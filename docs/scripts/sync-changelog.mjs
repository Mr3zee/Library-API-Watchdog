import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repositoryDirectory = fileURLToPath(new URL('../../', import.meta.url));
const source = path.join(repositoryDirectory, 'CHANGELOG.md');
const destination = path.join(repositoryDirectory, 'docs/pages/changelog.md');
const diagnosticsFile = path.join(repositoryDirectory, 'diagnostics.json');
const args = process.argv.slice(2);
const websiteUrl = 'https://kotlin.github.io/Library-API-Watchdog/';

// A link to the website becomes a link to the page file, so the docs build checks that the page exists.
function websiteLinks(markdown) {
  return markdown.replaceAll(
    new RegExp(`\\]\\(${websiteUrl.replaceAll('.', '\\.')}([^)#]+)(#[^)]*)?\\)`, 'g'),
    (_, page, anchor = '') => `](${page}.md${anchor})`,
  );
}

// A diagnostic name outside a code block, a code span, or a link becomes a link to its check page. The docs tests
// need the link, and a pull request title in the generated notes has none. The longest name matches first, so a
// name that has another name inside gets one link.
function diagnosticLinks(markdown) {
  const diagnostics = JSON.parse(fs.readFileSync(diagnosticsFile, 'utf8')).diagnostics
    .sort((a, b) => b.name.length - a.name.length);
  const names = new RegExp(`(?<![A-Z0-9_])(?:${diagnostics.map((d) => d.name).join('|')})(?![A-Z0-9_])`, 'g');
  const pages = new Map(diagnostics.map((d) => [d.name, `${d.docs}.md`]));
  const protectedSpans = /`[^`]*`|\[[^\]]*\]\([^)]*\)|https?:\/\/\S+/g;
  let fence = undefined;

  return markdown.split('\n').map((line) => {
    const fenceStart = line.match(/^\s*(`{3,}|~{3,})/);
    if (fenceStart) {
      if (fence === undefined) fence = fenceStart[1][0];
      else if (fence === fenceStart[1][0]) fence = undefined;
      return line;
    }
    if (fence !== undefined) return line;
    let result = '';
    let position = 0;
    for (const span of line.matchAll(protectedSpans)) {
      result += link(line.slice(position, span.index)) + span[0];
      position = span.index + span[0].length;
    }
    return result + link(line.slice(position));
  }).join('\n');

  function link(text) {
    return text.replaceAll(names, (name) => `[${name}](${pages.get(name)})`);
  }
}

if (args.length > 1 || args.some((arg) => arg !== '--check')) {
  console.error('Usage: node docs/scripts/sync-changelog.mjs [--check]');
  process.exitCode = 1;
} else {
  const expected = [
    '---',
    'title: Changelog',
    'slug: /changelog',
    'description: The changes of each release.',
    'custom_edit_url: https://github.com/Kotlin/Library-API-Watchdog/edit/main/CHANGELOG.md',
    // The release sections are GitHub generated notes. MDX can't parse a pull request title with `<` or `{`.
    'mdx:',
    '  format: md',
    '---',
    '',
    '<!-- Generated from CHANGELOG.md. Run npm run sync:changelog from docs/. -->',
    '',
    diagnosticLinks(websiteLinks(fs.readFileSync(source, 'utf8'))),
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
