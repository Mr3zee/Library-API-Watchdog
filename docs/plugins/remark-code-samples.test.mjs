import assert from 'node:assert/strict';
import test from 'node:test';
import {remarkCodeSamples} from './remark-code-samples.mjs';

test('accepts several diagnostics on shared and distinct inline ranges', () => {
  assert.doesNotThrow(() =>
    transform(`// !diag[/Config/] DATA_CLASS_PUBLIC_API ["Config"]
// !diag[/Config/] UNDOCUMENTED_PUBLIC_API ["class","Config"]
public data class Config(
    // !diag[/tags/] UNDOCUMENTED_PUBLIC_API ["property","tags"]
    // !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["property","tags","MutableList"]
    public val tags: MutableList<String>
)`),
  );
});

test('rejects an unknown diagnostic', () => {
  assert.throws(
    () => transform('// !diag[/Thing/] NOT_A_DIAGNOSTIC\npublic class Thing'),
    /Unknown diagnostic NOT_A_DIAGNOSTIC/,
  );
});

test('rejects a diagnostic without an inline range', () => {
  assert.throws(
    () => transform('// DATA_CLASS_PUBLIC_API\npublic data class Thing(public val value: Int)'),
    /needs the compiler-reported inline range/,
  );
});

test('rejects an inline range that matches no code', () => {
  assert.throws(
    () => transform('// !diag[/Missing/] DATA_CLASS_PUBLIC_API ["Thing"]\npublic data class Thing(public val value: Int)'),
    /matches no code/,
  );
});

test('rejects missing diagnostic parameters', () => {
  assert.throws(
    () => transform('// !diag[/Thing/] DATA_CLASS_PUBLIC_API\npublic data class Thing(public val value: Int)'),
    /expects 1 parameters, got 0/,
  );
});

test('accepts named diagnostic parameter values with and without arguments', () => {
  assert.doesNotThrow(() =>
    transform(`// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$suspend"]
public suspend fun refresh() {}
// !diag[/onEach/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["onEach","$unitFunctionType(action)"]
public fun onEach(action: (Int) -> Unit) {}`),
  );
});

test('rejects an unknown named diagnostic parameter value', () => {
  assert.throws(
    () =>
      transform(
        '// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$unknown"]\n' +
          'public suspend fun refresh() {}',
      ),
    /Unknown parameter reference '\$unknown'/,
  );
});

test('rejects the wrong named diagnostic parameter argument count', () => {
  assert.throws(
    () =>
      transform(
        '// !diag[/onEach/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["onEach","$unitFunctionType"]\n' +
          'public fun onEach(action: (Int) -> Unit) {}',
      ),
    /expects 1 arguments, got 0/,
  );
});

test('rejects a malformed named diagnostic parameter value', () => {
  assert.throws(
    () =>
      transform(
        '// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$suspend("]\n' +
          'public suspend fun refresh() {}',
      ),
    /Malformed parameter reference '\$suspend\('/,
  );
});

test('collapses supplemental KDoc by default', () => {
  const tree = transform(`/**
 * Returns the current value.
 */
public fun value(): Int = 0`);

  assert.equal(
    tree.children[0].value,
    `// !collapse(1:3) collapsed kdoc
/**
 * Returns the current value.
 */
public fun value(): Int = 0`,
  );
});

test('collapses one-line supplemental KDoc', () => {
  const tree = transform('/** Returns the current value. */\npublic fun value(): Int = 0');
  assert.match(tree.children[0].value, /^\/\/ !collapse\(1:1\) collapsed kdoc$/m);
});

test('leaves KDoc expanded on the undocumented API page', () => {
  const value = '/** Returns the current value. */\npublic fun value(): Int = 0';
  assert.equal(transform(value, 'undocumented-public-api.md').children[0].value, value);
});

test('collapses supporting file setup outside its own page', () => {
  const value = '@file:JvmName("Values")\n\npublic fun value(): Int = 0';
  assert.match(transform(value).children[0].value, /^\/\/ !collapse\(1:1\) collapsed setup$/m);
  assert.equal(transform(value, 'top-level-api-without-jvm-name.md').children[0].value, value);
});

test('does not nest KDoc collapse inside an explicit collapsed range', () => {
  const value = `// !collapse(1:2) collapsed
// Supporting type
// !diag[/Support/] DATA_CLASS_PUBLIC_API ["Support"]
/** Supporting type. */
public data class Support(public val value: Int)`;
  assert.equal(transform(value).children[0].value, value);
});

function transform(value, path = 'sample.md') {
  const tree = {
    type: 'root',
    children: [{type: 'code', lang: 'kotlin', meta: null, value}],
  };
  remarkCodeSamples()(tree, {path});
  return tree;
}
