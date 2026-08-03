// Prepares the fenced code blocks for Code Hike, which replaces them with the Code component in
// the plugin that runs right after this one:
//
//   - Fences with no language become `text`, so the highlighter is not asked for an empty one.
//   - The inline diagnostic annotations in Kotlin samples,
//
//         // !diag[/Point/] DATA_CLASS_PUBLIC_API ["Point"]
//         public data class Point(val x: Int, val y: Int)
//
//     are validated here, then Code Hike drops the comment and gives the selected range to the
//     tooltip handler. A name-shaped legacy comment is rejected so diagnostics cannot silently
//     fall back to highlighting a whole line.
import {visit} from 'unist-util-visit';
import {diagnostics} from './diagnostics.mjs';

const DIAGNOSTIC_ANNOTATION_COMMENT =
  /^\s*\/\/ !diag\[\/(.+)\/([a-z]*)\]\s+(.+?)\s*$/;
const LEGACY_DIAGNOSTIC_COMMENT =
  /^\s*\/\/ ([A-Z][A-Z0-9_]*(?:\s*,\s*[A-Z][A-Z0-9_]*)*)\s*$/;
const PARAMETER_REFERENCE = /^\$([A-Za-z][A-Za-z0-9]*)(?:\(([^()]*)\))?$/;

/** The annotation name the `diag` handler in src/components/Code.tsx listens for. */
export const DIAGNOSTIC_ANNOTATION = 'diag';

export function remarkCodeSamples() {
  return (tree, file) => {
    visit(tree, 'code', (node) => {
      if (!node.lang) {
        node.lang = 'text';
        return;
      }
      if (node.lang !== 'kotlin') return;
      validateDiagnosticAnnotations(node.value, file);
    });
  };
}

function validateDiagnosticAnnotations(value, file) {
  const lines = value.split('\n');
  const renderedLines = lines.filter((line) => !line.match(DIAGNOSTIC_ANNOTATION_COMMENT));
  let renderedLineIndex = 0;

  for (const line of lines) {
    const annotation = line.match(DIAGNOSTIC_ANNOTATION_COMMENT);
    if (annotation) {
      const [, pattern, flags, query] = annotation;
      validateReport(query, file);
      validateRange(pattern, flags, renderedLines, renderedLineIndex, file);
      continue;
    }

    const legacy = line.match(LEGACY_DIAGNOSTIC_COMMENT);
    if (legacy) {
      validateNames(legacy[1], file);
      throw new Error(
        `Diagnostic annotation in ${file.path} needs the compiler-reported inline range: ` +
          `use // !${DIAGNOSTIC_ANNOTATION}[/range/] NAME.`,
      );
    }

    renderedLineIndex += 1;
  }
}

function validateNames(list, file) {
  const names = list.split(',').map((name) => name.trim());
  const unknown = names.filter((name) => !diagnostics.has(name));
  if (unknown.length > 0) throw new Error(`Unknown diagnostic ${unknown.join(', ')} in ${file.path}.`);
}

function validateReport(query, file) {
  const match = query.match(/^([A-Z][A-Z0-9_]*)(?:\s+(\[.*\]))?$/);
  if (!match) throw new Error(`Malformed diagnostic report '${query}' in ${file.path}.`);

  const [, name, encodedParameters] = match;
  validateNames(name, file);

  let parameters = [];
  try {
    parameters = encodedParameters ? JSON.parse(encodedParameters) : [];
  } catch (error) {
    throw new Error(`Invalid diagnostic parameters for ${name} in ${file.path}.`, {cause: error});
  }
  if (!Array.isArray(parameters) || parameters.some((parameter) => typeof parameter !== 'string')) {
    throw new Error(`Diagnostic parameters for ${name} in ${file.path} must be an array of strings.`);
  }

  const diagnostic = diagnostics.get(name);
  const indexes = [...diagnostic.message.matchAll(/\{(\d+)\}/g)].map((placeholder) => Number(placeholder[1]));
  const expectedCount = indexes.length === 0 ? 0 : Math.max(...indexes) + 1;
  if (parameters.length !== expectedCount) {
    throw new Error(
      `Diagnostic ${name} in ${file.path} expects ${expectedCount} parameters, got ${parameters.length}.`,
    );
  }
  for (const parameter of parameters) validateParameterReference(parameter, diagnostic, file);
}

function validateParameterReference(parameter, diagnostic, file) {
  if (!parameter.startsWith('$')) return;

  const reference = parameter.match(PARAMETER_REFERENCE);
  if (!reference) {
    throw new Error(`Malformed parameter reference '${parameter}' for ${diagnostic.name} in ${file.path}.`);
  }

  const [, name, encodedArguments] = reference;
  const template = diagnostic.parameterValues?.[name];
  if (template === undefined) {
    throw new Error(`Unknown parameter reference '$${name}' for ${diagnostic.name} in ${file.path}.`);
  }

  const arguments_ = encodedArguments === undefined ? [] : encodedArguments.split(',').map((it) => it.trim());
  if (arguments_.some((argument) => argument.length === 0)) {
    throw new Error(`Malformed parameter reference '${parameter}' for ${diagnostic.name} in ${file.path}.`);
  }

  const indexes = [...template.matchAll(/\{(\d+)\}/g)].map((placeholder) => Number(placeholder[1]));
  const expectedCount = indexes.length === 0 ? 0 : Math.max(...indexes) + 1;
  if (arguments_.length !== expectedCount) {
    throw new Error(
      `Parameter reference '$${name}' for ${diagnostic.name} in ${file.path} expects ` +
        `${expectedCount} arguments, got ${arguments_.length}.`,
    );
  }
}

function validateRange(pattern, flags, renderedLines, renderedLineIndex, file) {
  let expression;
  try {
    expression = new RegExp(pattern, flags.replace('g', ''));
  } catch (error) {
    throw new Error(`Invalid diagnostic range /${pattern}/${flags} in ${file.path}.`, {cause: error});
  }

  const candidates = flags.includes('m')
    ? renderedLines.slice(renderedLineIndex)
    : renderedLines.slice(renderedLineIndex, renderedLineIndex + 1);
  if (!candidates.some((line) => expression.test(line))) {
    throw new Error(`Diagnostic range /${pattern}/${flags} matches no code in ${file.path}.`);
  }
}
