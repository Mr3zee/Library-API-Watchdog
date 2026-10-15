import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const repositoryDirectory = fileURLToPath(new URL('../', import.meta.url));
const centralVariables = [
  'ORG_GRADLE_PROJECT_mavenCentralUsername',
  'ORG_GRADLE_PROJECT_mavenCentralPassword',
  'ORG_GRADLE_PROJECT_signingInMemoryKey',
  'ORG_GRADLE_PROJECT_signingInMemoryKeyPassword',
];
const portalVariables = ['GRADLE_PUBLISH_KEY', 'GRADLE_PUBLISH_SECRET'];

function fixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'watchdog-release-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  for (const file of [
    'scripts/update-version.sh',
    'scripts/lib/project-version.sh',
    'docs/scripts/sync-changelog.mjs',
  ]) {
    const destination = path.join(directory, file);
    fs.mkdirSync(path.dirname(destination), {recursive: true});
    fs.copyFileSync(path.join(repositoryDirectory, file), destination);
  }
  fs.mkdirSync(path.join(directory, 'docs/pages'), {recursive: true});
  fs.writeFileSync(path.join(directory, 'gradle.properties'), 'group=example\nversion=1.2.3\n');
  fs.writeFileSync(path.join(directory, 'README.md'), [
    'kotlin("library.api-watchdog") version "1.2.3"',
    'kotlin("library.api-watchdog-report-aggregation") version "1.2.3"',
    'kotlin("jvm") version "1.2.3"',
    '',
  ].join('\n'));
  fs.writeFileSync(path.join(directory, 'CHANGELOG.md'), '# Changelog\n\n## 1.2.3\n\n- Historical release.\n');
  fs.writeFileSync(path.join(directory, 'docs/package.json'), JSON.stringify({
    name: 'docs', version: '1.2.3', dependencies: {other: '1.2.3'},
  }, null, 2) + '\n');
  fs.writeFileSync(path.join(directory, 'docs/package-lock.json'), JSON.stringify({
    name: 'docs', version: '1.2.3', packages: {
      '': {name: 'docs', version: '1.2.3'},
      'node_modules/other': {version: '1.2.3'},
    },
  }, null, 2) + '\n');
  return directory;
}

function runScript(directory, script, ...args) {
  return spawnSync(script.endsWith('.sh') ? 'bash' : process.execPath, [path.join(directory, script), ...args], {
    cwd: os.tmpdir(), encoding: 'utf8',
  });
}

test('LIBRARY_VERSION overrides the source version and rejects blank or invalid values', () => {
  const env = {...process.env};
  delete env.LIBRARY_VERSION;
  const run = () => spawnSync('bash', ['scripts/lib/project-version.sh'], {
    cwd: repositoryDirectory, env, encoding: 'utf8',
  });
  const baseline = run();
  assert.equal(baseline.status, 0, baseline.stderr);
  env.LIBRARY_VERSION = '9.8.7-dev-1';
  assert.equal(run().stdout.trim(), '9.8.7-dev-1');
  for (const version of ['', ' ', '1.0', '../1.2.3', '1.2.3\nINJECTED=value', '1.2.3\n']) {
    env.LIBRARY_VERSION = version;
    assert.equal(run().status, 1);
  }
});

test('version updates preserve dependency versions and historical changelog entries', (t) => {
  const directory = fixture(t);
  const read = (file) => fs.readFileSync(path.join(directory, file), 'utf8');
  const changelog = read('CHANGELOG.md');
  const updated = runScript(directory, 'scripts/update-version.sh', '2.3.4-RC1');
  assert.equal(updated.status, 0, updated.stderr);
  assert.match(read('gradle.properties'), /version=2\.3\.4-RC1/);
  assert.match(read('README.md'), /kotlin\("library\.api-watchdog"\) version "2\.3\.4-RC1"/);
  assert.match(read('README.md'), /kotlin\("library\.api-watchdog-report-aggregation"\) version "2\.3\.4-RC1"/);
  assert.match(read('README.md'), /kotlin\("jvm"\) version "1\.2\.3"/);
  const manifest = JSON.parse(read('docs/package.json'));
  const lock = JSON.parse(read('docs/package-lock.json'));
  assert.equal(manifest.version, '2.3.4-RC1');
  assert.equal(manifest.dependencies.other, '1.2.3');
  assert.equal(lock.version, '2.3.4-RC1');
  assert.equal(lock.packages[''].version, '2.3.4-RC1');
  assert.equal(lock.packages['node_modules/other'].version, '1.2.3');
  assert.equal(read('CHANGELOG.md'), changelog);
  assert.equal(runScript(directory, 'scripts/update-version.sh', '--check').status, 0);
  fs.writeFileSync(path.join(directory, 'docs/package.json'), read('docs/package.json').replace('2.3.4-RC1', '2.3.3'));
  const stale = runScript(directory, 'scripts/update-version.sh', '--check');
  assert.equal(stale.status, 1);
  assert.match(stale.stderr, /docs\/package.json/);
});

test('invalid version updates leave files unchanged', (t) => {
  const directory = fixture(t);
  const properties = fs.readFileSync(path.join(directory, 'gradle.properties'), 'utf8');
  assert.equal(runScript(directory, 'scripts/update-version.sh', 'invalid').status, 1);
  assert.equal(fs.readFileSync(path.join(directory, 'gradle.properties'), 'utf8'), properties);
});

test('changelog checks reject missing or stale pages and never rewrite them', (t) => {
  const directory = fixture(t);
  const script = 'docs/scripts/sync-changelog.mjs';
  const page = path.join(directory, 'docs/pages/changelog.md');
  assert.equal(runScript(directory, script, '--check').status, 1);
  assert.equal(runScript(directory, script).status, 0);
  assert.equal(runScript(directory, script, '--check').status, 0);
  fs.writeFileSync(page, 'stale\n');
  assert.equal(runScript(directory, script, '--check').status, 1);
  assert.equal(fs.readFileSync(page, 'utf8'), 'stale\n');
  assert.equal(runScript(directory, script).status, 0);
  fs.appendFileSync(path.join(directory, 'CHANGELOG.md'), '\n- Another change.\n');
  assert.equal(runScript(directory, script, '--check').status, 1);
  assert.equal(runScript(directory, script).status, 0);
  assert.equal(runScript(directory, script, '--check').status, 0);
});

test('publication preflight requires each selected credential without printing values', () => {
  for (const [target, required] of [
    ['maven-central', centralVariables],
    ['gradle-plugin-portal', portalVariables],
  ]) {
    const env = {...process.env};
    for (const name of [...centralVariables, ...portalVariables]) delete env[name];
    const run = () => spawnSync('bash', ['scripts/check-publication-env.sh', target], {
      cwd: repositoryDirectory, env, encoding: 'utf8',
    });
    assert.equal(run().status, 1);
    for (const name of required) env[name] = 'test-credential-never-log';
    const valid = run();
    assert.equal(valid.status, 0, valid.stderr);
    assert.doesNotMatch(valid.stdout + valid.stderr, /test-credential-never-log/);
    for (const name of required) {
      env[name] = ' \t ';
      const blank = run();
      assert.equal(blank.status, 1);
      assert.ok(blank.stderr.includes(name));
      assert.doesNotMatch(blank.stdout + blank.stderr, /test-credential-never-log/);
      env[name] = 'test-credential-never-log';
    }
  }
});
