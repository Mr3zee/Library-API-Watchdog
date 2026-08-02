import React, {type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import {InnerLine, Pre, type AnnotationHandler, type HighlightedCode} from 'codehike/code';
import registry from '../../../diagnostics.json';
import styles from './Code.module.css';

/**
 * Renders every fenced code block on the site. Code Hike replaces the blocks with this component
 * from the remark plugins configured in docusaurus.config.ts.
 */
export default function Code({codeblock}: {codeblock: HighlightedCode}): ReactNode {
  return <Pre className={styles.pre} code={codeblock} handlers={[diagnostics]} />;
}

const byName = new Map(registry.diagnostics.map((diagnostic) => [diagnostic.name, diagnostic]));

/**
 * Renders the diagnostics a sample line is reported with, under that line and aligned with it.
 * The remark plugin turns the `// DIAGNOSTIC_NAME` comments of the samples into these
 * annotations; see plugins/remark-code-samples.mjs.
 */
const diagnostics: AnnotationHandler = {
  name: 'diag',
  AnnotatedLine: ({annotation, ...props}) => {
    const names = annotation.query.split(',');
    return (
      <>
        <InnerLine merge={props} />
        <div className={styles.diagnostics} style={{paddingLeft: `${props.indentation}ch`}}>
          {names.map((name, index) => (
            <Diagnostic key={name} name={name} last={index === names.length - 1} />
          ))}
        </div>
      </>
    );
  },
};

function Diagnostic({name, last}: {name: string; last: boolean}): ReactNode {
  const diagnostic = byName.get(name);
  // The remark plugin only produces names it found in the registry, so the fallback is only
  // reachable if the two ever get out of sync.
  if (!diagnostic) return <span className={styles.diagnostic}>{name}</span>;
  return (
    <Link className={styles.diagnostic} to={`/${diagnostic.docs}`} title={diagnostic.title}>
      <span className={styles.branch} aria-hidden="true">
        {last ? '╰─' : '├─'}
      </span>
      <span className={styles.icon} aria-hidden="true">
        ⛔
      </span>
      {name}
    </Link>
  );
}
