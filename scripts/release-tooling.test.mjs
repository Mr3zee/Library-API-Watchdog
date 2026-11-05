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
const spaceVariables = ['KOTLIN_LIBRARY_SPACE_USERNAME', 'KOTLIN_LIBRARY_SPACE_PASSWORD'];

function fixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'watchdog-release-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  for (const file of [
    'scripts/update-version.sh',
    'scripts/bump-after-release.sh',
    'scripts/changelog-section.sh',
    'scripts/insert-changelog-section.sh',
    'scripts/prepare-release.sh',
    'scripts/release-plan.sh',
    'scripts/release-notes.sh',
    'scripts/push-signed-commit.sh',
    'scripts/lib/project-version.sh',
    'docs/scripts/sync-changelog.mjs',
  ]) {
    const destination = path.join(directory, file);
    fs.mkdirSync(path.dirname(destination), {recursive: true});
    fs.copyFileSync(path.join(repositoryDirectory, file), destination);
  }
  fs.mkdirSync(path.join(directory, 'docs/pages'), {recursive: true});
  fs.writeFileSync(path.join(directory, 'library.properties'), 'group=example\nversion=1.2.3\n');
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

function gitRepository(directory) {
  const env = {
    ...process.env,
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_AUTHOR_NAME: 'Test', GIT_AUTHOR_EMAIL: 'test@example.com',
    GIT_COMMITTER_NAME: 'Test', GIT_COMMITTER_EMAIL: 'test@example.com',
  };
  const git = (...args) => {
    const result = spawnSync('git', ['-C', directory, ...args], {env, encoding: 'utf8'});
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  };
  git('init', '--quiet');
  git('commit', '--quiet', '--allow-empty', '--message', 'Initial');
  return git;
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
  assert.match(read('library.properties'), /version=2\.3\.4-RC1/);
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
  const properties = fs.readFileSync(path.join(directory, 'library.properties'), 'utf8');
  assert.equal(runScript(directory, 'scripts/update-version.sh', 'invalid').status, 1);
  assert.equal(fs.readFileSync(path.join(directory, 'library.properties'), 'utf8'), properties);
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

test('changelog sections print the entries of one release heading', (t) => {
  const directory = fixture(t);
  const section = (version) => runScript(directory, 'scripts/changelog-section.sh', version);
  const historical = section('1.2.3');
  assert.equal(historical.status, 0, historical.stderr);
  assert.equal(historical.stdout, '- Historical release.\n');
  fs.writeFileSync(path.join(directory, 'CHANGELOG.md'), [
    '# Changelog', '', '## Unreleased', '', '- Next change.', '',
    '## 2.0.0 - 2026-11-03', '', '### Added', '', '- Feature.', '', '',
    '## 1.2.30', '', '- Other release.', '',
    '## 1.2.3', '', '- Historical release.', '',
  ].join('\r\n'));
  assert.equal(section('2.0.0').stdout, '### Added\n\n- Feature.\n');
  assert.equal(section('1.2.3').stdout, '- Historical release.\n');
  for (const version of ['9.9.9', '1.2', 'Unreleased']) assert.equal(section(version).status, 1, version);
  assert.match(section('9.9.9').stderr, /## 9\.9\.9/);
});

test('changelog sections reject empty, duplicate, and malformed release headings', (t) => {
  const directory = fixture(t);
  const changelog = (...lines) => fs.writeFileSync(path.join(directory, 'CHANGELOG.md'), lines.join('\n') + '\n');
  const section = () => runScript(directory, 'scripts/changelog-section.sh', '2.0.0');
  changelog('# Changelog', '', '## 2.0.0', '', '  ', '## 1.2.3', '', '- Historical release.');
  assert.equal(section().status, 1);
  assert.match(section().stderr, /empty/);
  changelog('## 2.0.0', '- One.', '## 2.0.0', '- Two.');
  assert.equal(section().status, 1);
  for (const heading of ['## 2.0.0 - yesterday', '## v2.0.0', '### 2.0.0', '## 2.0.0-RC1']) {
    changelog(heading, '', '- Change.');
    assert.equal(section().status, 1, heading);
  }
});

test('version bumps after a release move to the next minor version only when the source version is earlier', (t) => {
  const directory = fixture(t);
  const read = (file) => fs.readFileSync(path.join(directory, file), 'utf8');
  const bump = (version) => runScript(directory, 'scripts/bump-after-release.sh', version);
  const setVersion = (version) => fs.writeFileSync(path.join(directory, 'library.properties'), `group=example\nversion=${version}\n`);
  const bumped = bump('1.2.3');
  assert.equal(bumped.status, 0, bumped.stderr);
  assert.match(read('library.properties'), /^version=1\.3\.0$/m);
  assert.match(read('README.md'), /kotlin\("library\.api-watchdog"\) version "1\.3\.0"/);
  assert.equal(JSON.parse(read('docs/package.json')).version, '1.3.0');
  for (const [source, released] of [['1.3.0', '1.2.3'], ['1.3.0', '1.1.0'], ['1.3.0-RC1', '1.2.0'], ['2.0.0', '1.9.9']]) {
    setVersion(source);
    const unchanged = bump(released);
    assert.equal(unchanged.status, 0, unchanged.stderr);
    assert.equal(read('library.properties'), `group=example\nversion=${source}\n`, `${source} after ${released}`);
  }
  setVersion('1.3.0');
  assert.equal(bump('1.10.4').status, 0);
  assert.match(read('library.properties'), /^version=1\.11\.0$/m);
  for (const version of ['1.2', '1.2.3-RC1', '', 'v1.2.3']) {
    assert.equal(bump(version).status, 1, version);
    assert.match(read('library.properties'), /^version=1\.11\.0$/m);
  }
  setVersion('1.2.3');
  assert.equal(runScript(directory, 'scripts/bump-after-release.sh', '1.2.3', 'patch').status, 0);
  assert.match(read('library.properties'), /^version=1\.2\.4$/m);
  assert.equal(runScript(directory, 'scripts/bump-after-release.sh', '1.2.3', 'patch').status, 0);
  assert.match(read('library.properties'), /^version=1\.2\.4$/m);
  assert.equal(runScript(directory, 'scripts/bump-after-release.sh', '1.2.3', 'major').status, 1);
});

test('release plans allow later versions from main and backports from the branch of their line', (t) => {
  const directory = fixture(t);
  const git = gitRepository(directory);
  const plan = (version, branch) => {
    const result = runScript(directory, 'scripts/release-plan.sh', version, branch);
    const values = Object.fromEntries(result.stdout.split('\n').filter(Boolean).map((line) => line.split('=')));
    return {...result, values};
  };
  const expect = (tagged, fromMain, latest, docsVersion, nextPart) => ({
    tagged, from_main: fromMain, latest, docs_version: docsVersion, next_part: nextPart,
  });

  assert.deepEqual(plan('0.1.0', 'main').values, expect('false', 'true', 'true', '0.1.0', 'minor'));
  for (const tag of ['v0.1.0', 'v0.1.1', 'v0.2.0', 'v0.2.0-RC1', 'v0.10', 'other']) git('tag', tag);
  assert.deepEqual(plan('0.3.0', 'main').values, expect('false', 'true', 'true', '0.3.0', 'minor'));
  assert.deepEqual(plan('0.2.1', 'main').values, expect('false', 'true', 'true', '0.2.1', 'minor'));
  assert.deepEqual(plan('0.1.2', 'release/0.1.x').values, expect('false', 'false', 'false', '0.2.0', 'patch'));
  assert.deepEqual(plan('0.2.1', 'release/0.2.x').values, expect('false', 'false', 'true', '0.2.1', 'patch'));

  // A run after the tag finds it and checks nothing.
  git('tag', 'v0.1.2');
  assert.deepEqual(plan('0.1.2', 'release/0.1.x').values, expect('true', 'false', 'false', '0.2.0', 'patch'));
  assert.equal(plan('0.2.0', 'main').values.tagged, 'true');
  assert.equal(plan('0.2.0', 'main').values.latest, 'true');

  git('tag', 'v0.1.5');
  for (const [version, branch, message] of [
    ['0.1.9', 'main', /isn't later than v0\.2\.0\. Release a backport from release\/0\.1\.x/],
    ['0.1.3', 'release/0.2.x', /from main or from release\/0\.1\.x, not from release\/0\.2\.x/],
    ['0.3.0', 'feature', /not from feature/],
    ['0.1.4', 'release/0.1.x', /isn't later than v0\.1\.5/],
    ['0.3.1', 'release/0.3.x', /The 0\.3 line has no release tag/],
    ['0.3', 'main', /Usage/],
    ['v0.3.0', 'main', /Usage/],
  ]) {
    const result = plan(version, branch);
    assert.equal(result.status, 1, `${version} from ${branch}`);
    assert.match(result.stderr, message);
  }
});

test('release preparation adds the release section from the notes and the unreleased text, and sets the next development version', (t) => {
  const directory = fixture(t);
  const changelogFile = path.join(directory, 'CHANGELOG.md');
  const notesFile = path.join(directory, 'notes.md');
  const read = (file) => fs.readFileSync(path.join(directory, file), 'utf8');
  const prepare = (args, env = {}) => spawnSync('bash', [path.join(directory, 'scripts/prepare-release.sh'), ...args], {
    cwd: os.tmpdir(), encoding: 'utf8', env: {...process.env, RELEASE_DATE: '2026-11-04', ...env},
  });
  const notes = '### What\'s Changed\n\n* Change by @someone in https://example.com/pull/1\n\n**Full Changelog**: compare\n';
  fs.writeFileSync(notesFile, `\n${notes}\n\n`);

  // The text of the unreleased section goes above the notes, and the section goes away.
  fs.writeFileSync(changelogFile, '# Changelog\n\n## Unreleased\n\n### Added\n\n- Feature.\n\n## 1.2.3\n\n- Historical release.\n');
  const prepared = prepare(['1.3.0', 'minor', notesFile]);
  assert.equal(prepared.status, 0, prepared.stderr);
  assert.equal(read('CHANGELOG.md'),
    `# Changelog\n\n## 1.3.0 - 2026-11-04\n\n### Added\n\n- Feature.\n\n${notes}\n## 1.2.3\n\n- Historical release.\n`);
  assert.match(read('library.properties'), /^version=1\.4\.0$/m);
  assert.match(read('docs/pages/changelog.md'), /^## 1\.3\.0 - 2026-11-04$/m);
  assert.equal(runScript(directory, 'scripts/changelog-section.sh', '1.3.0').stdout, `### Added\n\n- Feature.\n\n${notes}`);

  // Without an unreleased section, the section holds the notes only.
  const notesOnly = prepare(['1.4.0', 'minor', notesFile]);
  assert.equal(notesOnly.status, 0, notesOnly.stderr);
  assert.match(read('CHANGELOG.md'), /^# Changelog\n\n## 1\.4\.0 - 2026-11-04\n\n### What's Changed\n/);
  assert.equal(runScript(directory, 'scripts/changelog-section.sh', '1.4.0').stdout, notes);
  assert.match(read('library.properties'), /^version=1\.5\.0$/m);

  // A backport goes between the releases of other lines and keeps the later development version.
  const patch = prepare(['1.3.1', 'patch', notesFile]);
  assert.equal(patch.status, 0, patch.stderr);
  assert.match(read('CHANGELOG.md'), /## 1\.4\.0 - 2026-11-04\n[^]*\n## 1\.3\.1 - 2026-11-04\n[^]*\n## 1\.3\.0 - 2026-11-04\n/);
  assert.match(read('library.properties'), /^version=1\.5\.0$/m);
  assert.match(prepare(['1.3.1', 'patch', notesFile]).stderr, /has a section of 1\.3\.1/);

  // The first release of a changelog with the unreleased section only.
  fs.writeFileSync(changelogFile, '# Changelog\n\n## Unreleased\n\n- First.\n');
  const first = prepare(['0.1.0', 'minor', notesFile]);
  assert.equal(first.status, 0, first.stderr);
  assert.equal(read('CHANGELOG.md'), `# Changelog\n\n## 0.1.0 - 2026-11-04\n\n- First.\n\n${notes}`);

  const emptyNotes = path.join(directory, 'empty.md');
  fs.writeFileSync(emptyNotes, '\n');
  fs.writeFileSync(changelogFile, '# Changelog\n\n## Unreleased\n\n## 1.2.3\n\n- Historical release.\n');
  const empty = prepare(['1.5.0', 'minor', emptyNotes]);
  assert.equal(empty.status, 1);
  assert.match(empty.stderr, /no text/);

  fs.writeFileSync(changelogFile, '# Changelog\n\n## Unreleased\n\n- A.\n\n## Unreleased\n\n- B.\n');
  assert.match(prepare(['1.5.0', 'minor', notesFile]).stderr, /at most one "## Unreleased" section, found 2/);

  const unchanged = read('CHANGELOG.md');
  for (const [args, env] of [
    [['1.5.0', 'minor'], {}],
    [['1.5.0', 'minor', path.join(directory, 'missing.md')], {}],
    [['1.5.0', 'major', notesFile], {}],
    [['1.5', 'minor', notesFile], {}],
    [['1.5.0', 'minor', notesFile], {RELEASE_DATE: 'today'}],
    [['1.5.0', 'minor', emptyNotes], {}],
  ]) {
    assert.equal(prepare(args, env).status, 1, args.join(' '));
    assert.equal(read('CHANGELOG.md'), unchanged);
  }
});

test('released sections go into the changelog in version order once', (t) => {
  const directory = fixture(t);
  const changelogFile = path.join(directory, 'CHANGELOG.md');
  const released = path.join(directory, 'released.md');
  const insert = (version) => runScript(directory, 'scripts/insert-changelog-section.sh', version, released);
  fs.writeFileSync(released,
    '# Changelog\n\n## Unreleased\n\n## 1.2.4 - 2026-11-04\n\n- Backported fix.\n\n\n## 1.2.3\n\n- Historical release.\n');

  fs.writeFileSync(changelogFile,
    '# Changelog\n\n## Unreleased\n\n- Next.\n\n## 1.3.0 - 2026-11-01\n\n- Feature.\n\n## 1.2.3\n\n- Historical release.\n');
  const inserted = insert('1.2.4');
  assert.equal(inserted.status, 0, inserted.stderr);
  const expected = '# Changelog\n\n## Unreleased\n\n- Next.\n\n## 1.3.0 - 2026-11-01\n\n- Feature.\n\n' +
    '## 1.2.4 - 2026-11-04\n\n- Backported fix.\n\n## 1.2.3\n\n- Historical release.\n';
  assert.equal(fs.readFileSync(changelogFile, 'utf8'), expected);
  assert.equal(insert('1.2.4').status, 0);
  assert.equal(fs.readFileSync(changelogFile, 'utf8'), expected);

  fs.writeFileSync(changelogFile, '# Changelog\n\n## 1.3.0\n\n- Feature.\n');
  assert.equal(insert('1.2.4').status, 0);
  assert.equal(fs.readFileSync(changelogFile, 'utf8'),
    '# Changelog\n\n## 1.3.0\n\n- Feature.\n\n## 1.2.4 - 2026-11-04\n\n- Backported fix.\n');

  const missing = insert('1.2.5');
  assert.equal(missing.status, 1);
  assert.match(missing.stderr, /no section of 1\.2\.5/);
  assert.equal(runScript(directory, 'scripts/insert-changelog-section.sh', '1.2', released).status, 1);
});

test('release notes are the GitHub changes since the highest earlier stable tag, one heading level lower', (t) => {
  const directory = fixture(t);
  const gitEnv = {
    ...process.env,
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_AUTHOR_NAME: 'Test', GIT_AUTHOR_EMAIL: 'test@example.com',
    GIT_COMMITTER_NAME: 'Test', GIT_COMMITTER_EMAIL: 'test@example.com',
  };
  const git = (...args) => {
    const result = spawnSync('git', ['-C', directory, ...args], {env: gitEnv, encoding: 'utf8'});
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  };
  git('init', '--quiet');
  git('commit', '--quiet', '--allow-empty', '--message', 'Initial');
  const head = git('rev-parse', 'HEAD');

  // The fake gh writes its arguments to a file and prints the body that `--jq .body` selects, with CRLF line ends.
  const ghArguments = path.join(directory, 'gh-arguments');
  fs.mkdirSync(path.join(directory, 'bin'));
  fs.writeFileSync(path.join(directory, 'bin/gh'), [
    '#!/usr/bin/env bash',
    `printf '%s\\n' "$@" > '${ghArguments}'`,
    '[[ -z "${GH_FAIL:-}" ]] || exit 1',
    `printf "## What's Changed\\r\\n* Change by @someone in https://example.com/pull/1\\r\\n\\r\\n**Full Changelog**: compare\\r\\n\\r\\n"`,
    '',
  ].join('\n'), {mode: 0o755});
  const notes = (version, env = {}) => {
    fs.rmSync(ghArguments, {force: true});
    return spawnSync('bash', [path.join(directory, 'scripts/release-notes.sh'), version], {
      cwd: os.tmpdir(), encoding: 'utf8',
      env: {...gitEnv, PATH: `${path.join(directory, 'bin')}:${process.env.PATH}`, GITHUB_REPOSITORY: 'example/watchdog', ...env},
    });
  };
  const fields = () => fs.readFileSync(ghArguments, 'utf8').split('\n').filter((line) => line.includes('='));

  const first = notes('1.3.0');
  assert.equal(first.status, 0, first.stderr);
  assert.equal(first.stdout, "### What's Changed\n* Change by @someone in https://example.com/pull/1\n\n**Full Changelog**: compare\n");
  assert.match(fs.readFileSync(ghArguments, 'utf8'), /^repos\/example\/watchdog\/releases\/generate-notes$/m);
  assert.deepEqual(fields(), ['tag_name=v1.3.0', `target_commitish=${head}`]);

  for (const tag of ['v1.2.3', 'v1.2.10', 'v1.3.0-RC1', 'v1.10', 'release-2.0.0']) git('tag', tag);
  const next = notes('1.3.0');
  assert.equal(next.status, 0, next.stderr);
  assert.deepEqual(fields(), ['tag_name=v1.3.0', `target_commitish=${head}`, 'previous_tag_name=v1.2.10']);

  git('tag', 'v1.3.0');
  const rerun = notes('1.3.0');
  assert.equal(rerun.status, 0, rerun.stderr);
  assert.deepEqual(fields(), ['tag_name=v1.3.0', `target_commitish=${head}`, 'previous_tag_name=v1.2.10']);

  const backport = notes('1.2.4');
  assert.equal(backport.status, 0, backport.stderr);
  assert.deepEqual(fields(), ['tag_name=v1.2.4', `target_commitish=${head}`, 'previous_tag_name=v1.2.3']);

  assert.notEqual(notes('1.3.0', {GH_FAIL: '1'}).status, 0);
  for (const version of ['1.3', '1.3.0-RC1', 'v1.3.0']) {
    const rejected = notes(version);
    assert.equal(rejected.status, 1, version);
    assert.equal(fs.existsSync(ghArguments), false, version);
  }
});

test('publication preflight requires each selected credential without printing values', () => {
  for (const [target, required] of [
    ['maven-central', centralVariables],
    ['gradle-plugin-portal', portalVariables],
    ['space', spaceVariables],
  ]) {
    const env = {...process.env};
    for (const name of [...centralVariables, ...portalVariables, ...spaceVariables]) delete env[name];
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

test('signed commits send the changes of the tracked files to the GitHub API, on top of HEAD', (t) => {
  const directory = fixture(t);
  const git = gitRepository(directory);
  for (const file of ['a.txt', 'b.txt', 'with space.txt']) fs.writeFileSync(path.join(directory, file), `${file}\n`);
  git('add', 'a.txt', 'b.txt', 'with space.txt');
  git('commit', '--quiet', '--message', 'Files');
  const head = git('rev-parse', 'HEAD');

  // The fake gh writes each call to a file, copies the GraphQL request, and fails the branch lookup without BRANCH_EXISTS.
  const calls = path.join(directory, 'gh-calls');
  const request = path.join(directory, 'gh-request.json');
  fs.mkdirSync(path.join(directory, 'bin'));
  fs.writeFileSync(path.join(directory, 'bin/gh'), [
    '#!/usr/bin/env bash',
    `printf '%s\\n' "$*" >> '${calls}'`,
    'if [[ "$2" == graphql ]]; then',
    `  cp "$4" '${request}'`,
    "  echo 'commit-hash'",
    'elif [[ "$2" == */git/ref/heads/* && -z "${BRANCH_EXISTS:-}" ]]; then',
    '  exit 1',
    'fi',
    '',
  ].join('\n'), {mode: 0o755});
  const push = (args = ['release/1.0', 'Release 1.0'], env = {}) => {
    fs.rmSync(calls, {force: true});
    fs.rmSync(request, {force: true});
    return spawnSync('bash', [path.join(directory, 'scripts/push-signed-commit.sh'), ...args], {
      cwd: os.tmpdir(), encoding: 'utf8',
      env: {...process.env, PATH: `${path.join(directory, 'bin')}:${process.env.PATH}`, GITHUB_REPOSITORY: 'example/watchdog', ...env},
    });
  };
  const callLines = () => fs.readFileSync(calls, 'utf8').trim().split('\n');

  fs.writeFileSync(path.join(directory, 'a.txt'), 'changed ä\n');
  fs.rmSync(path.join(directory, 'b.txt'));
  fs.writeFileSync(path.join(directory, 'with space.txt'), 'changed\n');
  fs.writeFileSync(path.join(directory, 'untracked.txt'), 'untracked\n');
  const created = push();
  assert.equal(created.status, 0, created.stderr);
  assert.equal(created.stdout, 'commit-hash\n');
  const [lookup, create, mutation] = callLines();
  assert.equal(lookup, 'api repos/example/watchdog/git/ref/heads/release/1.0 --silent');
  assert.equal(create, `api --method POST repos/example/watchdog/git/refs -f ref=refs/heads/release/1.0 -f sha=${head} --silent`);
  assert.match(mutation, /^api graphql --input \S+ --jq \.data\.createCommitOnBranch\.commit\.oid$/);
  const body = JSON.parse(fs.readFileSync(request, 'utf8'));
  assert.match(body.query, /createCommitOnBranch\(input: \$input\)/);
  assert.deepEqual(body.variables.input, {
    branch: {repositoryNameWithOwner: 'example/watchdog', branchName: 'release/1.0'},
    message: {headline: 'Release 1.0'},
    expectedHeadOid: head,
    fileChanges: {
      additions: [
        {path: 'a.txt', contents: Buffer.from('changed ä\n').toString('base64')},
        {path: 'with space.txt', contents: Buffer.from('changed\n').toString('base64')},
      ],
      deletions: [{path: 'b.txt'}],
    },
  });

  const moved = push(undefined, {BRANCH_EXISTS: '1'});
  assert.equal(moved.status, 0, moved.stderr);
  assert.equal(callLines()[1], `api --method PATCH repos/example/watchdog/git/refs/heads/release/1.0 -f sha=${head} -F force=true --silent`);

  fs.chmodSync(path.join(directory, 'a.txt'), 0o755);
  const executable = push();
  assert.equal(executable.status, 1);
  assert.match(executable.stderr, /^a\.txt has the file mode 100755\./);
  assert.equal(fs.existsSync(calls), false);

  git('checkout', '--', 'a.txt', 'b.txt', 'with space.txt');
  const unchanged = push();
  assert.equal(unchanged.status, 1);
  assert.match(unchanged.stderr, /no changes against HEAD/);
  assert.equal(fs.existsSync(calls), false);

  for (const args of [[], ['release/1.0'], ['', 'Release 1.0'], ['release/1.0', '']]) {
    assert.equal(push(args).status, 1, JSON.stringify(args));
    assert.equal(fs.existsSync(calls), false, JSON.stringify(args));
  }
});
