import assert from 'node:assert/strict';
import test from 'node:test';

import {
  kotlinVersion,
  kotlinVersionTemplate,
  projectVersion,
  projectVersionTemplate,
  remarkProjectVersion,
} from './remark-project-version.mjs';

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
