// Prepares the fenced code blocks for Code Hike, which replaces them with the Code component in
// the plugin that runs right after this one:
//
//   - Fences with no language become `text`, so the highlighter is not asked for an empty one.
//   - The diagnostic comments the Kotlin samples are annotated with,
//
//         // DATA_CLASS_PUBLIC_API, UNDOCUMENTED_PUBLIC_API
//         public data class Point(val x: Int, val y: Int)
//
//     become a `!diag` annotation on the declaration line, so Code Hike drops the comment from
//     the rendered sample and the Code component renders a marker for each diagnostic instead.
//     Only comments made up entirely of known diagnostic names are touched; every other comment
//     is part of the sample and stays as written.
import {visit} from 'unist-util-visit';
import {diagnostics} from './diagnostics.mjs';

const DIAGNOSTIC_COMMENT = /^(\s*)\/\/ ([A-Z][A-Z0-9_]*(?:\s*,\s*[A-Z][A-Z0-9_]*)*)\s*$/;

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
      node.value = node.value
        .split('\n')
        .map((line) => annotate(line, file))
        .join('\n');
    });
  };
}

function annotate(line, file) {
  const match = line.match(DIAGNOSTIC_COMMENT);
  if (!match) return line;
  const [, indentation, list] = match;
  const names = list.split(',').map((name) => name.trim());
  // A name-shaped comment that is not a diagnostic is either a typo or a diagnostic missing from
  // diagnostics.json; both are worth failing the build over.
  const unknown = names.filter((name) => !diagnostics.has(name));
  if (unknown.length > 0) {
    throw new Error(`Unknown diagnostic ${unknown.join(', ')} in ${file.path}.`);
  }
  return `${indentation}// !${DIAGNOSTIC_ANNOTATION} ${names.join(',')}`;
}
