import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import {formatMessage, joinTextLines, resolveParameter} from '../src/utils/diagnostic-messages.mjs';

const registry = JSON.parse(fs.readFileSync(new URL('../../diagnostics.json', import.meta.url), 'utf8'));
const overloadDiagnostic = registry.diagnostics.find(
  ({name}) => name === 'INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS',
);

test('renders a constructor overload diagnostic with the shared constructor parameter', () => {
  const parameters = [
    '$twoParameterOverload(Rect,width: Int,height: Int)',
    '$otherThreeParameterOverload(Rect,height: Int,width: Int,scale: Double)',
    '$twoParameterPositions(width,1st,2nd,height,2nd,1st)',
    '$constructor',
  ].map((parameter) => resolveParameter(overloadDiagnostic, parameter, registry.sharedParameterValues));
  const message = formatMessage(joinTextLines(overloadDiagnostic.message), parameters);

  assert.match(message, /This overload:  Rect\(width: Int, height: Int\)/);
  assert.match(message, /Other overload: Rect\(height: Int, width: Int, scale: Double\)/);
  assert.match(message, /Parameter \| This overload \| Other overload\n    width \| 1st \| 2nd\n    height \| 2nd \| 1st/);
  assert.match(message, /apply `@IntentionallyInconsistentParameterOrder` to the constructor\.$/);
});

test('a diagnostic can override a shared parameter named constructor', () => {
  const diagnostic = {parameterValues: {constructor: ['custom constructor']}};
  assert.equal(resolveParameter(diagnostic, '$constructor', registry.sharedParameterValues), 'custom constructor');
});

test('leaves unregistered prototype names unresolved', () => {
  for (const parameter of ['$constructor', '$toString', '$hasOwnProperty', '$__proto__']) {
    assert.equal(resolveParameter({parameterValues: {}}, parameter, {}), parameter);
  }
});

test('preserves the IDE shortcut and literal diagnostic parameters', () => {
  assert.equal(resolveParameter({}, '$ideaGenerateShortcut', {}, 'Alt+Insert'), 'Alt+Insert');
  assert.equal(resolveParameter({}, 'Rect', {}), 'Rect');
});

test('joins wrapped prose and preserves paragraph and code line breaks', () => {
  assert.equal(
    joinTextLines(['Wrapped', 'prose.', '', '    First code line', '    Second code line', '', 'Final paragraph.']),
    'Wrapped prose.\n\n    First code line\n    Second code line\n\nFinal paragraph.',
  );
});
