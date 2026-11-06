import assert from 'node:assert/strict';
import test from 'node:test';

import {collectPages, renderIndex, renderPage, renderSample, resolveLink} from './llms-txt.mjs';

const siteUrl = 'https://example.org/site/';
const pages = [
  page('/pages/overview.md', 'index.md', 'Get started'),
  page('/pages/configuration.md', 'configuration.md', 'Configuration'),
  page('/pages/checks/sample.md', 'checks/sample.md', 'Sample check'),
];

function page(sourcePath, markdownPath, title, section = 'Documentation') {
  return {id: markdownPath, section, title, sourcePath, markdownPath, url: `${siteUrl}${markdownPath}`};
}

test('a sample shows the Focused view with a comment for each diagnostic', () => {
  const sample = [
    '// !hide-focused',
    '@file:JvmName("Work")',
    '',
    '// !hide-focused',
    '/** Processes pending data. */',
    '// !diag[/optimizeForSpeed/] BOOLEAN_PARAMETER_PUBLIC_API ["doWork","optimizeForSpeed"]',
    '// !diag[/doWork/] UNDOCUMENTED_PUBLIC_API ["doWork"]',
    'public fun doWork(optimizeForSpeed: Boolean) {}',
  ].join('\n');

  assert.equal(
    renderSample(sample),
    [
      "// BOOLEAN_PARAMETER_PUBLIC_API on 'optimizeForSpeed'",
      "// UNDOCUMENTED_PUBLIC_API on 'doWork'",
      'public fun doWork(optimizeForSpeed: Boolean) {}',
    ].join('\n'),
  );
});

test('a hide-focused range hides the next lines and a link annotation is gone', () => {
  const sample = [
    '// !hide-focused(1:3)',
    '/**',
    ' * Values.',
    ' */',
    'public class Config(',
    '    // !link[/@Poko/] https://github.com/drewhamilton/Poko',
    '    // !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["$property"]',
    '    @Poko public val tags: MutableList<String>,',
    ')',
    '',
  ].join('\n');

  assert.equal(
    renderSample(sample),
    [
      'public class Config(',
      "    // MUTABLE_COLLECTION_PUBLIC_API on 'MutableList<String>'",
      '    @Poko public val tags: MutableList<String>,',
      ')',
    ].join('\n'),
  );
});

test('a multiline diagnostic range marks the first line that matches', () => {
  const sample = [
    '// !diag[/Point/m] DATA_CLASS_PUBLIC_API ["Point"]',
    'public',
    'data class Point(val x: Int)',
  ].join('\n');

  assert.equal(
    renderSample(sample),
    ['public', "// DATA_CLASS_PUBLIC_API on 'Point'", 'data class Point(val x: Int)'].join('\n'),
  );
});

test('links resolve to the Markdown of the linked page or to the site route', () => {
  const [, configuration, sample] = pages;
  const options = {page: sample, pages, siteUrl};
  assert.equal(resolveLink('../configuration.md#anchor', options), `${configuration.url}#anchor`);
  assert.equal(resolveLink('./sample.md', options), sample.url);
  assert.equal(resolveLink('/checks/other', options), `${siteUrl}checks/other`);
  assert.equal(resolveLink('#anchor', options), '#anchor');
  assert.equal(resolveLink('https://example.com/x', options), 'https://example.com/x');
  assert.throws(() => resolveLink('./missing.md', options), /points to no page/);
});

test('a page keeps its title, resolves links, replaces versions, and renders admonitions', () => {
  const source = [
    '---',
    'slug: /',
    '---',
    '',
    '[![Badge](https://img.example/badge.svg)](https://example.com)',
    '',
    'The first paragraph. See [Configuration](./configuration.md).',
    '',
    ':::tip',
    'Hover over a range.',
    ':::',
    '',
    '```kotlin build.gradle.kts',
    'id("watchdog") version "{{libraryApiWatchdogVersion}}"',
    '```',
    '',
  ].join('\n');

  const {markdown, description} = renderPage(source, {
    page: pages[0],
    pages,
    siteUrl,
    versions: {libraryVersion: '1.2.3', kotlin: '2.4.0'},
  });

  assert.equal(description, 'The first paragraph. See Configuration.');
  assert.equal(
    markdown,
    [
      '# Get started',
      '',
      '[![Badge](https://img.example/badge.svg)](https://example.com)',
      '',
      `The first paragraph. See [Configuration](${siteUrl}configuration.md).`,
      '',
      '> **Tip**: Hover over a range.',
      '',
      '```kotlin build.gradle.kts',
      'id("watchdog") version "1.2.3"',
      '```',
      '',
    ].join('\n'),
  );
});

test('the pages follow the sidebar, and a category gives its section', () => {
  const version = {
    docs: [
      doc('overview', '/site/', 'Get started'),
      doc('checks/java-interop/java-interop', '/site/checks/java-interop/', 'Java interop checks'),
      doc('checks/java-interop/one', '/site/checks/java-interop/one', 'One'),
      doc('changelog', '/site/changelog', 'Changelog', {description: 'The changes.'}),
    ],
    sidebars: {
      docs: [
        {type: 'doc', id: 'overview'},
        {
          type: 'category',
          label: 'Checks',
          items: [
            {
              type: 'category',
              label: 'Java interop',
              link: {type: 'doc', id: 'checks/java-interop/java-interop'},
              items: [{type: 'doc', id: 'checks/java-interop/one'}],
            },
          ],
        },
        {type: 'link', label: 'API', href: 'https://example.org/api/'},
        {type: 'doc', id: 'changelog'},
      ],
    },
  };

  const collected = collectPages(version, {siteDir: '/docs', siteUrl, baseUrl: '/site/'});

  assert.deepEqual(
    collected.map(({id, section, markdownPath, description}) => [id, section, markdownPath, description]),
    [
      ['overview', 'Documentation', 'index.md', undefined],
      ['checks/java-interop/java-interop', 'Checks: Java interop', 'checks/java-interop.md', undefined],
      ['checks/java-interop/one', 'Checks: Java interop', 'checks/java-interop/one.md', undefined],
      ['changelog', 'Documentation', 'changelog.md', 'The changes.'],
    ],
  );
  assert.equal(collected[0].sourcePath, '/docs/pages/overview.md');
  assert.equal(collected[2].url, `${siteUrl}checks/java-interop/one.md`);
});

function doc(id, permalink, title, frontMatter = {}) {
  return {id, permalink, title, frontMatter, source: `@site/pages/${id}.md`};
}

test('the index lists the pages of each section', () => {
  const index = renderIndex(
    [
      {...pages[0], description: 'The start.'},
      {...pages[1]},
      {...pages[2], section: 'Checks', description: 'A check.'},
    ],
    {title: 'Site', tagline: 'A tagline'},
  );

  assert.equal(
    index,
    [
      '# Site',
      '',
      '> A tagline',
      '',
      '## Documentation',
      '',
      `- [Get started](${siteUrl}index.md): The start.`,
      `- [Configuration](${siteUrl}configuration.md)`,
      '',
      '## Checks',
      '',
      `- [Sample check](${siteUrl}checks/sample.md): A check.`,
      '',
    ].join('\n'),
  );
});
