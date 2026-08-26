import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import {remarkCodeSamples} from './remark-code-samples.mjs';

const docsDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../docs');

test('validates diagnostic annotations in every documentation code sample', () => {
  for (const file of markdownFiles(docsDirectory)) {
    const markdown = fs.readFileSync(file, 'utf8');
    const children = [...markdown.matchAll(/^```([^\r\n]*)\r?\n([\s\S]*?)^```[ \t]*$/gm)].map((match) => ({
      type: 'code',
      lang: match[1].trim().split(/\s+/, 1)[0] || null,
      meta: null,
      value: match[2].replace(/\r?\n$/, ''),
    }));
    remarkCodeSamples()({type: 'root', children}, {path: file});
  }
});

test('accepts several diagnostics on shared and distinct inline ranges', () => {
  assert.doesNotThrow(() =>
    transform(`// !diag[/Config/] DATA_CLASS_PUBLIC_API ["Config"]
// !diag[/Config/] UNDOCUMENTED_PUBLIC_API ["class","Config","$declarationDocumentation"]
public data class Config(
    // !diag[/tags/] UNDOCUMENTED_PUBLIC_API ["property","tags","$constructorPropertyDocumentation"]
    // !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["property","tags","MutableList","$returnTypeFix"]
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

test('leaves supplemental code visible', () => {
  const value = `@file:JvmName("Values")

/** Returns the current value. */
public fun value(): Int = 0`;
  assert.equal(transform(value).children[0].value, value);
});

function transform(value) {
  const tree = {
    type: 'root',
    children: [{type: 'code', lang: 'kotlin', meta: null, value}],
  };
  remarkCodeSamples()(tree, {path: 'sample.md'});
  return tree;
}

function* markdownFiles(directory) {
  for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
    const resolved = path.join(directory, entry.name);
    if (entry.isDirectory()) yield* markdownFiles(resolved);
    else if (entry.isFile() && entry.name.endsWith('.md')) yield resolved;
  }
}
