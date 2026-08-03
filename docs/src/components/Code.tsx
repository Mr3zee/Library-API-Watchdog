import React, {useCallback, useEffect, useRef, useState, type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import * as Tooltip from '@radix-ui/react-tooltip';
import clsx from 'clsx';
import {
  InnerPre,
  Pre,
  type AnnotationHandler,
  type CodeAnnotation,
  type CustomPreProps,
  type HighlightedCode,
} from 'codehike/code';
import Markdown from 'react-markdown';
import IconCopy from '@theme/Icon/Copy';
import IconSuccess from '@theme/Icon/Success';
import registry from '../../../diagnostics.json';
import {KeyboardShortcut, useIdeaGenerateShortcut} from './KeyboardShortcut';
import styles from './Code.module.css';

/**
 * Renders every fenced code block on the site. Code Hike replaces the blocks with this component
 * from the remark plugins configured in docusaurus.config.ts.
 */
export default function Code({codeblock}: {codeblock: HighlightedCode}): ReactNode {
  const pre = (
    <Pre
      className={clsx(styles.pre, codeblock.meta && styles.namedPre)}
      code={mergeDiagnosticsOnTheSameRange(codeblock)}
      handlers={[createCopyButton(codeblock.code), diagnostics]}
    />
  );

  if (!codeblock.meta) {
    return (
      <Tooltip.Provider delayDuration={250} skipDelayDuration={100}>
        {pre}
      </Tooltip.Provider>
    );
  }

  return (
    <div className={styles.namedCodeBlock}>
      <div className={styles.filename}>{codeblock.meta}</div>
      <Tooltip.Provider delayDuration={250} skipDelayDuration={100}>
        {pre}
      </Tooltip.Provider>
    </div>
  );
}

const byName = new Map(registry.diagnostics.map((diagnostic) => [diagnostic.name, diagnostic]));

function createCopyButton(copyText: string): AnnotationHandler {
  return {
    name: 'copy-button',
    Pre: (props) => <CopyablePre {...props} copyText={copyText} />,
  };
}

function CopyablePre({copyText, ...props}: CustomPreProps & {copyText: string}): ReactNode {
  const timeoutRef = useRef<number | undefined>(undefined);
  const [isCopied, setIsCopied] = useState(false);

  useEffect(() => () => window.clearTimeout(timeoutRef.current), []);

  const copyCode = useCallback(async () => {
    if (!copyText || !(await copyToClipboard(copyText))) return;

    window.clearTimeout(timeoutRef.current);
    setIsCopied(true);
    timeoutRef.current = window.setTimeout(() => setIsCopied(false), 1000);
  }, [copyText]);

  return (
    <div className={styles.codeBlock}>
      <InnerPre merge={props} />
      <button
        aria-label={isCopied ? 'Copied' : 'Copy code to clipboard'}
        className={clsx('clean-btn', styles.copyButton, isCopied && styles.copyButtonCopied)}
        onClick={copyCode}
        title="Copy"
        type="button"
      >
        <span className={styles.copyButtonIcons} aria-hidden="true">
          <IconCopy className={styles.copyButtonIcon} />
          <IconSuccess className={styles.copyButtonSuccessIcon} />
        </span>
      </button>
    </div>
  );
}

async function copyToClipboard(text: string): Promise<boolean> {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text);
    return true;
  }

  const {default: copy} = await import('copy-text-to-clipboard');
  return copy(text);
}

/**
 * Underlines the exact source range reported by the compiler and shows its diagnostics in an
 * IntelliJ-style tooltip. The remark plugin validates the `// !diag[/range/] NAME` annotations;
 * see plugins/remark-code-samples.mjs.
 */
const diagnostics: AnnotationHandler = {
  name: 'diag',
  Inline: ({annotation, children}) => {
    const reports = reportsFrom(annotation);
    return (
      <Tooltip.Root>
        <Tooltip.Trigger asChild>
          <span className={styles.diagnosticTrigger} tabIndex={0}>
            {children}
          </span>
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            className={styles.tooltip}
            side="bottom"
            align="start"
            sideOffset={6}
            collisionPadding={8}
          >
            {reports.length > 1 && <div className={styles.problemCount}>{reports.length} problems found</div>}
            <div className={styles.diagnosticList}>
              {reports.map((report) => (
                <Diagnostic key={reportKey(report)} report={report} />
              ))}
            </div>
            <Tooltip.Arrow className={styles.tooltipArrow} width={10} height={5} />
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    );
  },
};

type DiagnosticReport = {
  name: string;
  parameters: string[];
};

type DiagnosticDefinition = (typeof registry.diagnostics)[number];

const PARAMETER_REFERENCE = /^\$([A-Za-z][A-Za-z0-9]*)(?:\(([^()]*)\))?$/;

function Diagnostic({report}: {report: DiagnosticReport}): ReactNode {
  const diagnostic = byName.get(report.name);
  const ideaGenerateShortcut = useIdeaGenerateShortcut();
  // The remark plugin only produces names it found in the registry, so the fallback is only
  // reachable if the two ever get out of sync.
  if (!diagnostic) return <span className={styles.diagnosticName}>{report.name}</span>;

  const parameters = report.parameters.map((parameter) =>
    resolveParameter(diagnostic, parameter, ideaGenerateShortcut.label),
  );
  const message = formatMessage(diagnostic.message, parameters);
  const trailer = 'messageTrailer' in diagnostic ? diagnostic.messageTrailer : undefined;
  return (
    <div className={styles.diagnostic}>
      <span className={styles.errorIcon} aria-hidden="true">
        !
      </span>
      <div className={styles.diagnosticText}>
        <div className={styles.diagnosticTitle}>{diagnostic.title}</div>
        <div className={styles.diagnosticMessage}>
          <Markdown
            components={{
              code: ({children, ...props}) =>
                String(children) === ideaGenerateShortcut.label ? (
                  <KeyboardShortcut value={ideaGenerateShortcut} />
                ) : (
                  <code {...props}>{children}</code>
                ),
            }}
          >
            {trailer ? `${message} ${trailer}` : message}
          </Markdown>
        </div>
        <Link
          aria-label={`See more about ${diagnostic.title}`}
          className={styles.diagnosticLink}
          to={`/${diagnostic.docs}`}
        >
          See more
        </Link>
      </div>
    </div>
  );
}

function resolveParameter(
  diagnostic: DiagnosticDefinition,
  parameter: string,
  ideaGenerateShortcut: string,
): string {
  const reference = parameter.match(PARAMETER_REFERENCE);
  if (!reference) return parameter;

  if (reference[1] === 'ideaGenerateShortcut') return ideaGenerateShortcut;

  const values =
    'parameterValues' in diagnostic
      ? (diagnostic.parameterValues as Record<string, string>)
      : undefined;
  const template = values?.[reference[1]];
  if (!template) return parameter;

  const arguments_ = reference[2] === undefined ? [] : reference[2].split(',').map((it) => it.trim());
  return formatMessage(template, arguments_);
}

function formatMessage(template: string, parameters: string[]): string {
  return template.replace(/\{(\d+)\}/g, (_, index: string) => parameters[Number(index)]);
}

function parseReport(query: string): DiagnosticReport {
  const match = query.match(/^([A-Z][A-Z0-9_]*)(?:\s+(\[.*\]))?$/);
  if (!match) return {name: query, parameters: []};
  return {
    name: match[1],
    parameters: match[2] ? JSON.parse(match[2]) : [],
  };
}

function reportsFrom(annotation: CodeAnnotation): DiagnosticReport[] {
  const reports = annotation.data?.diagnosticReports as DiagnosticReport[] | undefined;
  return reports ?? [parseReport(annotation.query)];
}

function reportKey(report: DiagnosticReport): string {
  return JSON.stringify(report);
}

function distinctReports(reports: DiagnosticReport[]): DiagnosticReport[] {
  return [...new Map(reports.map((report) => [reportKey(report), report])).values()];
}

/**
 * Code Hike nests annotations that select an identical range. A nested tooltip trigger is hard to
 * use, so collapse those reports into one annotation and list all of them in the same tooltip.
 */
function mergeDiagnosticsOnTheSameRange(codeblock: HighlightedCode): HighlightedCode {
  const mergedAnnotations: CodeAnnotation[] = [];
  const diagnosticByRange = new Map<string, number>();

  for (const annotation of codeblock.annotations) {
    if (annotation.name !== diagnostics.name || !('lineNumber' in annotation)) {
      mergedAnnotations.push(annotation);
      continue;
    }

    const range = `${annotation.lineNumber}:${annotation.fromColumn}:${annotation.toColumn}`;
    const existingIndex = diagnosticByRange.get(range);
    if (existingIndex === undefined) {
      diagnosticByRange.set(range, mergedAnnotations.length);
      mergedAnnotations.push({
        ...annotation,
        data: {...annotation.data, diagnosticReports: [parseReport(annotation.query)]},
      });
      continue;
    }

    const existing = mergedAnnotations[existingIndex];
    mergedAnnotations[existingIndex] = {
      ...existing,
      data: {
        ...existing.data,
        diagnosticReports: distinctReports([...reportsFrom(existing), parseReport(annotation.query)]),
      },
    };
  }

  return {...codeblock, annotations: mergedAnnotations};
}
