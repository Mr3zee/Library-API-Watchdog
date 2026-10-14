const PARAMETER_REFERENCE = /^\$([A-Za-z][A-Za-z0-9]*)(?:\(([^()]*)\))?$/;

export function resolveParameter(diagnostic, parameter, sharedParameterValues, ideaGenerateShortcut) {
  const reference = parameter.match(PARAMETER_REFERENCE);
  if (!reference) return parameter;

  if (reference[1] === 'ideaGenerateShortcut') return ideaGenerateShortcut;

  const values = diagnostic.parameterValues ?? {};
  const template = Object.hasOwn(values, reference[1])
    ? values[reference[1]]
    : Object.hasOwn(sharedParameterValues, reference[1])
      ? sharedParameterValues[reference[1]]
      : undefined;
  if (template === undefined) return parameter;

  const arguments_ = reference[2] === undefined ? [] : reference[2].split(',').map((it) => it.trim());
  return formatMessage(joinTextLines(template), arguments_);
}

export function formatMessage(template, parameters) {
  return template.replace(/\{(\d+)\}/g, (_, index) => parameters[Number(index)]);
}

/** The formatter uses newlines beside empty lines and lines with four leading spaces. */
export function joinTextLines(lines) {
  return lines.reduce((message, line, index) => {
    if (index === 0) return line;
    const previous = lines[index - 1];
    const hardBreak = line === '' || previous === '' || isCodeLine(line) || isCodeLine(previous);
    return `${message}${hardBreak ? '\n' : ' '}${line}`;
  }, '');
}

function isCodeLine(line) {
  return line.startsWith('    ');
}
