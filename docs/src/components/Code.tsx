import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
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
import {formatMessage, joinTextLines, resolveParameter} from '../utils/diagnostic-messages.mjs';
import {KeyboardShortcut, useIdeaGenerateShortcut} from './KeyboardShortcut';
import styles from './Code.module.css';

/** Renders every fenced code block. docusaurus.config.ts maps the Code Hike blocks to it. */
export default function Code({codeblock}: {codeblock: HighlightedCode}): ReactNode {
  const tabbed = hasFocusedCode(codeblock);

  if (tabbed) return <FocusedCodeTabs codeblock={codeblock} />;

  const pre = <CodePre codeblock={codeblock} named={Boolean(codeblock.meta)} />;

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

const HIDE_FOCUSED_ANNOTATION = 'hide-focused';
const CODE_VIEW_STORAGE_KEY = 'library-api-watchdog-code-view';
const CODE_VIEW_EVENT = 'library-api-watchdog-code-view-change';

type CodeView = 'focused' | 'full';
let inMemoryCodeView: CodeView = 'focused';

function CodePre({
  codeblock,
  named = false,
  tabbed = false,
}: {
  codeblock: HighlightedCode;
  named?: boolean;
  tabbed?: boolean;
}): ReactNode {
  return (
    <CopyTextContext value={codeblock.code}>
      <Pre
        className={clsx(styles.pre, named && styles.namedPre, tabbed && styles.tabbedPre)}
        code={mergeDiagnosticsOnTheSameRange(codeblock)}
        handlers={[copyButton, codeLink, diagnostics]}
      />
    </CopyTextContext>
  );
}

function FocusedCodeTabs({codeblock}: {codeblock: HighlightedCode}): ReactNode {
  const [view, setView] = usePersistentCodeView();
  const tabId = useId();
  const toolbarRef = useRef<HTMLDivElement>(null);
  const toolbarTopBeforeViewChange = useRef<number | null>(null);
  const full = withoutFocusedAnnotations(codeblock);
  const focused = focusedCode(codeblock);
  const selected = view === 'focused' ? focused : full;

  const selectView = useCallback(
    (nextView: CodeView) => {
      if (nextView === view) return;
      toolbarTopBeforeViewChange.current = toolbarRef.current?.getBoundingClientRect().top ?? null;
      setView(nextView);
    },
    [setView, view],
  );

  useLayoutEffect(() => {
    const previousTop = toolbarTopBeforeViewChange.current;
    toolbarTopBeforeViewChange.current = null;
    if (previousTop === null || !toolbarRef.current) return;

    // Every code block changes view together, so the blocks above the clicked one change height.
    // The scroll keeps its toolbar under the pointer.
    const topDelta = toolbarRef.current.getBoundingClientRect().top - previousTop;
    if (Math.abs(topDelta) >= 0.5) window.scrollBy(0, topDelta);
  }, [view]);

  return (
    <Tooltip.Provider delayDuration={250} skipDelayDuration={100}>
      <div className={styles.tabbedCodeBlock}>
        <div ref={toolbarRef} className={styles.codeToolbar}>
          <div aria-label="Code detail" className={styles.codeTabs} role="tablist">
            <CodeTab
              controls={`${tabId}-panel`}
              id={`${tabId}-focused`}
              onSelect={() => selectView('focused')}
              selected={view === 'focused'}
            >
              Focused
            </CodeTab>
            <CodeTab
              controls={`${tabId}-panel`}
              id={`${tabId}-full`}
              onSelect={() => selectView('full')}
              selected={view === 'full'}
            >
              Full
            </CodeTab>
          </div>
          {codeblock.meta && <span className={styles.tabbedFilename}>{codeblock.meta}</span>}
          <FocusedHint />
        </div>
        <div
          aria-labelledby={`${tabId}-${view}`}
          id={`${tabId}-panel`}
          role="tabpanel"
          tabIndex={0}
        >
          <CodePre codeblock={selected} tabbed />
        </div>
      </div>
    </Tooltip.Provider>
  );
}

function CodeTab({
  children,
  controls,
  id,
  onSelect,
  selected,
}: {
  children: ReactNode;
  controls: string;
  id: string;
  onSelect: () => void;
  selected: boolean;
}): ReactNode {
  return (
    <button
      aria-controls={controls}
      aria-selected={selected}
      className={clsx('clean-btn', styles.codeTab)}
      id={id}
      onKeyDown={moveCodeTabFocus}
      onClick={onSelect}
      role="tab"
      tabIndex={selected ? 0 : -1}
      type="button"
    >
      {children}
    </button>
  );
}

function moveCodeTabFocus(event: React.KeyboardEvent<HTMLButtonElement>): void {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;

  const tabs = Array.from(
    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]') ?? [],
  );
  const currentIndex = tabs.indexOf(event.currentTarget);
  if (currentIndex < 0 || tabs.length === 0) return;

  event.preventDefault();
  const nextIndex =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? tabs.length - 1
        : (currentIndex + (event.key === 'ArrowLeft' ? -1 : 1) + tabs.length) % tabs.length;
  tabs[nextIndex].click();
  tabs[nextIndex].focus();
}

function FocusedHint(): ReactNode {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>
        <button
          aria-label="What does Focused mean?"
          className={clsx('clean-btn', styles.focusedHint)}
          type="button"
        >
          ?
        </button>
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          align="center"
          className={styles.focusedTooltip}
          collisionPadding={8}
          side="top"
          sideOffset={6}
        >
          Focused hides the lines that only satisfy other checks, such as KDoc. Full shows the
          whole sample.
          <Tooltip.Arrow className={styles.tooltipArrow} width={10} height={5} />
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}

function usePersistentCodeView(): [CodeView, (view: CodeView) => void] {
  const [view, setView] = useState<CodeView>('focused');

  useEffect(() => {
    const update = () => setView(readStoredCodeView());
    update();
    window.addEventListener(CODE_VIEW_EVENT, update);
    window.addEventListener('storage', update);
    return () => {
      window.removeEventListener(CODE_VIEW_EVENT, update);
      window.removeEventListener('storage', update);
    };
  }, []);

  const select = useCallback((nextView: CodeView) => {
    inMemoryCodeView = nextView;
    try {
      window.localStorage.setItem(CODE_VIEW_STORAGE_KEY, nextView);
    } catch {
      // The browser can block storage. inMemoryCodeView holds the view in that case.
    }
    window.dispatchEvent(new Event(CODE_VIEW_EVENT));
  }, []);

  return [view, select];
}

function readStoredCodeView(): CodeView {
  try {
    inMemoryCodeView =
      window.localStorage.getItem(CODE_VIEW_STORAGE_KEY) === 'full' ? 'full' : 'focused';
  } catch {
    // When the browser blocks storage, the last selected view stays.
  }
  return inMemoryCodeView;
}

function hasFocusedCode(codeblock: HighlightedCode): boolean {
  return codeblock.annotations.some(({name}) => name === HIDE_FOCUSED_ANNOTATION);
}

function withoutFocusedAnnotations(codeblock: HighlightedCode): HighlightedCode {
  return {
    ...codeblock,
    annotations: codeblock.annotations.filter(({name}) => name !== HIDE_FOCUSED_ANNOTATION),
  };
}

function focusedCode(codeblock: HighlightedCode): HighlightedCode {
  const codeLines = codeblock.code.split('\n');
  const hiddenLines = hiddenFocusedLines(codeblock.annotations);
  normalizeBlankLines(codeLines, hiddenLines);

  const oldToNewLine = new Map<number, number>();
  const keptLines: number[] = [];
  for (let line = 1; line <= codeLines.length; line += 1) {
    if (hiddenLines.has(line)) continue;
    keptLines.push(line);
    oldToNewLine.set(line, keptLines.length);
  }
  if (keptLines.length === 0) return withoutFocusedAnnotations(codeblock);

  const tokenLines = splitTokenLines(codeblock.tokens);
  const tokens = keptLines.flatMap((line, index) => [
    ...(tokenLines[line - 1] ?? []),
    ...(index < keptLines.length - 1 ? ['\n'] : []),
  ]);
  const annotations = codeblock.annotations.flatMap((annotation) =>
    remapFocusedAnnotation(annotation, oldToNewLine),
  );
  const code = keptLines.map((line) => codeLines[line - 1]).join('\n');

  return {...codeblock, value: code, code, tokens, annotations};
}

function hiddenFocusedLines(annotations: CodeAnnotation[]): Set<number> {
  const hidden = new Set<number>();
  for (const annotation of annotations) {
    if (annotation.name !== HIDE_FOCUSED_ANNOTATION) continue;
    if ('lineNumber' in annotation) {
      hidden.add(annotation.lineNumber);
      continue;
    }
    for (let line = annotation.fromLineNumber; line <= annotation.toLineNumber; line += 1) {
      hidden.add(line);
    }
  }
  return hidden;
}

function normalizeBlankLines(lines: string[], hidden: Set<number>): void {
  const visible = lines.map((_, index) => index + 1).filter((line) => !hidden.has(line));
  while (visible.length > 0 && !lines[visible[0] - 1].trim()) hidden.add(visible.shift()!);
  while (visible.length > 0 && !lines[visible.at(-1)! - 1].trim()) hidden.add(visible.pop()!);

  let previousWasBlank = false;
  for (const line of visible) {
    if (hidden.has(line)) continue;
    const blank = !lines[line - 1].trim();
    if (blank && previousWasBlank) hidden.add(line);
    previousWasBlank = blank;
  }
}

function splitTokenLines(tokens: HighlightedCode['tokens']): HighlightedCode['tokens'][] {
  const lines: HighlightedCode['tokens'][] = [[]];
  for (const token of tokens) {
    if (typeof token !== 'string') {
      lines.at(-1)!.push(token);
      continue;
    }

    const parts = token.split('\n');
    for (const [index, part] of parts.entries()) {
      if (part) lines.at(-1)!.push(part);
      if (index < parts.length - 1) lines.push([]);
    }
  }
  return lines;
}

function remapFocusedAnnotation(
  annotation: CodeAnnotation,
  oldToNewLine: Map<number, number>,
): CodeAnnotation[] {
  if (annotation.name === HIDE_FOCUSED_ANNOTATION) return [];
  if ('lineNumber' in annotation) {
    const lineNumber = oldToNewLine.get(annotation.lineNumber);
    return lineNumber === undefined ? [] : [{...annotation, lineNumber}];
  }

  const lines = Array.from(
    {length: annotation.toLineNumber - annotation.fromLineNumber + 1},
    (_, index) => annotation.fromLineNumber + index,
  )
    .map((line) => oldToNewLine.get(line))
    .filter((line): line is number => line !== undefined);
  if (lines.length === 0) return [];
  return [{...annotation, fromLineNumber: lines[0], toLineNumber: lines.at(-1)!}];
}

const byName = new Map(registry.diagnostics.map((diagnostic) => [diagnostic.name, diagnostic]));
const sharedParameterValues: Record<string, readonly string[]> = registry.sharedParameterValues;
const CopyTextContext = createContext('');

const copyButton: AnnotationHandler = {name: 'copy-button', Pre: CopyablePre};

function CopyablePre(props: CustomPreProps): ReactNode {
  const copyText = useContext(CopyTextContext);
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

/** Renders a `// !link[/range/] /route` annotation as a link. */
const codeLink: AnnotationHandler = {
  name: 'link',
  Inline: ({annotation, children}) => <Link to={annotation.query}>{children}</Link>,
};

/**
 * Renders a `// !diag[/range/] NAME` annotation as an underlined range with a tooltip that lists
 * its diagnostics. plugins/remark-code-samples.mjs validates the annotation.
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

function Diagnostic({report}: {report: DiagnosticReport}): ReactNode {
  const diagnostic = byName.get(report.name);
  const ideaGenerateShortcut = useIdeaGenerateShortcut();
  // plugins/remark-code-samples.mjs rejects an unknown name, so this fallback never runs on a
  // built site.
  if (!diagnostic) return <span className={styles.diagnosticName}>{report.name}</span>;

  const parameters = report.parameters.map((parameter) =>
    resolveParameter(diagnostic, parameter, sharedParameterValues, ideaGenerateShortcut.label),
  );
  if (diagnostic.name === 'INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS') {
    parameters[2] = alignTable(parameters[2]);
  }
  const message = formatMessage(joinTextLines(diagnostic.message), parameters);
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

function alignTable(table: string): string {
  const rows = table.trim().split('\n').map((line) => line.trim().split(' | '));
  const widths = rows[0].map((_, column) => Math.max(...rows.map((row) => row[column].length)));
  return rows.map((row) =>
    '    ' + row.map((cell, column) => cell.padEnd(widths[column])).join(' | ').trimEnd(),
  ).join('\n');
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
 * Code Hike nests annotations that select the same range, and a nested tooltip is hard to use.
 * The reports of one range merge into one annotation and one tooltip.
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
