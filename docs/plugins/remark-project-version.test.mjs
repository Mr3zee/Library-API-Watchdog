import assert from 'node:assert/strict';
import test from 'node:test';

import {
  kotlinVersion,
  kotlinVersionTemplate,
  projectVersion,
  projectVersionTemplate,
  readProjectVersion,
  remarkProjectVersion,
} from './remark-project-version.mjs';

test('uses LIBRARY_VERSION for website samples and rejects invalid overrides', () => {
  assert.equal(readProjectVersion({LIBRARY_VERSION: '9.8.7-dev-1'}), '9.8.7-dev-1');
  for (const version of ['', ' ', '1.2', '1.2.3\n', '1.2.3\nX=value']) {
    assert.throws(() => readProjectVersion({LIBRARY_VERSION: version}));
  }
});

test('uses explicit versions from the loader options', () => {
  const tree = {
    type: 'root',
    children: [{
      type: 'code',
      value: `${projectVersionTemplate} ${kotlinVersionTemplate}`,
    }],
  };
  remarkProjectVersion({libraryVersion: '9.8.7', kotlin: '9.0.0'})(tree);
  assert.equal(tree.children[0].value, '9.8.7 9.0.0');
});

test('replaces project and Kotlin version templates in code', () => {
  const tree = {
    type: 'root',
    children: [
      {
        type: 'code',
        lang: 'kotlin',
        value: `plugins {
  kotlin("multiplatform") version "${kotlinVersionTemplate}"
  kotlin("library.api-watchdog") version "${projectVersionTemplate}"
}`,
      },
      {
        type: 'inlineCode',
        value: kotlinVersionTemplate,
      },
    ],
  };

  remarkProjectVersion()(tree);

  assert.equal(
    tree.children[0].value,
    `plugins {
  kotlin("multiplatform") version "${kotlinVersion}"
  kotlin("library.api-watchdog") version "${projectVersion}"
}`,
  );
  assert.equal(tree.children[1].value, kotlinVersion);
});
